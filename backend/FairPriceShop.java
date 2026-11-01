import java.util.HashMap;
public class FairPriceShop {
    private String shopId;
    private String name;
    private HashMap<String, CommodityStock> stocks;
    private int population;
    private static final HashMap<String, Double> MONTHLY_RATE = new HashMap<String, Double>();
    static { MONTHLY_RATE.put("Rice", 5.0); MONTHLY_RATE.put("Wheat", 2.0); MONTHLY_RATE.put("Sugar", 1.0); }
    public FairPriceShop(String shopId, String name) {
        this.shopId = shopId;
        this.name = name;
        stocks = new HashMap<>();
        population = 0;
    }
    public void addStock(CommodityStock stock) {
        stocks.put(stock.getCommodityName(), stock);
    }
    public CommodityStock getStock(String commodityName) {
        return stocks.get(commodityName);
    }
    public void receiveDelivery(String commodityName, double amount) {
        CommodityStock stock = stocks.get(commodityName);
        if (stock == null) {
            stock = new CommodityStock(commodityName, 0);
            stocks.put(commodityName, stock);
        }
        stock.addStock(amount);
    }
    public int getPopulation() { return population; }
    public void setPopulation(int people) { if (people < 0) throw new IllegalArgumentException("People count cannot be negative."); population = people; }
    public double getMonthlyQuota(String commodity) { Double rate = MONTHLY_RATE.get(commodity); return rate == null ? 0 : population * rate; }
    public String getShopId() {
        return shopId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) { if (name != null && !name.trim().isEmpty()) this.name = name.trim(); }
    public void displayStock() {
        System.out.println("\nShop: " + name);
        for (CommodityStock stock : stocks.values()) {
            System.out.println(
                stock.getCommodityName()
                + " : "
                + stock.getQuantity()
                + " kg"
            );
        }
    }
}
