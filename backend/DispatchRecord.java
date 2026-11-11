 public class DispatchRecord {
    private String recordId;
    private DeliveryRequest request;
    private Warehouse warehouse;
    private DeliveryVehicle vehicle;
    private Route route;
    private boolean received;

    public DispatchRecord(String recordId, DeliveryRequest request,
                          Warehouse warehouse, DeliveryVehicle vehicle, Route route) {
        this.recordId = recordId;
        this.request = request;
        this.warehouse = warehouse;
        this.vehicle = vehicle;
        this.route = route;
        received = false;
    }

    public String getRecordId() {
        return recordId;
    }

    public DeliveryRequest getRequest() {
        return request;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public DeliveryVehicle getVehicle() {
        return vehicle;
    }

    public Route getRoute() {
        return route;
    }

    public boolean isReceived() {
        return received;
    }
    public void setReceived(boolean value) { received = value; }

     public boolean confirmReceipt() {
        if (received) {
            return false;
        }

        for (String commodity : request.getRequirements().keySet()) {
            double amount = request.getRequirements().get(commodity);
            request.getShop().receiveDelivery(commodity, amount);
        }
        received = true;
        request.setStatus("DELIVERED");
        return true;
    }

    public void displayRecord() {
        System.out.println("\nDispatch ID: " + recordId);
        System.out.println("Request ID: " + request.getRequestId());
        System.out.println("Warehouse: " + warehouse.getName());
        System.out.println("Shop: " + request.getShop().getName());
        System.out.println("Vehicle: " + vehicle.getVehicleName());
        System.out.print("Route: ");
        route.displayRoute();
        if (received) {
            System.out.println("Receipt: Confirmed");
        } else {
            System.out.println("Receipt: Waiting for confirmation");
        }
    }
}
