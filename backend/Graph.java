import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

public class Graph {
    private HashMap<String, ArrayList<Edge>> roads = new HashMap<String, ArrayList<Edge>>();

    public void addLocation(String location) {
        if (!roads.containsKey(location)) roads.put(location, new ArrayList<Edge>());
    }

    public HashMap<String, ArrayList<Edge>> getRoads() { return roads; }

    public void addRoad(String first, String second, double minutes) {
        addLocation(first); addLocation(second);
        setOneWay(first, second, minutes); setOneWay(second, first, minutes);
    }

    private void setOneWay(String from, String to, double minutes) {
        for (Edge edge : roads.get(from)) {
            if (edge.getDestination().equals(to)) { edge.setTravelTime(minutes); return; }
        }
        roads.get(from).add(new Edge(to, minutes));
    }

    public boolean removeRoad(String first, String second) {
        boolean removed = removeOneWay(first, second);
        removeOneWay(second, first);
        return removed;
    }

    private boolean removeOneWay(String from, String to) {
        ArrayList<Edge> links = roads.get(from);
        if (links == null) return false;
        for (int i = links.size() - 1; i >= 0; i--) {
            if (links.get(i).getDestination().equals(to)) { links.remove(i); return true; }
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
        if (!roads.containsKey(start) || !roads.containsKey(end)) return null;
        HashMap<String, Double> times = new HashMap<String, Double>();
        HashMap<String, String> previous = new HashMap<String, String>();
        ArrayList<String> visited = new ArrayList<String>();
        for (String id : roads.keySet()) times.put(id, Double.MAX_VALUE);
        times.put(start, 0.0);
        PriorityQueue<LocationDistance> queue = new PriorityQueue<LocationDistance>(
            new Comparator<LocationDistance>() {
                public int compare(LocationDistance a, LocationDistance b) { return Double.compare(a.time, b.time); }
            });
        queue.add(new LocationDistance(start, 0.0));
        while (!queue.isEmpty()) {
            LocationDistance current = queue.poll();
            if (visited.contains(current.location)) continue;
            visited.add(current.location);
            if (current.location.equals(end)) break;
            for (Edge edge : roads.get(current.location)) {
                double newTime = current.time + edge.getTravelTime();
                if (newTime < times.get(edge.getDestination())) {
                    times.put(edge.getDestination(), newTime);
                    previous.put(edge.getDestination(), current.location);
                    queue.add(new LocationDistance(edge.getDestination(), newTime));
                }
            }
        }
        if (times.get(end) == Double.MAX_VALUE) return null;
        ArrayList<String> path = new ArrayList<String>();
        String place = end;
        while (place != null) {
            path.add(place);
            if (place.equals(start)) break;
            place = previous.get(place);
        }
        Collections.reverse(path);
        return new Route(path, times.get(end));
    }

    // Finds the quickest order for a vehicle to visit all shops in one trip.
    // The project scope has up to eight shops, so exact dynamic programming is practical.
    public Route findFastestRouteThrough(String start, ArrayList<String> stops) {
        if (!roads.containsKey(start)) return null;
        ArrayList<String> uniqueStops = new ArrayList<String>();
        for (String stop : stops) if (!stop.equals(start) && !uniqueStops.contains(stop)) uniqueStops.add(stop);
        if (uniqueStops.isEmpty()) return new Route(new ArrayList<String>(Collections.singletonList(start)), 0);
        for (String stop : uniqueStops) if (!roads.containsKey(stop)) return null;

        // For unusually large user-created batches, use a nearest-next-stop route.
        if (uniqueStops.size() > 16) return nearestNextRoute(start, uniqueStops);

        int count = uniqueStops.size();
        Route[] fromStart = new Route[count];
        Route[][] between = new Route[count][count];
        for (int i = 0; i < count; i++) {
            fromStart[i] = findFastestRoute(start, uniqueStops.get(i));
            if (fromStart[i] == null) return null;
            for (int j = 0; j < count; j++) if (i != j) {
                between[i][j] = findFastestRoute(uniqueStops.get(i), uniqueStops.get(j));
            }
        }

        int states = 1 << count;
        double[][] best = new double[states][count];
        int[][] parent = new int[states][count];
        for (int mask = 0; mask < states; mask++) {
            for (int last = 0; last < count; last++) { best[mask][last] = Double.MAX_VALUE; parent[mask][last] = -1; }
        }
        for (int i = 0; i < count; i++) best[1 << i][i] = fromStart[i].getTotalTime();
        for (int mask = 1; mask < states; mask++) {
            for (int last = 0; last < count; last++) {
                if ((mask & (1 << last)) == 0 || best[mask][last] == Double.MAX_VALUE) continue;
                for (int next = 0; next < count; next++) {
                    if ((mask & (1 << next)) != 0) continue;
                    Route leg = between[last][next];
                    if (leg == null) continue;
                    int newMask = mask | (1 << next);
                    double newTime = best[mask][last] + leg.getTotalTime();
                    if (newTime < best[newMask][next]) {
                        best[newMask][next] = newTime;
                        parent[newMask][next] = last;
                    }
                }
            }
        }

        int fullMask = states - 1;
        int last = -1;
        double total = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) if (best[fullMask][i] < total) { total = best[fullMask][i]; last = i; }
        if (last < 0 || total == Double.MAX_VALUE) return null;
        ArrayList<Integer> order = new ArrayList<Integer>();
        int mask = fullMask;
        while (last >= 0) {
            order.add(last);
            int oldLast = last;
            last = parent[mask][oldLast];
            mask ^= 1 << oldLast;
        }
        Collections.reverse(order);

        ArrayList<String> fullPath = new ArrayList<String>();
        String from = start;
        for (Integer index : order) {
            Route leg = findFastestRoute(from, uniqueStops.get(index));
            appendLeg(fullPath, leg.getLocations());
            from = uniqueStops.get(index);
        }
        return new Route(fullPath, total);
    }

    private Route nearestNextRoute(String start, ArrayList<String> stops) {
        ArrayList<String> remaining = new ArrayList<String>(stops);
        ArrayList<String> path = new ArrayList<String>();
        path.add(start);
        String current = start;
        double total = 0;
        while (!remaining.isEmpty()) {
            String chosen = null; Route chosenRoute = null;
            for (String stop : remaining) {
                Route route = findFastestRoute(current, stop);
                if (route != null && (chosenRoute == null || route.getTotalTime() < chosenRoute.getTotalTime())) {
                    chosen = stop; chosenRoute = route;
                }
            }
            if (chosenRoute == null) return null;
            appendLeg(path, chosenRoute.getLocations());
            total += chosenRoute.getTotalTime();
            current = chosen;
            remaining.remove(chosen);
        }
        return new Route(path, total);
    }

    private void appendLeg(ArrayList<String> full, ArrayList<String> leg) {
        for (int i = full.isEmpty() ? 0 : 1; i < leg.size(); i++) full.add(leg.get(i));
        if (full.isEmpty() && !leg.isEmpty()) full.add(leg.get(0));
    }

    private class LocationDistance {
        private String location; private double time;
        private LocationDistance(String location, double time) { this.location = location; this.time = time; }
    }
}
