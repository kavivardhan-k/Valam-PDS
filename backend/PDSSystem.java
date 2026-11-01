import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Scanner;

 public class PDSSystem {
    private HashMap<String, Warehouse> warehouses;
    private HashMap<String, FairPriceShop> shops;
    private HashMap<String, DeliveryVehicle> vehicles;
    private ArrayList<DeliveryRequest> requests;
    private ArrayList<DispatchRecord> dispatchRecords;
    private ArrayList<String> receiptHistory;
    private Graph roadMap;
    private int nextArrivalNumber;
    private int nextDispatchNumber;
    private String quotaMonth = "";
    private HashMap<String, HashMap<String, Double>> monthlyUsed = new HashMap<String, HashMap<String, Double>>();
    private HashMap<String, HashMap<String, Double>> monthlyReceived = new HashMap<String, HashMap<String, Double>>();

    public PDSSystem() {
        warehouses = new HashMap<String, Warehouse>();
        shops = new HashMap<String, FairPriceShop>();
        vehicles = new HashMap<String, DeliveryVehicle>();
        requests = new ArrayList<DeliveryRequest>();
        dispatchRecords = new ArrayList<DispatchRecord>();
        receiptHistory = new ArrayList<String>();
        roadMap = new Graph();
        nextArrivalNumber = 1;
        nextDispatchNumber = 1;
    }

    public void addWarehouse(Warehouse warehouse) {
        warehouses.put(warehouse.getWarehouseId(), warehouse);
        roadMap.addLocation(warehouse.getWarehouseId());
    }

    public void addShop(FairPriceShop shop) {
        shops.put(shop.getShopId(), shop);
        roadMap.addLocation(shop.getShopId());
    }

    public void addVehicle(DeliveryVehicle vehicle) {
        vehicles.put(vehicle.getVehicleId(), vehicle);
    }

    public void addRoad(String firstLocation, String secondLocation, double travelTime) {
        roadMap.addRoad(firstLocation, secondLocation, travelTime);
    }

    public ArrayList<DeliveryRequest> getRequests() {
        return requests;
    }

    public ArrayList<DispatchRecord> getDispatchRecords() {
        return dispatchRecords;
    }

    public ArrayList<FairPriceShop> getShops() {
        return new ArrayList<FairPriceShop>(shops.values());
    }

    public ArrayList<Warehouse> getWarehouses() {
        return new ArrayList<Warehouse>(warehouses.values());
    }

    public FairPriceShop getShop(String shopId) {
        return shops.get(shopId);
    }
    public DeliveryRequest getRequest(String requestId) { for (DeliveryRequest r : requests) if (r.getRequestId().equals(requestId)) return r; return null; }

    public Warehouse getWarehouse(String warehouseId) { return warehouses.get(warehouseId); }
    public boolean renameWarehouse(String id, String name) {
        Warehouse warehouse = warehouses.get(id);
        if (warehouse == null || name == null || name.trim().isEmpty()) return false;
        warehouse.setName(name);
        return true;
    }
    public boolean renameShop(String id, String name) {
        FairPriceShop shop = shops.get(id);
        if (shop == null || name == null || name.trim().isEmpty()) return false;
        shop.setName(name);
        return true;
    }
    public boolean updateLocationDetails(String id, boolean warehouse, String name, String connectTo, double travelTime) {
        boolean exists = warehouse ? warehouses.containsKey(id) : shops.containsKey(id);
        if (!exists || name == null || name.trim().isEmpty()) return false;
        if (connectTo != null && !connectTo.trim().isEmpty()) {
            if (connectTo.equals(id) || !hasLocation(connectTo) || travelTime <= 0 || !Double.isFinite(travelTime)) return false;
        }

        if (warehouse) warehouses.get(id).setName(name);
        else shops.get(id).setName(name);
        if (connectTo != null && !connectTo.trim().isEmpty()) {
            addOrUpdateRoad(id, connectTo.trim(), travelTime);
        }
        return true;
    }

    public boolean updateShopPopulation(String id, int people) { FairPriceShop shop = shops.get(id); if (shop == null || people < 0) return false; shop.setPopulation(people); return true; }

    public void setMonthlyUsage(String month, HashMap<String, HashMap<String, Double>> used) { quotaMonth = month; monthlyUsed = used; }
    public void setMonthlyReceived(HashMap<String, HashMap<String, Double>> received) { monthlyReceived = received; }
    public double getMonthlyReceived(String shopId, String commodity) { HashMap<String, Double> row = monthlyReceived.get(shopId); return row == null || row.get(commodity) == null ? 0 : row.get(commodity); }
    public double getMonthlyUsed(String shopId, String commodity) { HashMap<String, Double> row = monthlyUsed.get(shopId); return row == null || row.get(commodity) == null ? 0 : row.get(commodity); }
    public double getMonthlyRemaining(String shopId, String commodity) { FairPriceShop shop = shops.get(shopId); return shop == null ? 0 : Math.max(0, shop.getMonthlyQuota(commodity) - getMonthlyUsed(shopId, commodity)); }

    public boolean addOrUpdateRoad(String from, String to, double minutes) {
        if (from == null || to == null || from.trim().isEmpty() || to.trim().isEmpty()) return false;
        from = from.trim();
        to = to.trim();
        if (from.equals(to) || !hasLocation(from) || !hasLocation(to)
                || minutes <= 0 || !Double.isFinite(minutes)) return false;
        roadMap.addRoad(from, to, minutes);
        return true;
    }

    public boolean removeRoad(String from, String to) {
        if (from == null || to == null || from.equals(to)) return false;
        return roadMap.removeRoad(from, to);
    }

    public boolean removeWarehouse(String id) {
        Warehouse warehouse = warehouses.get(id);
        if (warehouse == null) return false;
        for (DispatchRecord record : dispatchRecords) {
            if (record.getWarehouse().getWarehouseId().equals(id)) return false;
        }
        warehouses.remove(id);
        roadMap.removeLocation(id);
        return true;
    }

    public boolean removeShop(String id) {
        if (!shops.containsKey(id)) return false;
        for (DeliveryRequest request : requests) {
            if (request.getShop().getShopId().equals(id)) return false;
        }
        shops.remove(id);
        roadMap.removeLocation(id);
        return true;
    }
    public void resetRoadMap() {
        roadMap = new Graph();
        for (Warehouse warehouse : warehouses.values()) roadMap.addLocation(warehouse.getWarehouseId());
        for (FairPriceShop shop : shops.values()) roadMap.addLocation(shop.getShopId());
    }
    public Graph getRoadMap() { return roadMap; }
    public boolean hasLocation(String id) { return warehouses.containsKey(id) || shops.containsKey(id) || roadMap.getRoads().containsKey(id); }
    public void addNewWarehouse(String id, String name, double rice, double wheat, double sugar, String connectTo, double travelTime) {
        validateNewLocation(id, name, travelTime);
        if (hasLocation(id)) throw new IllegalArgumentException("That location ID already exists.");
        if (connectTo == null || !roadMap.getRoads().containsKey(connectTo)) throw new IllegalArgumentException("Choose a connected location that exists.");
        Warehouse w = new Warehouse(id, name); w.addStock(new CommodityStock("Rice", rice)); w.addStock(new CommodityStock("Wheat", wheat)); w.addStock(new CommodityStock("Sugar", sugar));
        addWarehouse(w); addRoad(id, connectTo, travelTime);
    }
    public void addNewShop(String id, String name, double rice, double wheat, double sugar, String connectTo, double travelTime) {
        validateNewLocation(id, name, travelTime);
        if (hasLocation(id)) throw new IllegalArgumentException("That location ID already exists.");
        if (connectTo == null || !roadMap.getRoads().containsKey(connectTo)) throw new IllegalArgumentException("Choose a connected location that exists.");
        FairPriceShop s = new FairPriceShop(id, name); s.addStock(new CommodityStock("Rice", rice)); s.addStock(new CommodityStock("Wheat", wheat)); s.addStock(new CommodityStock("Sugar", sugar));
        addShop(s); addRoad(id, connectTo, travelTime);
    }
    public boolean updateWarehouseStock(String id, String commodity, double amount) {
        Warehouse w = warehouses.get(id); return w != null && w.setStock(commodity, amount);
    }
    private void validateNewLocation(String id, String name, double travelTime) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Enter both an ID and a name.");
        if (travelTime <= 0) throw new IllegalArgumentException("Road travel time must be greater than zero.");
    }
    public ArrayList<DeliveryVehicle> getVehicles() { return new ArrayList<DeliveryVehicle>(vehicles.values()); }
    public void setNextArrivalNumber(int number) { if (number > nextArrivalNumber) nextArrivalNumber = number; }
    public void setNextDispatchNumber(int number) { if (number > nextDispatchNumber) nextDispatchNumber = number; }
    public void restoreDispatch(DispatchRecord record) { dispatchRecords.add(record); if (!record.isReceived()) record.getVehicle().assignVehicle(); }

    public DeliveryRequest createNewRequest(String shopId, String urgency,
                                            HashMap<String, Double> items) {
        String requestId = "REQ" + nextArrivalNumber;
        return createRequest(requestId, shopId, urgency, items);
    }

    public ArrayList<String> getReceiptHistory() {
        return receiptHistory;
    }

    public boolean confirmReceipt(String dispatchId) {
        for (int i = 0; i < dispatchRecords.size(); i++) {
            DispatchRecord record = dispatchRecords.get(i);
            if (record.getRecordId().equalsIgnoreCase(dispatchId)) {
                if (record.confirmReceipt()) {
                    receiptHistory.add(record.getRecordId() + " - " + record.getRequest().getShop().getName());
                    return true;
                }
                return false;
            }
        }
        return false;
    }

     public DeliveryRequest createRequest(String requestId, String shopId,
                                         String urgency, HashMap<String, Double> items) {
        FairPriceShop shop = shops.get(shopId);
        if (shop == null) {
            return null;
        }

        DeliveryRequest request = new DeliveryRequest(requestId, shop,
                urgency, nextArrivalNumber);
        for (String commodity : items.keySet()) {
            request.addRequirement(commodity, items.get(commodity));
        }
        requests.add(request);
        nextArrivalNumber++;
        return request;
    }

     public void processRequests() {
        PriorityQueue<DeliveryRequest> requestQueue =
                new PriorityQueue<DeliveryRequest>(new ReplenishmentPolicy());

         for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).getStatus().equals("PENDING")) {
                requestQueue.add(requests.get(i));
            }
        }

        if (requestQueue.isEmpty()) {
            System.out.println("There are no pending requests.");
            return;
        }

        while (!requestQueue.isEmpty()) {
            DeliveryRequest request = requestQueue.poll();
            tryDispatch(request);
        }
    }

     private Warehouse findWarehouseFor(DeliveryRequest request) {
        ArrayList<String> warehouseIds = new ArrayList<String>(warehouses.keySet());
        Collections.sort(warehouseIds);
        Warehouse bestWarehouse = null;
        double shortestTime = Double.MAX_VALUE;

        for (int i = 0; i < warehouseIds.size(); i++) {
            Warehouse warehouse = warehouses.get(warehouseIds.get(i));
            Route route = roadMap.findFastestRoute(
                    warehouse.getWarehouseId(), request.getShop().getShopId());

            if (route != null && hasAllStock(warehouse, request)) {
                if (route.getTotalTime() < shortestTime) {
                    shortestTime = route.getTotalTime();
                    bestWarehouse = warehouse;
                }
            }
        }
        return bestWarehouse;
    }

    private boolean hasAllStock(Warehouse warehouse, DeliveryRequest request) {
        for (String commodity : request.getRequirements().keySet()) {
            double amount = request.getRequirements().get(commodity);
            if (!warehouse.canFulfill(commodity, amount)) {
                return false;
            }
        }
        return true;
    }

     private void tryDispatch(DeliveryRequest request) {
        FairPriceShop shop = request.getShop();
        for (String commodity : request.getRequirements().keySet()) {
            double amount = request.getRequirements().get(commodity);
            double remaining = getMonthlyRemaining(shop.getShopId(), commodity);
            if (amount > remaining + 0.000001) {
                keepPending(request, "Monthly " + commodity + " limit is " + shop.getMonthlyQuota(commodity) + " kg; " + remaining + " kg remains for " + shop.getName() + ".");
                return;
            }
        }
        Warehouse warehouse = findWarehouseFor(request);
        if (warehouse == null) {
            if (!hasAnyRouteTo(request.getShop())) {
                keepPending(request, "The shop cannot be reached from either warehouse.");
            } else {
                keepPending(request, "No reachable warehouse has all requested commodities.");
            }
            return;
        }

        Route route = roadMap.findFastestRoute(
                warehouse.getWarehouseId(), request.getShop().getShopId());
        DeliveryVehicle vehicle = findVehicleFor(request.getTotalWeight());
        if (vehicle == null) {
            keepPending(request, vehiclePendingReason(request.getTotalWeight()));
            return;
        }

         boolean reservationWorked = true;
        for (String commodity : request.getRequirements().keySet()) {
            double amount = request.getRequirements().get(commodity);
            if (!warehouse.reserveStock(commodity, amount)) {
                reservationWorked = false;
                break;
            }
        }

        if (!reservationWorked) {
            keepPending(request, "Stock changed before the whole request could be reserved.");
            return;
        }

         for (String commodity : request.getRequirements().keySet()) {
            double amount = request.getRequirements().get(commodity);
            warehouse.dispatchStock(commodity, amount);
        }
        vehicle.assignVehicle();
        request.setStatus("DISPATCHED");
        request.setPendingReason("");

        String recordId = "DSP" + nextDispatchNumber;
        nextDispatchNumber++;
        DispatchRecord record = new DispatchRecord(recordId, request,
                warehouse, vehicle, route);
        dispatchRecords.add(record);
        HashMap<String, Double> used = monthlyUsed.get(shop.getShopId());
        if (used == null) { used = new HashMap<String, Double>(); monthlyUsed.put(shop.getShopId(), used); }
        for (String commodity : request.getRequirements().keySet()) {
            Double old = used.get(commodity); used.put(commodity, (old == null ? 0 : old) + request.getRequirements().get(commodity));
        }
        System.out.println("Request " + request.getRequestId() + " dispatched.");
        record.displayRecord();
    }

    private boolean hasAnyRouteTo(FairPriceShop shop) {
        for (Warehouse warehouse : warehouses.values()) {
            if (roadMap.findFastestRoute(warehouse.getWarehouseId(), shop.getShopId()) != null) {
                return true;
            }
        }
        return false;
    }

     private DeliveryVehicle findVehicleFor(double weight) {
        ArrayList<String> vehicleIds = new ArrayList<String>(vehicles.keySet());
        Collections.sort(vehicleIds);
        DeliveryVehicle selected = null;

        for (int i = 0; i < vehicleIds.size(); i++) {
            DeliveryVehicle vehicle = vehicles.get(vehicleIds.get(i));
            if (vehicle.canCarry(weight)) {
                if (selected == null || vehicle.getCapacity() < selected.getCapacity()) {
                    selected = vehicle;
                }
            }
        }
        return selected;
    }

    private String vehiclePendingReason(double weight) {
        double largestCapacity = 0;
        double largestFreeCapacity = 0;
        int freeVehicles = 0;

        for (DeliveryVehicle vehicle : vehicles.values()) {
            if (vehicle.getCapacity() > largestCapacity) {
                largestCapacity = vehicle.getCapacity();
            }
            if (vehicle.isAvailable()) {
                freeVehicles++;
                if (vehicle.getCapacity() > largestFreeCapacity) {
                    largestFreeCapacity = vehicle.getCapacity();
                }
            }
        }

        String requestWeight = String.format(java.util.Locale.US, "%.1f", weight);
        String biggestVehicle = String.format(java.util.Locale.US, "%.1f", largestCapacity);

        if (vehicles.isEmpty()) {
            return "No delivery vehicles are configured.";
        }
        if (weight > largestCapacity) {
            return "Request weighs " + requestWeight + " kg, above the largest vehicle capacity of "
                    + biggestVehicle + " kg.";
        }
        if (freeVehicles == 0) {
            return "Request weighs " + requestWeight + " kg and fits the fleet, but all "
                    + vehicles.size() + " vehicles are assigned to open deliveries. Confirm a shop receipt to release one.";
        }

        String freeCapacity = String.format(java.util.Locale.US, "%.1f", largestFreeCapacity);
        return "Request weighs " + requestWeight + " kg and fits the fleet, but the largest free vehicle carries "
                + freeCapacity + " kg. Confirm an open delivery receipt to release a larger vehicle.";
    }

    private void keepPending(DeliveryRequest request, String reason) {
        request.setStatus("PENDING");
        request.setPendingReason(reason);
        System.out.println(request.getRequestId() + " is still pending: " + reason);
    }

    public void displayWarehouseStock() {
        for (Warehouse warehouse : warehouses.values()) {
            warehouse.displayStock();
        }
    }

    public void displayShopStock() {
        for (FairPriceShop shop : shops.values()) {
            System.out.println("\nShop ID: " + shop.getShopId());
            shop.displayStock();
        }
    }

    public void displayRequests() {
        if (requests.isEmpty()) {
            System.out.println("No requests have been entered.");
            return;
        }
        for (int i = 0; i < requests.size(); i++) {
            requests.get(i).displayRequest();
        }
    }

    public void displayDispatchRecords() {
        if (dispatchRecords.isEmpty()) {
            System.out.println("No delivery trips have been dispatched.");
            return;
        }
        for (int i = 0; i < dispatchRecords.size(); i++) {
            dispatchRecords.get(i).displayRecord();
        }
    }

     private void confirmReceiptFromMenu(Scanner input) {
        System.out.print("Enter dispatch ID (for example DSP1): ");
        String id = input.nextLine().trim();
        for (int i = 0; i < dispatchRecords.size(); i++) {
            DispatchRecord record = dispatchRecords.get(i);
            if (record.getRecordId().equalsIgnoreCase(id)) {
                if (record.confirmReceipt()) {
                    receiptHistory.add(record.getRecordId() + " - "
                            + record.getRequest().getShop().getName());
                    System.out.println("Receipt confirmed. Shop stock was updated.");
                } else {
                    System.out.println("Receipt was already confirmed.");
                }
                return;
            }
        }
        System.out.println("Dispatch ID was not found.");
    }

    private void displayReceiptHistory() {
        if (receiptHistory.isEmpty()) {
            System.out.println("No receipts have been confirmed.");
            return;
        }
        System.out.println("Confirmed receipts:");
        for (int i = 0; i < receiptHistory.size(); i++) {
            System.out.println(receiptHistory.get(i));
        }
    }

    private void createRequestFromMenu(Scanner input) {
        System.out.println("Available shops:");
        ArrayList<String> shopIds = new ArrayList<String>(shops.keySet());
        Collections.sort(shopIds);
        for (int i = 0; i < shopIds.size(); i++) {
            FairPriceShop shop = shops.get(shopIds.get(i));
            System.out.println(shop.getShopId() + " - " + shop.getName());
        }

        System.out.print("Enter shop ID: ");
        String shopId = input.nextLine().trim();
        if (!shops.containsKey(shopId)) {
            System.out.println("Shop ID was not found.");
            return;
        }

        System.out.print("Enter urgency (HIGH, MEDIUM, or LOW): ");
        String urgency = input.nextLine().trim().toUpperCase();
        if (!urgency.equals("HIGH") && !urgency.equals("MEDIUM")
                && !urgency.equals("LOW")) {
            System.out.println("Urgency must be HIGH, MEDIUM, or LOW.");
            return;
        }

        HashMap<String, Double> items = new HashMap<String, Double>();
        System.out.println("Enter commodity and quantity. Leave commodity blank to finish.");
        while (true) {
            System.out.print("Commodity: ");
            String commodity = input.nextLine().trim();
            if (commodity.isEmpty()) {
                break;
            }
            System.out.print("Quantity in kg: ");
            double amount = Double.parseDouble(input.nextLine().trim());
            if (amount > 0) {
                items.put(commodity, amount);
            } else {
                System.out.println("Quantity must be greater than zero.");
            }
        }

        if (items.isEmpty()) {
            System.out.println("No request was created because no items were entered.");
        } else {
            String requestId = "REQ" + nextArrivalNumber;
            createRequest(requestId, shopId, urgency, items);
            System.out.println("Created request " + requestId + ".");
        }
    }

    public void run() {
        Scanner input = new Scanner(System.in);
        boolean running = true;
        while (running) {
            System.out.println("\n=== Valam Public Distribution System ===");
            System.out.println("1. View warehouse stock");
            System.out.println("2. View shop stock");
            System.out.println("3. Create delivery request");
            System.out.println("4. View requests");
            System.out.println("5. Process pending requests");
            System.out.println("6. View dispatch records");
            System.out.println("7. Confirm delivery receipt");
            System.out.println("8. View receipt history");
            System.out.println("0. Exit");
            System.out.print("Choose: ");
            String choice = input.nextLine().trim();

            if (choice.equals("1")) {
                displayWarehouseStock();
            } else if (choice.equals("2")) {
                displayShopStock();
            } else if (choice.equals("3")) {
                createRequestFromMenu(input);
            } else if (choice.equals("4")) {
                displayRequests();
            } else if (choice.equals("5")) {
                processRequests();
            } else if (choice.equals("6")) {
                displayDispatchRecords();
            } else if (choice.equals("7")) {
                confirmReceiptFromMenu(input);
            } else if (choice.equals("8")) {
                displayReceiptHistory();
            } else if (choice.equals("0")) {
                running = false;
                 
            } else {
                System.out.println("Please choose one of the listed options.");
            }
        }
    }
}
