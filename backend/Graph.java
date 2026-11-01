import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

 public class Graph {
    private HashMap<String, ArrayList<Edge>> roads;

    public Graph() {
        roads = new HashMap<String, ArrayList<Edge>>();
    }

    public void addLocation(String location) {
        if (!roads.containsKey(location)) {
            roads.put(location, new ArrayList<Edge>());
        }
    }

    public HashMap<String, ArrayList<Edge>> getRoads() { return roads; }

     public void addRoad(String firstLocation, String secondLocation, double travelTime) {
        addLocation(firstLocation);
        addLocation(secondLocation);
        for (Edge e : roads.get(firstLocation)) {
            if (e.getDestination().equals(secondLocation)) {
                e.setTravelTime(travelTime);
                for (Edge reverse : roads.get(secondLocation)) {
                    if (reverse.getDestination().equals(firstLocation)) {
                        reverse.setTravelTime(travelTime);
                        return;
                    }
                }
                roads.get(secondLocation).add(new Edge(firstLocation, travelTime));
                return;
            }
        }
        roads.get(firstLocation).add(new Edge(secondLocation, travelTime));
        roads.get(secondLocation).add(new Edge(firstLocation, travelTime));
    }

    public boolean removeRoad(String firstLocation, String secondLocation) {
        if (!roads.containsKey(firstLocation) || !roads.containsKey(secondLocation)) return false;
        boolean removed = removeOneWayRoad(firstLocation, secondLocation);
        removeOneWayRoad(secondLocation, firstLocation);
        return removed;
    }

    private boolean removeOneWayRoad(String from, String to) {
        ArrayList<Edge> links = roads.get(from);
        if (links == null) return false;
        for (int i = links.size() - 1; i >= 0; i--) {
            if (links.get(i).getDestination().equals(to)) {
                links.remove(i);
                return true;
            }
        }
        return false;
    }

    public void removeLocation(String location) {
        roads.remove(location);
        for (ArrayList<Edge> links : roads.values()) {
            for (int i = links.size() - 1; i >= 0; i--) {
                if (links.get(i).getDestination().equals(location)) links.remove(i);
            }
        }
    }

     public Route findFastestRoute(String start, String end) {
        if (!roads.containsKey(start) || !roads.containsKey(end)) {
            return null;
        }

        final HashMap<String, Double> times = new HashMap<String, Double>();
        final HashMap<String, String> previous = new HashMap<String, String>();
        ArrayList<String> visited = new ArrayList<String>();

        for (String location : roads.keySet()) {
            times.put(location, Double.MAX_VALUE);
        }
        times.put(start, 0.0);

         PriorityQueue<LocationDistance> queue = new PriorityQueue<LocationDistance>(
                new Comparator<LocationDistance>() {
                    public int compare(LocationDistance a, LocationDistance b) {
                        return Double.compare(a.time, b.time);
                    }
                });
        queue.add(new LocationDistance(start, 0.0));

        while (!queue.isEmpty()) {
            LocationDistance current = queue.poll();
            if (visited.contains(current.location)) {
                continue;
            }
            visited.add(current.location);

            if (current.location.equals(end)) {
                break;
            }

            ArrayList<Edge> nearbyRoads = roads.get(current.location);
            for (int i = 0; i < nearbyRoads.size(); i++) {
                Edge road = nearbyRoads.get(i);
                String nextLocation = road.getDestination();
                double newTime = current.time + road.getTravelTime();

                if (newTime < times.get(nextLocation)) {
                    times.put(nextLocation, newTime);
                    previous.put(nextLocation, current.location);
                    queue.add(new LocationDistance(nextLocation, newTime));
                }
            }
        }

        if (times.get(end) == Double.MAX_VALUE) {
            return null;
        }

        ArrayList<String> routeLocations = new ArrayList<String>();
        String location = end;
        while (location != null) {
            routeLocations.add(location);
            if (location.equals(start)) {
                break;
            }
            location = previous.get(location);
        }
        Collections.reverse(routeLocations);
        return new Route(routeLocations, times.get(end));
    }

    // A location and the travel time needed to reach it.
    private class LocationDistance {
        private String location;
        private double time;

        public LocationDistance(String location, double time) {
            this.location = location;
            this.time = time;
        }
    }
}
