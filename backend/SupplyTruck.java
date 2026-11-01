public class SupplyTruck extends DeliveryVehicle {

    public SupplyTruck(
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