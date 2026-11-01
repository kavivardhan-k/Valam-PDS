import java.util.HashMap;
public class Warehouse {
    private String warehouseId;
    private String name;
    private HashMap<String, CommodityStock> stocks;
    public Warehouse(String warehouseId, String name) {
        this.warehouseId = warehouseId;
        this.name = name;
        stocks = new HashMap<>();
    }
    public void addStock(CommodityStock stock) {
        stocks.put(stock.getCommodityName(), stock);
    }
    public CommodityStock getStock(String commodityName) {
        return stocks.get(commodityName);
    }
    public boolean setStock(String commodityName, double amount) {
        CommodityStock stock = stocks.get(commodityName);
        if (stock == null) { stock = new CommodityStock(commodityName, 0); stocks.put(commodityName, stock); }
        return stock.setQuantity(amount);
    }
    public boolean canFulfill(String commodityName, double amount) {
        CommodityStock stock = stocks.get(commodityName);
        if (stock == null) {
            return false;
        }
        return stock.getAvailableQuantity() >= amount;
    }
    public boolean reserveStock(String commodityName, double amount) {
        CommodityStock stock = stocks.get(commodityName);
        if (stock == null) {
            return false;
        }
        return stock.reserveStock(amount);
    }
    public boolean dispatchStock(String commodityName, double amount) {
        CommodityStock stock = stocks.get(commodityName);
        if (stock == null) {
            return false;
        }
        return stock.dispatchStock(amount);
    }
    public String getWarehouseId() {
        return warehouseId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) { if (name != null && !name.trim().isEmpty()) this.name = name.trim(); }
    public void displayStock() {
        System.out.println("\nWarehouse: " + name);
        for (CommodityStock stock : stocks.values()) {
            System.out.println(
                stock.getCommodityName()
                + " : "
                + stock.getQuantity()
                + " kg"
                + " | Reserved: "
                + stock.getReservedQuantity()
                + " kg"
                + " | Available: "
                + stock.getAvailableQuantity()
                + " kg"
            );
        }
    }
}
