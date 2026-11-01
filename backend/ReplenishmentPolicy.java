import java.util.Comparator;
public class ReplenishmentPolicy implements Comparator<DeliveryRequest> {
    @Override
    public int compare(DeliveryRequest r1, DeliveryRequest r2) {
        int urgencyResult =
                Integer.compare(
                        r2.getUrgencyPriority(),
                        r1.getUrgencyPriority()
                );
        if (urgencyResult != 0) {
            return urgencyResult;
        }
        int arrivalResult =
                Integer.compare(
                        r1.getArrivalOrder(),
                        r2.getArrivalOrder()
                );
        if (arrivalResult != 0) {
            return arrivalResult;
        }
         
        return r1.getRequestId()
                .compareTo(r2.getRequestId());
    }
}