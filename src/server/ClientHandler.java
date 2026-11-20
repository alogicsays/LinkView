package server;

import common.ClientMetrics;
import common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** One handler per TCP connection; buffered readLine handles partial TCP reads. */
final class ClientHandler implements Runnable {
    private final Socket socket;
    private final MonitoringServer server;

    ClientHandler(Socket socket, MonitoringServer server) { this.socket = socket; this.server = server; }

    @Override public void run() {
        String deviceId = null;
        try (socket; BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    ClientMetrics metrics = Protocol.parse(line);
                    deviceId = metrics.deviceId();
                    server.accept(metrics);
                } catch (Protocol.ProtocolException exception) {
                    server.event("Malformed message from " + socket.getRemoteSocketAddress() + ": " + exception.getMessage());
                }
            }
        } catch (IOException exception) {
            server.event("Connection error from " + socket.getRemoteSocketAddress() + ": " + exception.getMessage());
        } finally { server.disconnected(deviceId, "connection closed"); }
    }
}
