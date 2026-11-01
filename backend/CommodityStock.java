public class CommodityStock {
    private String commodityName;
    private double quantity;
    private double reservedQuantity;
    public CommodityStock(String commodityName, double quantity) {
        this.commodityName = commodityName;
        this.quantity = quantity;
        this.reservedQuantity = 0;
    }
    public boolean reserveStock(double amount) {
        if (amount <= 0) {
            return false;
        }
        if (amount > getAvailableQuantity()) {
            return false;
        }
        reservedQuantity += amount;
        return true;
    }
    public boolean dispatchStock(double amount) {

        if (amount <= 0) {
            return false;
        }
        if (amount > reservedQuantity) {
            return false;
        }
        quantity -= amount;
        reservedQuantity -= amount;

        return true;
    }
    public void addStock(double amount) {

        if (amount > 0) {
            quantity += amount;
        }
    }
    public boolean setQuantity(double amount) {
        if (amount < reservedQuantity || amount < 0) return false;
        quantity = amount;
        return true;
    }
    public double getAvailableQuantity() {
        return quantity - reservedQuantity;
    }
    public double getQuantity() {
        return quantity;
    }
    public double getReservedQuantity() {
        return reservedQuantity;
    }
    public String getCommodityName() {
        return commodityName;
    }
}
