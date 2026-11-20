package client;

import common.Protocol;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runnable TCP monitoring client. Usage: MonitoringClient [host] [port] [intervalSeconds]. */
public final class MonitoringClient {
    private final String host;
    private final int port;
    private final long intervalMillis;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public MonitoringClient(String host, int port, long intervalMillis) { this.host = host; this.port = port; this.intervalMillis = intervalMillis; }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5050;
        long interval = args.length > 2 ? Long.parseLong(args[2]) * 1000L : 2000L;
        new MonitoringClient(host, port, interval).run();
    }

    public void run() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false), "client-shutdown"));
        try {
            SystemMetrics metrics = new SystemMetrics();
            while (running.get()) {
                try (Socket socket = new Socket(host, port);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                    System.out.println("Connected to " + host + ":" + port);
                    while (running.get() && !socket.isClosed()) {
                        writer.write(Protocol.encode(metrics.collect()));
                        writer.newLine();
                        writer.flush();
                        sleep(intervalMillis);
                    }
                } catch (IOException exception) {
                    System.err.println("Connection unavailable: " + exception.getMessage() + "; retrying in 3 seconds.");
                    sleep(3000);
                }
            }
        } catch (IOException exception) { System.err.println("Unable to initialize metrics: " + exception.getMessage()); }
    }

    private static void sleep(long milliseconds) { try { Thread.sleep(milliseconds); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } }
}
