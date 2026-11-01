// One road from a location to a nearby location.
public class Edge {
    private String destination;
    private double travelTime;

    public Edge(String destination, double travelTime) {
        this.destination = destination;
        this.travelTime = travelTime;
    }

    public String getDestination() {
        return destination;
    }

    public double getTravelTime() {
        return travelTime;
    }

    public void setTravelTime(double travelTime) {
        this.travelTime = travelTime;
    }
}
