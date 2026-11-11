import java.util.ArrayList;
import java.util.HashMap;

 public class Main {
    public static void main(String[] args) {
        PDSSystem system = createSampleSystem();
        system.run();
    }

    public static PDSSystem createSampleSystem() {
        PDSSystem system = new PDSSystem();

        Warehouse warehouse1 = new Warehouse("W1", "Valam North Warehouse");
        warehouse1.addStock(new CommodityStock("Rice", 1000));
        warehouse1.addStock(new CommodityStock("Wheat", 1000));
        warehouse1.addStock(new CommodityStock("Sugar", 50));
        system.addWarehouse(warehouse1);

        Warehouse warehouse2 = new Warehouse("W2", "Valam South Warehouse");
        warehouse2.addStock(new CommodityStock("Rice", 1000));
        warehouse2.addStock(new CommodityStock("Wheat", 1000));
        warehouse2.addStock(new CommodityStock("Sugar", 0));
        system.addWarehouse(warehouse2);

         FairPriceShop shop1 = makeShop("FPS01", "North One");
        FairPriceShop shop2 = makeShop("FPS02", "North Two");
        FairPriceShop shop3 = makeShop("FPS03", "Central One");
        FairPriceShop shop4 = makeShop("FPS04", "Central Two");
        FairPriceShop shop5 = makeShop("FPS05", "South One");
        FairPriceShop shop6 = makeShop("FPS06", "Remote Shop (no road)");
        system.addShop(shop1);
        system.addShop(shop2);
        system.addShop(shop3);
        system.addShop(shop4);
        system.addShop(shop5);
        system.addShop(shop6);
        shop1.setPopulation(320); shop2.setPopulation(280); shop3.setPopulation(450);
        shop4.setPopulation(390); shop5.setPopulation(350); shop6.setPopulation(120);

         system.addVehicle(new SmallVan("V1", "Small Van", 300));
        system.addVehicle(new SupplyTruck("V2", "Supply Truck 1", 1000));
        system.addVehicle(new SupplyTruck("V3", "Supply Truck 2", 1000));

        addDefaultCompleteRoadConnections(system);

         addRequest(system, "REQ01", "FPS01", "LOW", "Rice", 20);
        addRequest(system, "REQ02", "FPS02", "LOW", "Wheat", 15);
        addRequest(system, "REQ03", "FPS03", "MEDIUM", "Rice", 25);
        addRequest(system, "REQ04", "FPS04", "LOW", "Sugar", 5);
        addRequest(system, "REQ05", "FPS05", "MEDIUM", "Wheat", 30);
        addRequest(system, "REQ06", "FPS01", "LOW", "Rice", 10);
        addRequest(system, "REQ07", "FPS02", "MEDIUM", "Wheat", 20);
        addRequest(system, "REQ08", "FPS03", "LOW", "Rice", 15);
        addRequest(system, "REQ09", "FPS04", "MEDIUM", "Wheat", 25);
        addRequest(system, "REQ10", "FPS05", "LOW", "Rice", 20);

         addRequest(system, "REQ11", "FPS06", "HIGH", "Rice", 20);
         HashMap<String, Double> overloadedItems = new HashMap<String, Double>();
        overloadedItems.put("Rice", 600.0);
        overloadedItems.put("Wheat", 500.0);
        system.createRequest("REQ12", "FPS03", "HIGH", overloadedItems);
         addRequest(system, "REQ13", "FPS01", "HIGH", "Sugar", 60);
         addRequest(system, "REQ14", "FPS01", "HIGH", "Sugar", 40);
        addRequest(system, "REQ15", "FPS01", "HIGH", "Sugar", 40);

        return system;
    }

    public static void addDefaultCompleteRoadConnections(PDSSystem system) {
        ArrayList<String> places = new ArrayList<String>();
        places.add("W1"); places.add("W2");
        places.add("J1"); places.add("J2"); places.add("J3"); places.add("J4");
        places.add("FPS01"); places.add("FPS02"); places.add("FPS03");
        places.add("FPS04"); places.add("FPS05"); places.add("FPS06");
        for (int i = 0; i < places.size(); i++) {
            for (int j = i + 1; j < places.size(); j++) {
                // Different positive times make the network connected while leaving
                // faster multi-road routes for Dijkstra's algorithm to discover.
                double minutes = 7.0 + Math.abs(j - i) * 1.6 + ((i * 17 + j * 29) % 19);
                minutes = Math.round(minutes * 10.0) / 10.0;
                system.addRoad(places.get(i), places.get(j), minutes);
            }
        }
    }

    private static FairPriceShop makeShop(String id, String name) {
        FairPriceShop shop = new FairPriceShop(id, "Valam " + name + " Fair Price Shop");
        shop.addStock(new CommodityStock("Rice", 20));
        shop.addStock(new CommodityStock("Wheat", 20));
        shop.addStock(new CommodityStock("Sugar", 10));
        return shop;
    }

    private static void addRequest(PDSSystem system, String id, String shopId,
                                   String urgency, String commodity, double amount) {
        HashMap<String, Double> items = new HashMap<String, Double>();
        items.put(commodity, amount);
        system.createRequest(id, shopId, urgency, items);
    }
}
