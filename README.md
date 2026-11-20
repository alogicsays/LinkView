# LinkView — Real-Time Network Monitoring System

LinkView is a dependency-free Java 21 desktop utility for real-time TCP network monitoring. It combines a central server, independent monitoring clients, and a compact Swing/Graphics2D workspace designed for a local macOS demonstration or LAN use.

## Objectives

- Accept metrics from multiple clients concurrently over TCP.
- Report real host CPU, physical-memory, and network activity when the host exposes them.
- Present live device state in the polished LinkView desktop utility.
- Handle connection loss, malformed data, reconnection, and graceful shutdown without external dependencies.

## Architecture

Each `MonitoringClient` collects one host snapshot and sends it over a persistent TCP connection. `MonitoringServer` accepts each socket on a separate executor thread, validates each message, retains the most recent device state, appends events to `data/monitoring.log`, and exposes the model to the Swing dashboard. The dashboard refreshes on the Swing Event Dispatch Thread; it never waits on network I/O.

Running several clients on one Mac is supported for demonstration. They use distinct process IDs as device identifiers, but correctly report the same underlying physical machine's metrics. The dashboard does not invent separate metrics.

## Protocol

Messages are UTF-8 text, one message per newline:

```
METRICS|deviceId|hostName|ipAddress|epochMillis|cpuPercent|totalMemoryBytes|availableMemoryBytes|memoryPercent|receivedBytes|transmittedBytes|downloadBytesPerSecond|uploadBytesPerSecond
```

Text fields are URL encoded to keep `|` and line boundaries safe. Missing metrics use `NA`. The server uses `BufferedReader.readLine()`, so fragmented TCP packets are assembled before parsing. A malformed line is logged and ignored; it cannot terminate another client connection.

## Folder structure

```
src/common/     Shared protocol and immutable metric snapshot
src/client/     Metric collection and reconnecting TCP client
src/server/     Concurrent TCP server and client handler
src/dashboard/  Swing dashboard and Graphics2D visualizations
data/           Created on first server event; contains monitoring.log
```

## Compile

Requires JDK 21. From the project directory:

```bash
mkdir -p out
javac --release 21 -d out $(find src -name '*.java')
```

## Run

Start the server and dashboard on the default port (`5050`):

```bash
java -cp out server.MonitoringServer
```

For a server-only terminal run (useful on a machine without a graphical display), add `--no-ui`:

```bash
java -cp out server.MonitoringServer 5050 --no-ui
```

In separate terminals, start one or more clients. Arguments are `[serverHost] [port] [intervalSeconds]`:

```bash
java -cp out client.MonitoringClient 127.0.0.1 5050 2
java -cp out client.MonitoringClient 127.0.0.1 5050 2
java -cp out client.MonitoringClient 127.0.0.1 5050 2
```

For a LAN demonstration, launch the server with a chosen port and use the server Mac's LAN address in each client command:

```bash
java -cp out server.MonitoringServer 5050
java -cp out client.MonitoringClient 192.168.1.10 5050 2
```

Close a client with `Ctrl+C`; the server records it as disconnected. A client waits three seconds and reconnects automatically while the server is unavailable. Close the dashboard to stop the server cleanly.

## LinkView desktop guide

The dark desktop UI has a collapsible sidebar: use the top-left toggle to change between the full 230 px navigation and a 68 px icon rail. Tooltips remain available in collapsed mode. Navigation is organised into Monitor (Overview, Devices), Analytics (Performance, Network Activity), and Management (Alerts, Activity Logs).

- **Overview** shows connected client-process count, aggregate CPU/memory, and aggregate host network throughput.
- **Devices** is selectable and includes hostname, IP, process-based client ID, current status, last seen time, connection duration, free/total RAM, and genuine retained CPU/network history.
- **Performance** displays Graphics2D CPU and memory rings and histories from connected clients with available readings.
- **Network Activity** plots download/upload rates in KB/s or MB/s. These are host-level counters, not traffic attributable to an individual monitoring process.
- **Alerts** configures CPU and memory thresholds. An alert is logged only when a value crosses into an exceeded state; recovery is logged when it crosses back.
- **Activity Logs** includes a native save dialog for exporting all retained samples to CSV. CSV fields are quoted and escaped and include timestamp, identifiers, status, CPU, memory, counters, and throughput.

## Metric collection and limitations

CPU load and physical memory are obtained through `com.sun.management.OperatingSystemMXBean`, when the runtime supports them. Network byte counters are read from macOS `netstat -ib`; the client computes throughput from differences in cumulative counters divided by elapsed time. Counter resets and unavailable counters produce `N/A`, not fabricated zeroes. Values are host-level and multiple clients on one Mac intentionally report the same physical host metrics while retaining distinct process-based client IDs.

The network values are machine-wide and can include traffic from all applications and interfaces (except loopback). Interface output varies by macOS version, VPN configuration, and permissions, so this metric can be `N/A`. Each client reports local time; clocks between LAN machines may differ. The application keeps current state in memory and uses a readable local event log rather than a database.

## Suggested verification

1. Start server, then one client; confirm a connected device and periodic metric changes.
2. Start three client processes; confirm three rows with unique process-based IDs in the event log.
3. Stop one client; confirm only that row becomes disconnected while the others continue updating, then restart it to observe reconnection.
4. Start clients before the server; then start the server and verify automatic reconnection.
5. Send a deliberately invalid newline message using a TCP client and verify the server continues running and records the malformed message.
6. Lower an alert threshold temporarily; verify one crossing alert and one recovery event rather than an alert every polling interval.
7. Use **Export CSV…** from Activity Logs and inspect the header and quoted fields in the saved file.
