import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local-only web frontend and HTTP backend for the Valam PDS demonstration. */
public class WebServer {
    private final PDSSystem system;
    private final DatabaseManager database;
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<String, UserSession>();
    private final Map<String, CaptchaEntry> captchaChallenges = new ConcurrentHashMap<String, CaptchaEntry>();
    private final java.security.SecureRandom random = new java.security.SecureRandom();
    private static final long SESSION_MILLIS = 8L * 60 * 60 * 1000;
    private static final String WAREHOUSE_USER = "warehouse.demo";
    private static final String WAREHOUSE_PASSWORD = "ValamWH#2026";
    private static final String FPS_USER = "fps.demo";
    private static final String FPS_PASSWORD = "ValamFPS#2026";

    private WebServer() throws Exception {
        system = Main.createSampleSystem();
        database = new DatabaseManager();
        database.loadSavedLocations(system);
        database.createUserIfMissing(WAREHOUSE_USER, WAREHOUSE_PASSWORD, "WAREHOUSE", null);
        for (FairPriceShop shop : system.getShops()) createShopDemoAccount(shop.getShopId());
        database.disableOldPublicAccounts();
        if (database.countRequests() == 0) {
            for (DeliveryRequest request : system.getRequests()) database.saveRequest(request);
            database.saveStockSnapshot(system);
        } else database.restoreRequestsAndDispatches(system);
        database.saveStockSnapshot(system);
        java.time.YearMonth month = java.time.YearMonth.now();
        system.setMonthlyUsage(month.toString(), database.readMonthlyUsage(month.toString()));
        system.setMonthlyReceived(database.readMonthlyReceived(month.toString()));
        database.log("Website server started");
    }

