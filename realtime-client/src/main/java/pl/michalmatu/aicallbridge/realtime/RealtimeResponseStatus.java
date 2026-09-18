package pl.michalmatu.aicallbridge.realtime;

/** Final lifecycle status reported by a GA Realtime response.done server event. */
public enum RealtimeResponseStatus {
    COMPLETED,
    CANCELLED,
    FAILED,
    INCOMPLETE,
    UNKNOWN;

    public static RealtimeResponseStatus fromWireValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return switch (value) {
            case "completed" -> COMPLETED;
            case "cancelled" -> CANCELLED;
            case "failed" -> FAILED;
            case "incomplete" -> INCOMPLETE;
            default -> UNKNOWN;
        };
    }
}
