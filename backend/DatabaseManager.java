import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Small SQLite helper used by the desktop demo to save request and delivery records. */
public class DatabaseManager {
    private Connection connection;

    public DatabaseManager() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path folder = Paths.get("database");
        Files.createDirectories(folder);
        Path databaseFile = folder.resolve("valam_supply_chain.db");
        Path oldDatabaseFile = Paths.get("valam_supply_chain.db");
        if (!Files.exists(databaseFile) && Files.exists(oldDatabaseFile)) Files.copy(oldDatabaseFile, databaseFile);
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toString());
        createTables();
    }

    private void createTables() throws Exception {
        Statement statement = connection.createStatement();
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS requests (" +
                "request_id TEXT PRIMARY KEY, shop_id TEXT, urgency TEXT, items TEXT, " +
                "total_kg REAL, status TEXT, pending_reason TEXT, updated_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS dispatches (" +
                "dispatch_id TEXT PRIMARY KEY, request_id TEXT, warehouse TEXT, shop TEXT, " +
                "vehicle TEXT, route TEXT, receipt_confirmed INTEGER, updated_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS activity_log (" +
                "activity_id INTEGER PRIMARY KEY AUTOINCREMENT, message TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                "username TEXT PRIMARY KEY, password_salt TEXT NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'WAREHOUSE', shop_id TEXT)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS receipts (" +
                "dispatch_id TEXT PRIMARY KEY, shop TEXT, items TEXT, confirmed_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS stock_balances (" +
                "location_type TEXT, location_id TEXT, location_name TEXT, commodity TEXT, quantity REAL, " +
                "PRIMARY KEY(location_type,location_id,commodity))");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS warehouses (warehouse_id TEXT PRIMARY KEY, name TEXT NOT NULL)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS fair_price_shops (shop_id TEXT PRIMARY KEY, name TEXT NOT NULL)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS roads (location_from TEXT, location_to TEXT, travel_time REAL NOT NULL, PRIMARY KEY(location_from,location_to))");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS vehicles (vehicle_id TEXT PRIMARY KEY, name TEXT, capacity REAL)");
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS project_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT NOT NULL)");
        ensureColumn("requests", "arrival_order", "INTEGER DEFAULT 0");
        ensureColumn("users", "role", "TEXT NOT NULL DEFAULT 'WAREHOUSE'");
        ensureColumn("users", "shop_id", "TEXT");
        ensureColumn("fair_price_shops", "population", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("dispatches", "dispatched_at", "TEXT");
        Statement dates = connection.createStatement(); dates.executeUpdate("UPDATE dispatches SET dispatched_at=datetime(updated_at,'localtime') WHERE dispatched_at IS NULL"); dates.close();
        statement.close();
    }

    private void ensureColumn(String table, String column, String definition) throws Exception {
        Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")"); boolean found = false;
        while (rs.next()) if (column.equalsIgnoreCase(rs.getString("name"))) found = true;
        rs.close(); st.close(); if (!found) { st = connection.createStatement(); st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); st.close(); }
    }

    public synchronized void createUserIfMissing(String username, String password) throws Exception { createUserIfMissing(username, password, "WAREHOUSE", null); }

    public synchronized void createUserIfMissing(String username, String password, String role, String shopId) throws Exception {
        PreparedStatement find = connection.prepareStatement("SELECT username FROM users WHERE username=?");
        find.setString(1, username);
        ResultSet rs = find.executeQuery();
        boolean exists = rs.next();
        rs.close(); find.close();
        if (exists) {
            PreparedStatement update = connection.prepareStatement("UPDATE users SET role=?,shop_id=? WHERE username=?");
            update.setString(1, role); update.setString(2, shopId); update.setString(3, username); update.executeUpdate(); update.close();
            return;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String encodedSalt = Base64.getEncoder().encodeToString(salt);
        String encodedHash = hashPassword(password, salt);
        PreparedStatement insert = connection.prepareStatement("INSERT INTO users(username,password_salt,password_hash,role,shop_id) VALUES(?,?,?,?,?)");
        insert.setString(1, username); insert.setString(2, encodedSalt); insert.setString(3, encodedHash); insert.setString(4, role); insert.setString(5, shopId);
        insert.executeUpdate(); insert.close();
    }

    public synchronized void disableOldPublicAccounts() throws Exception {
        PreparedStatement ps=connection.prepareStatement("UPDATE users SET role='DISABLED' WHERE username<>? AND username NOT LIKE 'fps.%'");
        ps.setString(1,"warehouse.demo"); ps.executeUpdate(); ps.close();
    }

    public synchronized boolean registerUser(String username, String password) throws Exception {
        PreparedStatement find = connection.prepareStatement("SELECT username FROM users WHERE username=?");
        find.setString(1, username);
        ResultSet rs = find.executeQuery();
        boolean exists = rs.next();
        rs.close(); find.close();
        if (exists) return false;

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String encodedSalt = Base64.getEncoder().encodeToString(salt);
        String encodedHash = hashPassword(password, salt);
        PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO users(username,password_salt,password_hash) VALUES(?,?,?)");
        insert.setString(1, username);
        insert.setString(2, encodedSalt);
        insert.setString(3, encodedHash);
        insert.executeUpdate();
        insert.close();
        return true;
    }

    public synchronized boolean verifyUser(String username, String password) throws Exception { return verifyUser(username, password, null); }

    public synchronized boolean verifyUser(String username, String password, String role) throws Exception {
        return verifyUser(username, password, role, null);
    }

    public synchronized boolean verifyUser(String username, String password, String role, String shopId) throws Exception {
        PreparedStatement find = connection.prepareStatement("SELECT password_salt,password_hash FROM users WHERE username=? AND (? IS NULL OR role=?) AND (? IS NULL OR shop_id=?)");
        find.setString(1, username); find.setString(2, role); find.setString(3, role); find.setString(4, shopId); find.setString(5, shopId);
        ResultSet rs = find.executeQuery();
        if (!rs.next()) { rs.close(); find.close(); return false; }
        byte[] salt = Base64.getDecoder().decode(rs.getString(1));
        String expected = rs.getString(2);
        rs.close(); find.close();
        return expected.equals(hashPassword(password, salt));
    }

    private String hashPassword(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return Base64.getEncoder().encodeToString(hash);
    }

    private String encodeItems(DeliveryRequest request) {
        StringBuilder value = new StringBuilder();
        for (String key : request.getRequirements().keySet()) { if (value.length() > 0) value.append(';'); value.append(key).append('=').append(request.getRequirements().get(key)); }
        return value.toString();
    }

    private java.util.HashMap<String, Double> decodeItems(String value) {
        java.util.HashMap<String, Double> items = new java.util.HashMap<String, Double>();
        if (value == null) return items;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z]+)=([0-9]+(?:\\.[0-9]+)?)").matcher(value);
        while (m.find()) items.put(m.group(1), Double.parseDouble(m.group(2)));
        return items;
    }

    /** Restore saved app records after creating the sample objects. */
    public synchronized void restoreRequestsAndDispatches(PDSSystem system) throws Exception {
        int maxRequest = 0;
        Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT request_id,shop_id,urgency,items,status,pending_reason,arrival_order FROM requests ORDER BY arrival_order,request_id");
        while (rs.next()) {
            String id=rs.getString(1), shopId=rs.getString(2), urgency=rs.getString(3), status=rs.getString(5), reason=rs.getString(6);
            int order=rs.getInt(7); java.util.HashMap<String,Double> items=decodeItems(rs.getString(4));
            if (order <= 0) { java.util.regex.Matcher n=java.util.regex.Pattern.compile("(\\d+)$").matcher(id); if(n.find()) order=Integer.parseInt(n.group(1)); }
            DeliveryRequest request=system.getRequest(id);
            if(request==null && system.getShop(shopId)!=null && !items.isEmpty()) request=system.createRequest(id,shopId,urgency,items);
            if(request!=null) { request.setStatus(status); request.setPendingReason(reason==null?"":reason); }
            maxRequest=Math.max(maxRequest,order);
        }
        rs.close(); system.setNextArrivalNumber(maxRequest+1);

        int maxDispatch=0; rs=st.executeQuery("SELECT dispatch_id,request_id,warehouse,shop,vehicle,route,receipt_confirmed FROM dispatches ORDER BY dispatch_id");
        while(rs.next()) {
            String id=rs.getString(1), requestId=rs.getString(2), warehouseName=rs.getString(3), vehicleName=rs.getString(5), routeText=rs.getString(6);
            java.util.regex.Matcher dn=java.util.regex.Pattern.compile("(\\d+)$").matcher(id);
            if(dn.find()) maxDispatch=Math.max(maxDispatch,Integer.parseInt(dn.group(1)));
            DeliveryRequest req=system.getRequest(requestId); if(req==null) continue;
            java.util.regex.Matcher rm=java.util.regex.Pattern.compile("^(.*?) \\(([0-9.]+) min\\)$").matcher(routeText);
            String routeOnly=routeText; double time=0; if(rm.find()){routeOnly=rm.group(1);time=Double.parseDouble(rm.group(2));}
            java.util.ArrayList<String> locations=new java.util.ArrayList<String>(); for(String loc:routeOnly.split(" -> ")) locations.add(loc.trim());
            Warehouse wh=locations.isEmpty()?null:system.getWarehouse(locations.get(0));
            if(wh==null) for(Warehouse candidate:system.getWarehouses()) if(candidate.getWarehouseId().equals(warehouseName)||candidate.getName().equals(warehouseName)) wh=candidate;
            DeliveryVehicle vehicle=null; for(DeliveryVehicle candidate:system.getVehicles()) if(candidate.getVehicleName().equals(vehicleName)) vehicle=candidate;
            if(wh==null||vehicle==null) continue;
            DispatchRecord record=new DispatchRecord(id,req,wh,vehicle,new Route(locations,time));
            boolean received=rs.getInt(7)==1; record.setReceived(received); system.restoreDispatch(record);
            req.setStatus(received?"DELIVERED":"DISPATCHED");
        }
        rs.close(); st.close(); system.setNextDispatchNumber(maxDispatch+1);
    }

    public synchronized void saveRequest(DeliveryRequest request) throws Exception {
        String sql = "INSERT INTO requests(request_id,shop_id,urgency,items,total_kg,status,pending_reason,arrival_order) " +
                "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(request_id) DO UPDATE SET " +
                "status=excluded.status,pending_reason=excluded.pending_reason,items=excluded.items,total_kg=excluded.total_kg,arrival_order=excluded.arrival_order,updated_at=CURRENT_TIMESTAMP";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, request.getRequestId());
        ps.setString(2, request.getShop().getShopId());
        ps.setString(3, request.getUrgency());
        ps.setString(4, encodeItems(request));
        ps.setDouble(5, request.getTotalWeight());
        ps.setString(6, request.getStatus());
        ps.setString(7, request.getPendingReason());
        ps.setInt(8, request.getArrivalOrder());
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void saveDispatch(DispatchRecord record) throws Exception {
        String sql = "INSERT INTO dispatches(dispatch_id,request_id,warehouse,shop,vehicle,route,receipt_confirmed,dispatched_at) " +
                "VALUES(?,?,?,?,?,?,?,datetime('now','localtime')) ON CONFLICT(dispatch_id) DO UPDATE SET " +
                "receipt_confirmed=excluded.receipt_confirmed,updated_at=CURRENT_TIMESTAMP";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, record.getRecordId());
        ps.setString(2, record.getRequest().getRequestId());
        ps.setString(3, record.getWarehouse().getName());
        ps.setString(4, record.getRequest().getShop().getName());
        ps.setString(5, record.getVehicle().getVehicleName());
        ps.setString(6, String.join(" -> ", record.getRoute().getLocations()) +
                " (" + record.getRoute().getTotalTime() + " min)");
        ps.setInt(7, record.isReceived() ? 1 : 0);
        ps.executeUpdate();
        ps.close();
        if (record.isReceived()) saveReceipt(record);
    }

    public synchronized void saveReceipt(DispatchRecord record) throws Exception {
        PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO receipts(dispatch_id,shop,items,confirmed_at) VALUES(?,?,?,datetime('now','localtime'))");
        ps.setString(1, record.getRecordId());
        ps.setString(2, record.getRequest().getShop().getName());
        ps.setString(3, record.getRequest().getRequirements().toString());
        ps.executeUpdate(); ps.close();
    }

    public synchronized void saveStockSnapshot(PDSSystem system) throws Exception {
        connection.setAutoCommit(false);
        try {
            Statement clearRoads = connection.createStatement();
            clearRoads.executeUpdate("DELETE FROM roads");
            clearRoads.close();
            for (Warehouse w : system.getWarehouses()) {
                saveEntity("warehouses", "warehouse_id", w.getWarehouseId(), w.getName());
                saveOneStock("WAREHOUSE", w.getWarehouseId(), w.getName(), "Rice", w.getStock("Rice"));
                saveOneStock("WAREHOUSE", w.getWarehouseId(), w.getName(), "Wheat", w.getStock("Wheat"));
                saveOneStock("WAREHOUSE", w.getWarehouseId(), w.getName(), "Sugar", w.getStock("Sugar"));
            }
            for (FairPriceShop s : system.getShops()) {
                PreparedStatement shopSave = connection.prepareStatement("INSERT INTO fair_price_shops(shop_id,name,population) VALUES(?,?,?) ON CONFLICT(shop_id) DO UPDATE SET name=excluded.name,population=excluded.population");
                shopSave.setString(1, s.getShopId()); shopSave.setString(2, s.getName()); shopSave.setInt(3, s.getPopulation()); shopSave.executeUpdate(); shopSave.close();
                saveOneStock("SHOP", s.getShopId(), s.getName(), "Rice", s.getStock("Rice"));
                saveOneStock("SHOP", s.getShopId(), s.getName(), "Wheat", s.getStock("Wheat"));
                saveOneStock("SHOP", s.getShopId(), s.getName(), "Sugar", s.getStock("Sugar"));
            }
            for (java.util.Map.Entry<String, ArrayList<Edge>> entry : system.getRoadMap().getRoads().entrySet()) {
                for (Edge edge : entry.getValue()) if (entry.getKey().compareTo(edge.getDestination()) < 0) {
                    PreparedStatement road = connection.prepareStatement("INSERT OR REPLACE INTO roads(location_from,location_to,travel_time) VALUES(?,?,?)");
                    road.setString(1, entry.getKey()); road.setString(2, edge.getDestination()); road.setDouble(3, edge.getTravelTime()); road.executeUpdate(); road.close();
                }
            }
            for (DeliveryVehicle vehicle : system.getVehicles()) {
                PreparedStatement v = connection.prepareStatement("INSERT OR REPLACE INTO vehicles(vehicle_id,name,capacity) VALUES(?,?,?)");
                v.setString(1, vehicle.getVehicleId()); v.setString(2, vehicle.getVehicleName()); v.setDouble(3, vehicle.getCapacity()); v.executeUpdate(); v.close();
            }
            connection.commit();
        } catch (Exception ex) { connection.rollback(); throw ex; }
        finally { connection.setAutoCommit(true); }
    }

    public synchronized void deleteLocation(boolean warehouse, String id) throws Exception {
        String table = warehouse ? "warehouses" : "fair_price_shops";
        String idColumn = warehouse ? "warehouse_id" : "shop_id";
        connection.setAutoCommit(false);
        try {
            PreparedStatement roads = connection.prepareStatement(
                    "DELETE FROM roads WHERE location_from=? OR location_to=?");
            roads.setString(1, id); roads.setString(2, id); roads.executeUpdate(); roads.close();
            PreparedStatement stocks = connection.prepareStatement(
                    "DELETE FROM stock_balances WHERE location_type=? AND location_id=?");
            stocks.setString(1, warehouse ? "WAREHOUSE" : "SHOP"); stocks.setString(2, id);
            stocks.executeUpdate(); stocks.close();
            PreparedStatement location = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE " + idColumn + "=?");
            location.setString(1, id); location.executeUpdate(); location.close();
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void saveEntity(String table, String idColumn, String id, String name) throws Exception {
        PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO " + table + "(" + idColumn + ",name) VALUES(?,?)");
        ps.setString(1, id); ps.setString(2, name); ps.executeUpdate(); ps.close();
    }

    /** Loads user-created locations and their saved balances on top of the sample map. */
    public synchronized void loadSavedLocations(PDSSystem system) throws Exception {
        Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT warehouse_id,name FROM warehouses");
        while (rs.next()) {
            String id=rs.getString(1), name=rs.getString(2);
            if (system.getWarehouse(id) == null) system.addWarehouse(new Warehouse(id, name));
            else system.renameWarehouse(id, name);
        }
        rs.close();
        rs = st.executeQuery("SELECT shop_id,name,population FROM fair_price_shops");
        while (rs.next()) {
            String id=rs.getString(1), name=rs.getString(2); int people=rs.getInt(3);
            if (system.getShop(id) == null) system.addShop(new FairPriceShop(id, name));
            else system.renameShop(id, name);
            if (people > 0) system.updateShopPopulation(id, people);
        }
        rs.close();
        rs = st.executeQuery("SELECT COUNT(*) FROM roads");
        boolean hasSavedRoads = rs.next() && rs.getInt(1) > 0;
        rs.close();
        if (hasSavedRoads) system.resetRoadMap();
        rs = st.executeQuery("SELECT location_from,location_to,travel_time FROM roads");
        while (rs.next()) system.addRoad(rs.getString(1), rs.getString(2), rs.getDouble(3));
        rs.close();
        if (!"2".equals(readSetting("road_network_version"))) {
            Main.addDefaultCompleteRoadConnections(system);
            saveSetting("road_network_version", "2");
        }
        rs = st.executeQuery("SELECT location_type,location_id,commodity,quantity FROM stock_balances");
        while (rs.next()) {
            String type = rs.getString(1), id = rs.getString(2), commodity = rs.getString(3); double q = rs.getDouble(4);
            if (type.equals("WAREHOUSE")) system.updateWarehouseStock(id, commodity, q);
            else if (type.equals("SHOP")) { FairPriceShop shop = system.getShop(id); if (shop != null) { CommodityStock stock = shop.getStock(commodity); if (stock == null) shop.addStock(new CommodityStock(commodity, q)); else stock.setQuantity(q); } }
        }
        rs.close(); st.close();
    }

    private String readSetting(String key) throws Exception {
        PreparedStatement ps = connection.prepareStatement("SELECT setting_value FROM project_settings WHERE setting_key=?");
        ps.setString(1, key); ResultSet rs = ps.executeQuery();
        String value = rs.next() ? rs.getString(1) : null;
        rs.close(); ps.close(); return value;
    }

    private void saveSetting(String key, String value) throws Exception {
        PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO project_settings(setting_key,setting_value) VALUES(?,?)");
        ps.setString(1, key); ps.setString(2, value); ps.executeUpdate(); ps.close();
    }

    private void saveOneStock(String type, String id, String name, String commodity, CommodityStock stock) throws Exception {
        if (stock == null) return;
        PreparedStatement ps = connection.prepareStatement("INSERT INTO stock_balances(location_type,location_id,location_name,commodity,quantity) VALUES(?,?,?,?,?) " +
                "ON CONFLICT(location_type,location_id,commodity) DO UPDATE SET location_name=excluded.location_name,quantity=excluded.quantity");
        ps.setString(1,type); ps.setString(2,id); ps.setString(3,name); ps.setString(4,commodity); ps.setDouble(5,stock.getQuantity());
        ps.executeUpdate(); ps.close();
    }

    public synchronized ArrayList<String[]> readReceiptRows() throws Exception {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT dispatch_id,shop,items,confirmed_at FROM receipts ORDER BY confirmed_at DESC");
        while (rs.next()) rows.add(new String[] {rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)});
        rs.close(); st.close(); return rows;
    }

    public synchronized void log(String message) throws Exception {
        PreparedStatement ps = connection.prepareStatement("INSERT INTO activity_log(message) VALUES(?)");
        ps.setString(1, message);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized ArrayList<String[]> readRecentActivity() throws Exception {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT created_at,message FROM activity_log ORDER BY activity_id DESC LIMIT 30");
        while (rs.next()) rows.add(new String[] { rs.getString(1), rs.getString(2) });
        rs.close(); st.close();
        return rows;
    }

    public synchronized java.util.HashMap<String, java.util.HashMap<String, Double>> readMonthlyUsage(String month) throws Exception {
        java.util.HashMap<String, java.util.HashMap<String, Double>> usage = new java.util.HashMap<String, java.util.HashMap<String, Double>>();
        PreparedStatement ps = connection.prepareStatement("SELECT r.shop_id,r.items FROM requests r JOIN dispatches d ON d.request_id=r.request_id WHERE d.receipt_confirmed=0 OR EXISTS (SELECT 1 FROM receipts rc WHERE rc.dispatch_id=d.dispatch_id AND substr(rc.confirmed_at,1,7)=?)");
        ps.setString(1, month); ResultSet rs = ps.executeQuery();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([A-Za-z]+)=([0-9]+(?:\\.[0-9]+)?)");
        while (rs.next()) {
            String shopId=rs.getString(1), items=rs.getString(2); java.util.HashMap<String, Double> row=usage.get(shopId);
            if(row==null){row=new java.util.HashMap<String,Double>();usage.put(shopId,row);}
            java.util.regex.Matcher matcher=pattern.matcher(items==null?"":items);
            while(matcher.find()){String commodity=matcher.group(1);Double old=row.get(commodity);row.put(commodity,(old==null?0:old)+Double.parseDouble(matcher.group(2)));}
        }
        rs.close(); ps.close(); return usage;
    }

    public synchronized java.util.HashMap<String, java.util.HashMap<String, Double>> readMonthlyReceived(String month) throws Exception {
        java.util.HashMap<String, java.util.HashMap<String, Double>> received = new java.util.HashMap<String, java.util.HashMap<String, Double>>();
        PreparedStatement ps = connection.prepareStatement("SELECT r.shop_id,r.items FROM receipts rc JOIN dispatches d ON d.dispatch_id=rc.dispatch_id JOIN requests r ON r.request_id=d.request_id WHERE substr(rc.confirmed_at,1,7)=?");
        ps.setString(1, month); ResultSet rs = ps.executeQuery();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([A-Za-z]+)=([0-9]+(?:\\.[0-9]+)?)");
        while (rs.next()) {
            String shopId=rs.getString(1), items=rs.getString(2); java.util.HashMap<String, Double> row=received.get(shopId);
            if(row==null){row=new java.util.HashMap<String,Double>();received.put(shopId,row);}
            java.util.regex.Matcher matcher=pattern.matcher(items==null?"":items);
            while(matcher.find()){String commodity=matcher.group(1);Double old=row.get(commodity);row.put(commodity,(old==null?0:old)+Double.parseDouble(matcher.group(2)));}
        }
        rs.close(); ps.close(); return received;
    }

    public synchronized ArrayList<String[]> readDispatchRows() throws Exception {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT dispatch_id,request_id,warehouse,shop,vehicle,route,receipt_confirmed,updated_at FROM dispatches ORDER BY updated_at DESC, dispatch_id");
        while (rs.next()) rows.add(new String[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7) == 1 ? "Confirmed" : "Waiting", rs.getString(8) });
        rs.close(); st.close();
        return rows;
    }

    public synchronized int countRequests() throws Exception { return count("requests"); }
    public synchronized int countDispatches() throws Exception { return count("dispatches"); }
    private int count(String table) throws Exception {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table);
        int n = rs.next() ? rs.getInt(1) : 0;
        rs.close(); st.close();
        return n;
    }

    public synchronized void close() throws Exception { if (connection != null) connection.close(); }
}
