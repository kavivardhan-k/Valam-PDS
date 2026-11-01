public class DeliveryVehicle {

    protected String vehicleId;
    protected String vehicleName;
    protected double capacity;
    protected boolean available;

    public DeliveryVehicle(
            String vehicleId,
            String vehicleName,
            double capacity) {

        this.vehicleId = vehicleId;
        this.vehicleName = vehicleName;
        this.capacity = capacity;
        this.available = true;
    }

    public boolean canCarry(double weight) {
        return available && weight <= capacity;
    }

    public void assignVehicle() {
        available = false;
    }

    public void releaseVehicle() {
        available = true;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getVehicleName() {
        return vehicleName;
    }

    public double getCapacity() {
        return capacity;
    }
}