package server;

import common.ClientMetrics;
import dashboard.Dashboard;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** TCP server and thread-safe in-memory model consumed by the Swing dashboard. */
public final class MonitoringServer {
    private static final int HISTORY_LIMIT = 720;
    private final int port;
    private final Map<String, DeviceState> devices = new ConcurrentHashMap<>();
    private final Map<String, Boolean> alertState = new ConcurrentHashMap<>();
    private final List<String> events = java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<Alert> alerts = java.util.Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile double cpuThreshold = 85.0;
    private volatile double memoryThreshold = 85.0;
    private ServerSocket serverSocket;

    public MonitoringServer(int port) { this.port = port; }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 && !"--no-ui".equals(args[0]) ? Integer.parseInt(args[0]) : 5050;
        MonitoringServer server = new MonitoringServer(port);
        server.start();
        if (java.util.Arrays.asList(args).contains("--no-ui")) {
            System.out.println("LinkView monitoring server running on port " + port);
            try { Thread.currentThread().join(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); server.stop(); }
        } else Dashboard.show(server);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running.set(true);
        event("LinkView server started on TCP port " + port);
        Thread acceptor = new Thread(this::acceptLoop, "linkview-server-acceptor");
        acceptor.setDaemon(true); acceptor.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "linkview-server-shutdown"));
    }

    private void acceptLoop() {
        while (running.get()) try { Socket socket = serverSocket.accept(); clients.execute(new ClientHandler(socket, this)); }
        catch (IOException exception) { if (running.get()) event("Accept error: " + exception.getMessage()); }
    }

    public void accept(ClientMetrics metrics) {
        long now = Instant.now().toEpochMilli();
        DeviceState state = devices.compute(metrics.deviceId(), (key, old) -> {
            boolean newConnection = old == null || !ClientMetrics.CONNECTED.equals(old.status);
            long connectedAt = newConnection ? now : old.connectedAt;
            List<ClientMetrics> history = old == null ? new ArrayList<>() : new ArrayList<>(old.history);
            history.add(metrics);
            if (history.size() > HISTORY_LIMIT) history.remove(0);
            return new DeviceState(metrics, ClientMetrics.CONNECTED, connectedAt, now, history);
        });
        if (state.history.size() == 1 || state.connectedAt == now) event("Client connected: " + metrics.deviceId());
        evaluateThreshold(metrics, "CPU", metrics.cpuPercent(), cpuThreshold);
        evaluateThreshold(metrics, "Memory", metrics.memoryPercent(), memoryThreshold);
    }

    private void evaluateThreshold(ClientMetrics metrics, String metric, Double value, double threshold) {
        String key = metrics.deviceId() + ':' + metric;
        boolean exceeded = value != null && value >= threshold;
        boolean wasExceeded = Boolean.TRUE.equals(alertState.put(key, exceeded));
        if (exceeded && !wasExceeded) {
            Alert alert = new Alert(Instant.now().toEpochMilli(), metrics.deviceId(), metrics.hostName(), metric, value, threshold, true);
            synchronized (alerts) { alerts.add(alert); if (alerts.size() > 500) alerts.remove(0); }
            event("ALERT " + metric + " threshold exceeded by " + metrics.deviceId() + ": " + String.format("%.1f%%", value));
        } else if (!exceeded && wasExceeded) event("Alert cleared: " + metric + " on " + metrics.deviceId());
    }

    public void disconnected(String deviceId, String reason) {
        if (deviceId == null) return;
        devices.computeIfPresent(deviceId, (key, state) -> new DeviceState(state.metrics, ClientMetrics.DISCONNECTED, state.connectedAt,
                Instant.now().toEpochMilli(), state.history));
        event("Client disconnected: " + deviceId + " (" + reason + ")");
    }

    public void event(String message) {
        String line = DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + "  " + message;
        synchronized (events) { events.add(line); if (events.size() > 500) events.remove(0); }
        try { Files.createDirectories(Path.of("data")); Files.writeString(Path.of("data", "monitoring.log"), line + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); } catch (IOException ignored) { }
    }

    public void exportCsv(Path file) throws IOException {
        StringBuilder output = new StringBuilder("timestamp,device_id,hostname,ip_address,status,cpu_percent,total_memory_bytes,available_memory_bytes,memory_percent,received_bytes,transmitted_bytes,download_bytes_per_second,upload_bytes_per_second\n");
        List<DeviceState> snapshot = new ArrayList<>(devices.values());
        snapshot.sort(Comparator.comparing(state -> state.metrics.deviceId()));
        for (DeviceState state : snapshot) for (ClientMetrics metric : state.history) output.append(csv(metric.timestamp())).append(',')
                .append(csv(metric.deviceId())).append(',').append(csv(metric.hostName())).append(',').append(csv(metric.ipAddress())).append(',')
                .append(csv(state.status)).append(',').append(csv(metric.cpuPercent())).append(',').append(csv(metric.totalMemoryBytes())).append(',')
                .append(csv(metric.availableMemoryBytes())).append(',').append(csv(metric.memoryPercent())).append(',').append(csv(metric.receivedBytes())).append(',')
                .append(csv(metric.transmittedBytes())).append(',').append(csv(metric.downloadBytesPerSecond())).append(',').append(csv(metric.uploadBytesPerSecond())).append('\n');
        Files.writeString(file, output.toString()); event("CSV exported: " + file.getFileName());
    }

    private static String csv(Object value) { String text = value == null ? "" : value.toString(); return '"' + text.replace("\"", "\"\"") + '"'; }
    public Collection<DeviceState> devices() { return List.copyOf(devices.values()); }
    public List<String> events() { synchronized (events) { return List.copyOf(events); } }
    public List<Alert> alerts() { synchronized (alerts) { return List.copyOf(alerts); } }
    public double cpuThreshold() { return cpuThreshold; }
    public double memoryThreshold() { return memoryThreshold; }
    public void setThresholds(double cpu, double memory) { cpuThreshold = cpu; memoryThreshold = memory; event("Alert thresholds updated: CPU " + cpu + "%, Memory " + memory + "%"); }
    public int port() { return port; }
    public boolean isRunning() { return running.get(); }
    public void stop() { if (!running.getAndSet(false)) return; try { serverSocket.close(); } catch (IOException ignored) { } clients.shutdownNow(); event("Server stopped"); }

    public static final class DeviceState {
        private final ClientMetrics metrics; private final String status; private final long connectedAt; private final long lastSeen; private final List<ClientMetrics> history;
        DeviceState(ClientMetrics metrics, String status, long connectedAt, long lastSeen, List<ClientMetrics> history) { this.metrics = metrics; this.status = status; this.connectedAt = connectedAt; this.lastSeen = lastSeen; this.history = List.copyOf(history); }
        public ClientMetrics metrics() { return metrics; } public String status() { return status; } public long connectedAt() { return connectedAt; } public long lastSeen() { return lastSeen; } public List<ClientMetrics> history() { return history; }
    }
    public record Alert(long timestamp, String deviceId, String hostName, String metric, double value, double threshold, boolean active) { }
}
