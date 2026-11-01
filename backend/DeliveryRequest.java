import java.util.HashMap;
public class DeliveryRequest {
    private String requestId;
    private FairPriceShop shop;
    private HashMap<String, Double> requirements;
    private String urgency;
    private int arrivalOrder;
    private String status;
    private String pendingReason;
    public DeliveryRequest(
            String requestId,
            FairPriceShop shop,
            String urgency,
            int arrivalOrder) {
        this.requestId = requestId;
        this.shop = shop;
        this.urgency = urgency;
        this.arrivalOrder = arrivalOrder;
        requirements = new HashMap<>();
        status = "PENDING";
        pendingReason = "";
    }
    public void addRequirement(String commodityName, double quantity) {
        if (quantity > 0) {
            requirements.put(commodityName, quantity);
        }
    }
    public HashMap<String, Double> getRequirements() {
        return requirements;
    }
    public double getTotalWeight() {
        double total = 0;
        for (double quantity : requirements.values()) {
            total += quantity;
        }
        return total;
    }
    public int getUrgencyPriority() {
        switch (urgency.toUpperCase()) {
            case "HIGH":
                return 3;
            case "MEDIUM":
                return 2;
            case "LOW":
                return 1;
            default:
                return 0;
        }
    }
    public String getRequestId() {
        return requestId;
    }
    public FairPriceShop getShop() {
        return shop;
    }
    public String getUrgency() {
        return urgency;
    }
    public int getArrivalOrder() {
        return arrivalOrder;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getPendingReason() {
        return pendingReason;
    }
    public void setPendingReason(String pendingReason) {
        this.pendingReason = pendingReason;
    }
    public void displayRequest() {
        System.out.println("\nRequest ID: " + requestId);
        System.out.println("Shop: " + shop.getName());
        System.out.println("Urgency: " + urgency);
        System.out.println("Arrival Order: " + arrivalOrder);
        System.out.println("Status: " + status);
        System.out.println("Requirements:");
        for (String commodity : requirements.keySet()) {
            System.out.println(
                commodity + " : "
                + requirements.get(commodity)
                + " kg"
            );
        }
        System.out.println("Total Weight: " + getTotalWeight() + " kg");
        if (!pendingReason.isEmpty()) {
            System.out.println("Reason: " + pendingReason);
        }
    }
}