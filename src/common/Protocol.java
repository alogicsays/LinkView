package common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Newline-delimited, pipe-separated protocol with URL encoded text fields. */
public final class Protocol {
    public static final String TYPE_METRICS = "METRICS";
    private static final String NA = "NA";

    private Protocol() { }

    public static String encode(ClientMetrics metrics) {
        return String.join("|", TYPE_METRICS, text(metrics.deviceId()), text(metrics.hostName()), text(metrics.ipAddress()),
                Long.toString(metrics.timestamp()), number(metrics.cpuPercent()), number(metrics.totalMemoryBytes()),
                number(metrics.availableMemoryBytes()), number(metrics.memoryPercent()), number(metrics.receivedBytes()),
                number(metrics.transmittedBytes()), number(metrics.downloadBytesPerSecond()), number(metrics.uploadBytesPerSecond()));
    }

    public static ClientMetrics parse(String line) throws ProtocolException {
        String[] fields = line.split("\\|", -1);
        if (fields.length != 13 || !TYPE_METRICS.equals(fields[0])) {
            throw new ProtocolException("Expected a 13-field METRICS message");
        }
        try {
            String deviceId = decode(fields[1]);
            String hostName = decode(fields[2]);
            String ipAddress = decode(fields[3]);
            if (deviceId.isBlank() || hostName.isBlank() || ipAddress.isBlank()) {
                throw new ProtocolException("Device identity fields must not be blank");
            }
            long timestamp = Long.parseLong(fields[4]);
            return new ClientMetrics(deviceId, hostName, ipAddress, timestamp, optionalDouble(fields[5]), optionalLong(fields[6]),
                    optionalLong(fields[7]), optionalDouble(fields[8]), optionalLong(fields[9]), optionalLong(fields[10]),
                    optionalDouble(fields[11]), optionalDouble(fields[12]));
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("Invalid metric value: " + exception.getMessage());
        }
    }

    private static Double optionalDouble(String value) { return NA.equals(value) ? null : Double.valueOf(value); }
    private static Long optionalLong(String value) { return NA.equals(value) ? null : Long.valueOf(value); }
    private static String text(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static String number(Object value) { return value == null ? NA : value.toString(); }

    public static final class ProtocolException extends Exception {
        public ProtocolException(String message) { super(message); }
    }
}
