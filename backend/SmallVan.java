public class SmallVan extends DeliveryVehicle {

    public SmallVan(
            String vehicleId,
            String vehicleName,
            double capacity) {

        super(vehicleId, vehicleName, capacity);
    }

    @Override
    public boolean canCarry(double weight) {

        return isAvailable() && weight <= capacity;
    }
}