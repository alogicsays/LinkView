package common;

/** Immutable snapshot sent by one monitoring client. Null numeric fields mean unavailable. */
public record ClientMetrics(
        String deviceId,
        String hostName,
        String ipAddress,
        long timestamp,
        Double cpuPercent,
        Long totalMemoryBytes,
        Long availableMemoryBytes,
        Double memoryPercent,
        Long receivedBytes,
        Long transmittedBytes,
        Double downloadBytesPerSecond,
        Double uploadBytesPerSecond) {

    public static final String CONNECTED = "Connected";
    public static final String DISCONNECTED = "Disconnected";
    public static final String RECONNECTING = "Reconnecting";
}
