package client;

import com.sun.management.OperatingSystemMXBean;
import common.ClientMetrics;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.Enumeration;

/** Reads host metrics. Network counters use macOS netstat when it is available. */
public final class SystemMetrics {
    private final OperatingSystemMXBean operatingSystem = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private long previousReceived = -1;
    private long previousTransmitted = -1;
    private long previousSampleNanos = -1;
    private final String deviceId;
    private final String hostName;
    private final String ipAddress;

    public SystemMetrics() throws IOException {
        InetAddress local = InetAddress.getLocalHost();
        hostName = local.getHostName();
        ipAddress = findAddress(local);
        deviceId = hostName + "-" + ProcessHandle.current().pid();
    }

    public ClientMetrics collect() {
        Double cpu = operatingSystem == null ? null : percentage(operatingSystem.getCpuLoad());
        Long totalMemory = operatingSystem == null ? null : positive(operatingSystem.getTotalMemorySize());
        Long freeMemory = operatingSystem == null ? null : positive(operatingSystem.getFreeMemorySize());
        Double memory = totalMemory == null || freeMemory == null || totalMemory == 0 ? null
                : ((totalMemory - freeMemory) * 100.0 / totalMemory);
        long[] counters = macNetworkCounters();
        Double download = null;
        Double upload = null;
        long now = System.nanoTime();
        if (counters != null && previousSampleNanos > 0) {
            double seconds = (now - previousSampleNanos) / 1_000_000_000.0;
            if (seconds > 0 && counters[0] >= previousReceived && counters[1] >= previousTransmitted) {
                download = (counters[0] - previousReceived) / seconds;
                upload = (counters[1] - previousTransmitted) / seconds;
            }
        }
        if (counters != null) {
            previousReceived = counters[0];
            previousTransmitted = counters[1];
            previousSampleNanos = now;
        }
        return new ClientMetrics(deviceId, hostName, ipAddress, Instant.now().toEpochMilli(), cpu, totalMemory, freeMemory,
                memory, counters == null ? null : counters[0], counters == null ? null : counters[1], download, upload);
    }

    private static Double percentage(double value) { return value < 0 ? null : Math.min(100.0, value * 100.0); }
    private static Long positive(long value) { return value < 0 ? null : value; }

    private static String findAddress(InetAddress fallback) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return fallback.getHostAddress();
    }

    private static long[] macNetworkCounters() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return null;
        long received = 0;
        long transmitted = 0;
        boolean found = false;
        try {
            Process process = new ProcessBuilder("netstat", "-ib").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 10 || parts[0].startsWith("lo")) continue;
                    Long in = counter(parts, 6);
                    Long out = counter(parts, 9);
                    if (in != null && out != null) { received += in; transmitted += out; found = true; }
                }
            }
            process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException ignored) { }
        return found ? new long[] {received, transmitted} : null;
    }

    private static Long counter(String[] values, int index) {
        try { return Long.parseLong(values[index]); } catch (NumberFormatException exception) { return null; }
    }
}