    private void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handlePage);
        server.setExecutor(Executors.newFixedThreadPool(6));
        server.start();
        System.out.println("Valam PDS website is ready at http://localhost:8080");
        System.out.println("Demo accounts: warehouse.demo / ValamWH#2026 and fps.demo / ValamFPS#2026");
    }

    private synchronized void handleApi(HttpExchange x) throws IOException {
        try {
            String path = x.getRequestURI().getPath();
            String method = x.getRequestMethod();
            if (path.equals("/api/captcha") && method.equals("GET")) { newCaptcha(x); return; }
            if (path.equals("/api/shop-options") && method.equals("GET")) { sendShopOptions(x); return; }
            if (path.equals("/api/login") && method.equals("POST")) { login(x); return; }

            String token = sessionToken(x);
            UserSession user = getSession(token);
            if (user == null) { sendJson(x, 401, "{\"error\":\"Please sign in again.\"}"); return; }
            if (path.equals("/api/session") && method.equals("GET")) { sendSession(x, user); return; }
            if (!userCanAccess(user, path, method)) { sendJson(x, 403, "{\"error\":\"This account does not have access to that operation.\"}"); return; }
            if (path.equals("/api/logout") && method.equals("POST")) {
                sessions.remove(token);
                x.getResponseHeaders().add("Set-Cookie", "pds_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
                sendJson(x, 200, "{\"ok\":true}");
            } else if (path.equals("/api/dispatches") && method.equals("GET")) {
                sendJson(x, 200, dispatchJson(database.readDispatchRows(), user));
            } else if (path.equals("/api/vehicles") && method.equals("GET")) {
                sendJson(x, 200, vehiclesJson());
            } else if (path.equals("/api/requests") && method.equals("GET")) {
                sendJson(x, 200, requestsJson(user));
            } else if (path.equals("/api/warehouses") && method.equals("GET")) {
                sendJson(x, 200, stockJson(true, user));
            } else if (path.equals("/api/shops") && method.equals("GET")) {
                refreshMonthlyUsage();
                sendJson(x, 200, stockJson(false, user));
            } else if (path.equals("/api/shops/population") && method.equals("POST")) {
                updateCurrentShopPopulation(x, user);
            } else if (path.equals("/api/receipts") && method.equals("GET")) {
                sendJson(x, 200, receiptsJson(user));
            } else if (path.equals("/api/locations") && method.equals("GET")) {
                sendJson(x, 200, locationsJson());
            } else if (path.equals("/api/graph") && method.equals("GET")) {
                sendJson(x, 200, graphJson());
            } else if (path.equals("/api/roads") && method.equals("POST")) {
                addOrUpdateRoad(x);
            } else if (path.equals("/api/roads") && method.equals("DELETE")) {
                removeRoad(x);
            } else if (path.equals("/api/warehouses") && method.equals("POST")) {
                addWarehouse(x);
            } else if (path.equals("/api/warehouses") && method.equals("DELETE")) {
                removeLocation(x, true);
            } else if (path.equals("/api/warehouses") && method.equals("PUT")) {
                renameLocation(x, true);
            } else if (path.equals("/api/shops") && method.equals("POST")) {
                addShop(x);
            } else if (path.equals("/api/shops") && method.equals("DELETE")) {
                removeLocation(x, false);
            } else if (path.equals("/api/shops") && method.equals("PUT")) {
                renameLocation(x, false);
            } else if (path.equals("/api/warehouse-stock") && method.equals("POST")) {
                updateWarehouseStock(x);
            } else if (path.equals("/api/requests") && method.equals("POST")) {
                createRequest(x, user);
            } else if (path.equals("/api/receipts") && method.equals("POST")) {
                confirmReceipt(x, user);
            } else if (path.equals("/api/stats") && method.equals("GET")) {
                int delivered = 0, unable = 0, requestCount = 0, dispatchCount = 0;
                for (DeliveryRequest request : system.getRequests()) if (visibleTo(user, request.getShop().getShopId())) {
                    requestCount++;
                    if ("DELIVERED".equals(request.getStatus())) delivered++;
                    if ("PENDING".equals(request.getStatus()) && request.getPendingReason() != null && !request.getPendingReason().trim().isEmpty()) unable++;
                }
                for (DispatchRecord record : system.getDispatchRecords()) if (visibleTo(user, record.getRequest().getShop().getShopId())) dispatchCount++;
                sendJson(x, 200, "{\"requests\":" + requestCount + ",\"dispatches\":" + dispatchCount
                        + ",\"delivered\":" + delivered + ",\"unable\":" + unable + "}");
            } else if (path.equals("/api/process") && method.equals("POST")) {
                processRequests();
                sendJson(x, 200, dispatchJson(database.readDispatchRows(), user));
            } else {
                sendJson(x, 404, "{\"error\":\"Page or API route not found.\"}");
            }
        } catch (Exception ex) {
            sendJson(x, 500, "{\"error\":" + json(ex.getMessage() == null ? "Server error" : ex.getMessage()) + "}");
        }
    }

    private String locationsJson() {
        ArrayList<String> ids=new ArrayList<String>(system.getRoadMap().getRoads().keySet()); java.util.Collections.sort(ids);
        StringBuilder out=new StringBuilder("{\"locations\":["); for(int i=0;i<ids.size();i++){if(i>0)out.append(',');out.append(json(ids.get(i)));} return out.append("]}").toString();
    }

    private String graphJson() {
        HashMap<String, String> names = new HashMap<String, String>();
        HashMap<String, String> types = new HashMap<String, String>();
        for (Warehouse w : system.getWarehouses()) { names.put(w.getWarehouseId(), w.getName()); types.put(w.getWarehouseId(), "Warehouse"); }
        for (FairPriceShop s : system.getShops()) { names.put(s.getShopId(), s.getName()); types.put(s.getShopId(), "Fair Price Shop"); }
        ArrayList<String> ids = new ArrayList<String>(system.getRoadMap().getRoads().keySet());
        java.util.Collections.sort(ids);
        StringBuilder out = new StringBuilder("{\"nodes\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) out.append(',');
            String id = ids.get(i), type = types.containsKey(id) ? types.get(id) : "Road junction";
            String name = names.containsKey(id) ? names.get(id) : "Junction " + id;
            out.append("{\"id\":").append(json(id)).append(",\"name\":").append(json(name))
               .append(",\"type\":").append(json(type)).append('}');
        }
        out.append("],\"edges\":[");
        boolean first = true;
        for (String from : ids) {
            for (Edge edge : system.getRoadMap().getRoads().get(from)) {
                String to = edge.getDestination();
                if (from.compareTo(to) < 0) {
                    if (!first) out.append(',');
                    first = false;
                    out.append("{\"from\":").append(json(from)).append(",\"to\":").append(json(to))
                       .append(",\"minutes\":").append(edge.getTravelTime()).append('}');
                }
            }
        }
        return out.append("]}").toString();
    }

    private String vehiclesJson() {
        StringBuilder out = new StringBuilder("{\"vehicles\":[");
        ArrayList<DeliveryVehicle> vehicles = system.getVehicles();
        for (int i = 0; i < vehicles.size(); i++) {
            DeliveryVehicle vehicle = vehicles.get(i);
            ArrayList<DispatchRecord> activeTrip = new ArrayList<DispatchRecord>();
            HashMap<String, Double> cargo = new HashMap<String, Double>();
            double loadKg = 0;
            for (DispatchRecord record : system.getDispatchRecords()) {
                if (!record.isReceived() && record.getVehicle().getVehicleId().equals(vehicle.getVehicleId())) {
                    activeTrip.add(record);
                    loadKg += record.getRequest().getTotalWeight();
                    for (String commodity : record.getRequest().getRequirements().keySet()) {
                        Double old = cargo.get(commodity);
                        cargo.put(commodity, (old == null ? 0 : old) + record.getRequest().getRequirements().get(commodity));
                    }
                }
            }
            if (i > 0) out.append(',');
            out.append("{\"id\":").append(json(vehicle.getVehicleId()))
               .append(",\"name\":").append(json(vehicle.getVehicleName()))
               .append(",\"capacity\":").append(vehicle.getCapacity())
               .append(",\"available\":").append(vehicle.isAvailable())
               .append(",\"dispatch\":").append(json(activeTrip.isEmpty() ? "" : activeTrip.get(0).getRecordId()))
               .append(",\"loadKg\":").append(loadKg).append(",\"cargo\":{");
            boolean firstCargo = true;
            for (String commodity : new java.util.TreeSet<String>(cargo.keySet())) {
                if (!firstCargo) out.append(',');
                firstCargo = false;
                out.append(json(commodity)).append(':').append(cargo.get(commodity));
            }
            out.append("},\"warehouseId\":").append(json(activeTrip.isEmpty() ? "" : activeTrip.get(0).getWarehouse().getWarehouseId()))
               .append(",\"warehouseName\":").append(json(activeTrip.isEmpty() ? "" : activeTrip.get(0).getWarehouse().getName()))
               .append(",\"routeMinutes\":").append(activeTrip.isEmpty() ? 0 : activeTrip.get(0).getRoute().getTotalTime())
               .append(",\"route\":[");
            if (!activeTrip.isEmpty()) {
                ArrayList<String> locations = activeTrip.get(0).getRoute().getLocations();
                for (int j = 0; j < locations.size(); j++) {
                    if (j > 0) out.append(',');
                    out.append(json(locations.get(j)));
                }
            }
            out.append("],\"stops\":[");
            for (int j = 0; j < activeTrip.size(); j++) {
                DispatchRecord record = activeTrip.get(j);
                if (j > 0) out.append(',');
                out.append("{\"shopId\":").append(json(record.getRequest().getShop().getShopId()))
                   .append(",\"shop\":").append(json(record.getRequest().getShop().getName()))
                   .append(",\"request\":").append(json(record.getRequest().getRequestId()))
                   .append(",\"weight\":").append(record.getRequest().getTotalWeight())
                   .append(",\"items\":{");
                boolean firstItem = true;
                for (String commodity : new java.util.TreeSet<String>(record.getRequest().getRequirements().keySet())) {
                    if (!firstItem) out.append(',');
                    firstItem = false;
                    out.append(json(commodity)).append(':').append(record.getRequest().getRequirements().get(commodity));
                }
                out.append("}}");
            }
            out.append("]}");
        }
        return out.append("]}").toString();
    }

    private void removeRoad(HttpExchange x) throws Exception {
        String body = readBody(x);
        String from = jsonValue(body, "from").trim();
        String to = jsonValue(body, "to").trim();
        if (!system.removeRoad(from, to)) {
            sendJson(x, 404, "{\"error\":\"That road connection was not found.\"}");
            return;
        }
        database.saveStockSnapshot(system);
        database.log("Removed road " + from + " to " + to);
        sendJson(x, 200, "{\"ok\":true}");
    }

    private void addOrUpdateRoad(HttpExchange x) throws Exception {
        String body = readBody(x);
        String from = jsonValue(body, "from").trim();
        String to = jsonValue(body, "to").trim();
        double minutes = number(body, "minutes");
        if (!system.addOrUpdateRoad(from, to, minutes)) {
            sendJson(x, 400, "{\"error\":\"Choose two different saved locations and a positive travel time.\"}");
            return;
        }
        database.saveStockSnapshot(system);
        database.log("Added or updated road " + from + " to " + to);
        sendJson(x, 200, "{\"ok\":true}");
    }

    private void removeLocation(HttpExchange x, boolean warehouse) throws Exception {
        String id = jsonValue(readBody(x), "id").trim();
        boolean removed = warehouse ? system.removeWarehouse(id) : system.removeShop(id);
        if (!removed) {
            String kind = warehouse ? "warehouse" : "shop";
            sendJson(x, 409, "{\"error\":\"Could not remove this " + kind
                    + ". It may not exist or may be referenced by saved requests or dispatch history.\"}");
            return;
        }
        database.deleteLocation(warehouse, id);
        database.saveStockSnapshot(system);
        database.log("Removed " + (warehouse ? "warehouse " : "Fair Price Shop ") + id);
        sendJson(x, 200, "{\"ok\":true}");
    }

    private void updateCurrentShopPopulation(HttpExchange x, UserSession user) throws Exception {
        if (!user.role.equals("FPS")) { sendJson(x,403,"{\"error\":\"Only a Fair Price Shop account can update its customer count.\"}"); return; }
        String body=readBody(x);
        if (!system.updateShopPopulation(user.shopId, peopleCount(body))) { sendJson(x,400,"{\"error\":\"Could not update the selected shop's people count.\"}"); return; }
        database.saveStockSnapshot(system);
        database.log("Updated people count for " + user.shopId);
        sendJson(x,200,"{\"ok\":true}");
    }

    private void addWarehouse(HttpExchange x) throws Exception {
        String b=readBody(x); String id=jsonValue(b,"id").trim(), name=jsonValue(b,"name").trim();
        system.addNewWarehouse(id,name,number(b,"rice"),number(b,"wheat"),number(b,"sugar"),jsonValue(b,"connectTo"),number(b,"travelTime"));
        database.saveStockSnapshot(system); database.log("Added warehouse " + id); sendJson(x,201,"{\"ok\":true}");
    }

    private void addShop(HttpExchange x) throws Exception {
        String b=readBody(x); String id=jsonValue(b,"id").trim(), name=jsonValue(b,"name").trim();
        system.addNewShop(id,name,number(b,"rice"),number(b,"wheat"),number(b,"sugar"),jsonValue(b,"connectTo"),number(b,"travelTime"));
        system.updateShopPopulation(id,peopleCount(b));
        createShopDemoAccount(id);
        database.saveStockSnapshot(system); database.log("Added fair price shop " + id); sendJson(x,201,"{\"ok\":true}");
    }

    private void createShopDemoAccount(String shopId) throws Exception {
        String username = shopId.equalsIgnoreCase("FPS01") ? FPS_USER : "fps." + shopId.toLowerCase(java.util.Locale.ROOT);
        database.createUserIfMissing(username, FPS_PASSWORD, "FPS", shopId);
    }

    private void renameLocation(HttpExchange x, boolean warehouse) throws Exception {
        String body = readBody(x);
        String id = jsonValue(body, "id").trim();
        String name = jsonValue(body, "name").trim();
        String connectTo = jsonValue(body, "connectTo").trim();
        double travelTime = number(body, "travelTime");
        boolean updated = system.updateLocationDetails(id, warehouse, name, connectTo, travelTime);
        if (updated && !warehouse) updated = system.updateShopPopulation(id, peopleCount(body));
        if (!updated) {
            sendJson(x, 400, "{\"error\":\"Enter a name and choose a valid road connection.\"}");
            return;
        }
        database.saveStockSnapshot(system);
        database.log("Updated location details for " + id);
        sendJson(x, 200, "{\"ok\":true}");
    }

    private int peopleCount(String body) { double n=number(body,"population"); if(n<1 || n!=Math.rint(n)) throw new IllegalArgumentException("Enter a whole number of at least one registered person."); return (int)n; }

    private double number(String body,String key) { String value=jsonValue(body,key); double n=Double.parseDouble(value); if(!Double.isFinite(n)||n<0) throw new IllegalArgumentException("Enter a valid non-negative number for " + key + "."); return n; }

    private void updateWarehouseStock(HttpExchange x) throws Exception {
        String b=readBody(x); String id=jsonValue(b,"warehouseId"), commodity=jsonValue(b,"commodity"); double amount=number(b,"quantity");
        if(!java.util.Arrays.asList("Rice","Wheat","Sugar").contains(commodity)) throw new IllegalArgumentException("Choose Rice, Wheat, or Sugar.");
        if(!system.updateWarehouseStock(id,commodity,amount)) throw new IllegalArgumentException("Warehouse not found or new quantity is below already reserved stock.");
        database.saveStockSnapshot(system); database.log("Updated " + commodity + " stock at warehouse " + id); sendJson(x,200,"{\"ok\":true}");
    }

    private synchronized void refreshMonthlyUsage() throws Exception {
        java.time.YearMonth month=java.time.YearMonth.now();
        system.setMonthlyUsage(month.toString(), database.readMonthlyUsage(month.toString()));
        system.setMonthlyReceived(database.readMonthlyReceived(month.toString()));
    }

    private synchronized void processRequests() throws Exception {
        refreshMonthlyUsage();
        system.processRequests();
        for (DeliveryRequest request : system.getRequests()) database.saveRequest(request);
        for (DispatchRecord record : system.getDispatchRecords()) database.saveDispatch(record);
        database.saveStockSnapshot(system);
        database.log("Processed pending requests from website");
    }

    private void createRequest(HttpExchange x, UserSession user) throws Exception {
        String body = readBody(x);
        String shopId = jsonValue(body,"shopId");
        if (user.role.equals("FPS")) shopId = user.shopId;
        String urgency = jsonValue(body,"urgency").toUpperCase();
        if (system.getShop(shopId) == null) { sendJson(x,400,"{\"error\":\"Choose a valid shop.\"}"); return; }
        if (!urgency.equals("HIGH") && !urgency.equals("MEDIUM") && !urgency.equals("LOW")) { sendJson(x,400,"{\"error\":\"Urgency must be HIGH, MEDIUM, or LOW.\"}"); return; }
        HashMap<String,Double> items = new HashMap<String,Double>();
        addRequestAmount(items,"Rice",jsonValue(body,"rice"));
        addRequestAmount(items,"Wheat",jsonValue(body,"wheat"));
        addRequestAmount(items,"Sugar",jsonValue(body,"sugar"));
        if (items.isEmpty()) { sendJson(x,400,"{\"error\":\"Enter at least one positive commodity quantity.\"}"); return; }
        DeliveryRequest request = system.createNewRequest(shopId,urgency,items);
        database.saveRequest(request); database.saveStockSnapshot(system); database.log("Created request " + request.getRequestId());
        sendJson(x,201,"{\"ok\":true,\"requestId\":"+json(request.getRequestId())+"}");
    }

    private void addRequestAmount(HashMap<String,Double> items,String name,String value) {
        double amount=Double.parseDouble(value.isEmpty()?"0":value);
        if(amount<0) throw new IllegalArgumentException("Quantities cannot be negative.");
        if(amount>0) items.put(name,amount);
    }

    private void confirmReceipt(HttpExchange x, UserSession user) throws Exception {
        String id=jsonValue(readBody(x),"dispatchId");
        if (user.role.equals("FPS")) {
            boolean owns=false; for(DispatchRecord r:system.getDispatchRecords()) if(r.getRecordId().equalsIgnoreCase(id) && r.getRequest().getShop().getShopId().equals(user.shopId)) owns=true;
            if(!owns){sendJson(x,403,"{\"error\":\"This delivery belongs to another shop.\"}");return;}
        }
        if(!system.confirmReceipt(id)) { sendJson(x,400,"{\"error\":\"Dispatch not found or receipt already confirmed.\"}"); return; }
        for(DeliveryRequest request:system.getRequests()) database.saveRequest(request);
        for(DispatchRecord record:system.getDispatchRecords()) database.saveDispatch(record);
        database.saveStockSnapshot(system); refreshMonthlyUsage(); database.log("Confirmed receipt for " + id);
        sendJson(x,200,"{\"ok\":true}");
    }

    private void newCaptcha(HttpExchange x) throws IOException {
        long now = System.currentTimeMillis();
        captchaChallenges.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        int first = random.nextInt(8) + 2;
        int second = random.nextInt(8) + 2;
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(18));
        captchaChallenges.put(id, new CaptchaEntry(first + second, now + 5 * 60 * 1000));
        sendJson(x, 200, "{\"id\":" + json(id) + ",\"question\":"
                + json(first + " + " + second + " = ?") + "}");
    }

    private byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }

    private boolean verifyCaptcha(String body) {
        String id = jsonValue(body, "captchaId");
        String answer = jsonValue(body, "captchaAnswer").trim();
        CaptchaEntry entry = captchaChallenges.remove(id);
        if (entry == null || entry.expiresAt < System.currentTimeMillis()) return false;
        try {
            return Integer.parseInt(answer) == entry.answer;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void login(HttpExchange x) throws Exception {
        String body = readBody(x);
        if (!verifyCaptcha(body)) {
            sendJson(x, 400, "{\"error\":\"Verification answer is incorrect or expired. Try the new check.\"}");
            return;
        }
        String username = jsonValue(body, "username").trim().toLowerCase(java.util.Locale.ROOT);
        String password = jsonValue(body, "password");
        String role = jsonValue(body, "role").toUpperCase(java.util.Locale.ROOT);
        String shopId = jsonValue(body, "shopId").trim();
        if (role.equals("FPS") && system.getShop(shopId) == null) {
            sendJson(x, 400, "{\"error\":\"Choose your assigned Fair Price Shop before signing in.\"}");
            return;
        }
        String requiredShop = role.equals("FPS") ? shopId : null;
        if ((!role.equals("WAREHOUSE") && !role.equals("FPS")) || !database.verifyUser(username, password, role, requiredShop)) {
            database.log("Failed login attempt for " + username);
            sendJson(x, 401, "{\"error\":\"Username or password is incorrect.\"}");
            return;
        }
        startSession(x, username, role, role.equals("FPS") ? shopId : "");
    }

    private void startSession(HttpExchange x, String username, String role, String shopId) throws Exception {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        sessions.put(token, new UserSession(username, role, shopId, System.currentTimeMillis() + SESSION_MILLIS));
        x.getResponseHeaders().add("Set-Cookie", "pds_session=" + token
                + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800");
        database.log("Successful website login: " + username);
        sendJson(x, 200, "{\"ok\":true,\"username\":" + json(username) + ",\"role\":" + json(role) + ",\"shopId\":" + json(shopId) + "}");
    }

    private static class CaptchaEntry {
        private final int answer;
        private final long expiresAt;

        private CaptchaEntry(int answer, long expiresAt) {
            this.answer = answer;
            this.expiresAt = expiresAt;
        }
    }

    private void handlePage(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath();
        String file;
        String type;
        if (path.equals("/") || path.equals("/index.html")) { file = "index.html"; type = "text/html; charset=utf-8"; }
        else if (path.equals("/styles.css")) { file = "styles.css"; type = "text/css; charset=utf-8"; }
        else if (path.equals("/app.js")) { file = "app.js"; type = "application/javascript; charset=utf-8"; }
        else { sendText(x, 404, "Not found", "text/plain; charset=utf-8"); return; }
        try {
            Path frontendFile = Paths.get("frontend", file);
            byte[] data = Files.readAllBytes(Files.exists(frontendFile) ? frontendFile : Paths.get("web", file));
            x.getResponseHeaders().set("Content-Type", type);
            x.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            x.getResponseHeaders().set("Pragma", "no-cache");
            x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            x.getResponseHeaders().set("X-Frame-Options", "DENY");
            x.sendResponseHeaders(200, data.length);
            x.getResponseBody().write(data);
            x.close();
        } catch (Exception ex) { sendText(x, 500, "Website files not found. Keep the frontend folder beside Run-Website.bat.", "text/plain; charset=utf-8"); }
    }

    private String dispatchJson(ArrayList<String[]> rows, UserSession user) {
        StringBuilder out = new StringBuilder("{\"dispatches\":[");
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (user.role.equals("FPS") && !dispatchBelongsToShop(r[0], user.shopId)) continue;
            if (out.charAt(out.length()-1) != '[') out.append(',');
            out.append("{\"id\":").append(json(r[0])).append(",\"request\":").append(json(r[1]))
               .append(",\"warehouse\":").append(json(r[2])).append(",\"shop\":").append(json(r[3]))
               .append(",\"vehicle\":").append(json(r[4])).append(",\"route\":").append(json(r[5]))
               .append(",\"receipt\":").append(json(r[6])).append(",\"updated\":").append(json(r[7])).append('}');
        }
        return out.append("]}").toString();
    }

    private boolean dispatchBelongsToShop(String dispatchId,String shopId) {
        for(DispatchRecord record:system.getDispatchRecords()) if(record.getRecordId().equalsIgnoreCase(dispatchId)) return record.getRequest().getShop().getShopId().equals(shopId);
        return false;
    }

    private String requestsJson(UserSession user) {
        StringBuilder out=new StringBuilder("{\"requests\":[");
        ArrayList<DeliveryRequest> requests=system.getRequests();
        for(int i=0;i<requests.size();i++) {
            DeliveryRequest r=requests.get(i); if(!visibleTo(user,r.getShop().getShopId()))continue; if(out.charAt(out.length()-1)!='[')out.append(',');
            out.append("{\"id\":").append(json(r.getRequestId())).append(",\"shopId\":").append(json(r.getShop().getShopId()))
               .append(",\"shop\":").append(json(r.getShop().getName())).append(",\"urgency\":").append(json(r.getUrgency()))
               .append(",\"items\":").append(json(r.getRequirements().toString())).append(",\"totalKg\":").append(r.getTotalWeight())
               .append(",\"status\":").append(json(r.getStatus())).append(",\"reason\":").append(json(r.getPendingReason())).append('}');
        }
        return out.append("]}").toString();
    }

    private String stockJson(boolean warehouseRows, UserSession user) {
        StringBuilder out=new StringBuilder("{\"rows\":[");
        int index=0;
        if(warehouseRows) {
            for(Warehouse w:system.getWarehouses()) {
                if(index++>0)out.append(',');
                out.append("{\"id\":").append(json(w.getWarehouseId())).append(",\"name\":").append(json(w.getName()));
                appendStock(out,w.getStock("Rice"),"rice"); appendStock(out,w.getStock("Wheat"),"wheat"); appendStock(out,w.getStock("Sugar"),"sugar"); out.append('}');
            }
        } else {
            for(FairPriceShop s:system.getShops()) {
                if(!visibleTo(user,s.getShopId()))continue;
                if(index++>0)out.append(',');
                out.append("{\"id\":").append(json(s.getShopId())).append(",\"name\":").append(json(s.getName()));
                appendStock(out,s.getStock("Rice"),"rice"); appendStock(out,s.getStock("Wheat"),"wheat"); appendStock(out,s.getStock("Sugar"),"sugar");
                out.append(",\"population\":").append(s.getPopulation()).append(",\"riceQuota\":").append(s.getMonthlyQuota("Rice"))
                   .append(",\"wheatQuota\":").append(s.getMonthlyQuota("Wheat")).append(",\"sugarQuota\":").append(s.getMonthlyQuota("Sugar"))
                   .append(",\"riceUsed\":").append(system.getMonthlyReceived(s.getShopId(),"Rice")).append(",\"wheatUsed\":").append(system.getMonthlyReceived(s.getShopId(),"Wheat"))
                   .append(",\"sugarUsed\":").append(system.getMonthlyReceived(s.getShopId(),"Sugar"))
                   .append(",\"riceCommitted\":").append(system.getMonthlyUsed(s.getShopId(),"Rice")).append(",\"wheatCommitted\":").append(system.getMonthlyUsed(s.getShopId(),"Wheat"))
                   .append(",\"sugarCommitted\":").append(system.getMonthlyUsed(s.getShopId(),"Sugar")).append('}');
            }
        }
        return out.append("]}").toString();
    }

    private void appendStock(StringBuilder out,CommodityStock stock,String key) {
        out.append(",\"").append(key).append("\":").append(stock==null?0:stock.getQuantity());
    }

    private String receiptsJson(UserSession user) throws Exception {
        ArrayList<String[]> rows=database.readReceiptRows(); StringBuilder out=new StringBuilder("{\"receipts\":[");
        for(int i=0;i<rows.size();i++) { String[] r=rows.get(i); if(user.role.equals("FPS") && !dispatchBelongsToShop(r[0],user.shopId))continue; if(out.charAt(out.length()-1)!='[')out.append(',');
            out.append("{\"dispatchId\":").append(json(r[0])).append(",\"shop\":").append(json(r[1]))
               .append(",\"items\":").append(json(r[2])).append(",\"confirmedAt\":").append(json(r[3])).append('}'); }
        return out.append("]}").toString();
    }

    private String jsonValue(String body, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
        Matcher m = p.matcher(body);
        if (!m.find()) return "";
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String json(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private String sessionToken(HttpExchange x) {
        String cookie = x.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return "";
        for (String part : cookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("pds_session=")) return trimmed.substring("pds_session=".length());
        }
        return "";
    }

    private UserSession getSession(String token) {
        UserSession user = sessions.get(token);
        if (user == null) return null;
        if (user.expiresAt < System.currentTimeMillis()) { sessions.remove(token); return null; }
        return user;
    }
    private boolean visibleTo(UserSession user, String shopId) { return !user.role.equals("FPS") || user.shopId.equals(shopId); }
    private boolean userCanAccess(UserSession user, String path, String method) {
        if (user.role.equals("WAREHOUSE")) return !(path.equals("/api/receipts") || path.equals("/api/requests") && method.equals("POST"));
        if (path.equals("/api/warehouses") || path.equals("/api/warehouse-stock") || path.equals("/api/vehicles") || path.equals("/api/process")) return false;
        if ((path.equals("/api/roads") && !method.equals("GET")) || path.equals("/api/locations") && !method.equals("GET")) return false;
        if (path.equals("/api/shops") && !method.equals("GET")) return false;
        return true;
    }
    private void sendSession(HttpExchange x, UserSession user) throws IOException {
        sendJson(x,200,"{\"username\":"+json(user.username)+",\"role\":"+json(user.role)+",\"shopId\":"+json(user.shopId)+"}");
    }
    private void sendShopOptions(HttpExchange x) throws IOException {
        StringBuilder out=new StringBuilder("{\"shops\":["); int i=0;
        for(FairPriceShop shop:system.getShops()){if(i++>0)out.append(',');out.append("{\"id\":").append(json(shop.getShopId())).append(",\"name\":").append(json(shop.getName())).append('}');}
        sendJson(x,200,out.append("]}").toString());
    }
    private static class UserSession {
        final String username, role, shopId; final long expiresAt;
        UserSession(String username,String role,String shopId,long expiresAt){this.username=username;this.role=role;this.shopId=shopId;this.expiresAt=expiresAt;}
    }

    private String readBody(HttpExchange x) throws IOException {
        InputStream in = x.getRequestBody(); ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048]; int n; int total = 0;
        while ((n = in.read(buffer)) != -1) { total += n; if (total > 20000) throw new IOException("Request is too large."); out.write(buffer, 0, n); }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void sendJson(HttpExchange x, int status, String body) throws IOException { sendText(x, status, body, "application/json; charset=utf-8"); }
    private void sendText(HttpExchange x, int status, String body, String type) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(status, data.length);
        x.getResponseBody().write(data);
        x.close();
    }

    public static void main(String[] args) throws Exception { new WebServer().start(); }
}
