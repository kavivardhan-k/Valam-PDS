import java.util.ArrayList;

 public class Route {
    private ArrayList<String> locations;
    private double totalTime;

    public Route(ArrayList<String> locations, double totalTime) {
        this.locations = locations;
        this.totalTime = totalTime;
    }

    public ArrayList<String> getLocations() {
        return locations;
    }

    public double getTotalTime() {
        return totalTime;
    }

    public void displayRoute() {
        for (int i = 0; i < locations.size(); i++) {
            System.out.print(locations.get(i));
            if (i < locations.size() - 1) {
                System.out.print(" -> ");
            }
        }
        System.out.println(" (" + totalTime + " minutes)");
    }
}
