package dashboard;

import common.ClientMetrics;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import server.MonitoringServer;

/** LinkView's EDT-only Swing presentation. Server access is snapshot based and never blocks. */
public final class Dashboard extends JFrame {
    private final MonitoringServer server;
    private final JLabel title = new JLabel("Overview"), summary = new JLabel(), deviceCount = valueLabel(), avgCpu = valueLabel(), avgMemory = valueLabel(), network = valueLabel();
    private final RingPanel overviewCpuRing = new RingPanel("CPU", Theme.CPU), overviewMemoryRing = new RingPanel("Memory", Theme.MEMORY), performanceCpuRing = new RingPanel("CPU", Theme.CPU), performanceMemoryRing = new RingPanel("Memory", Theme.MEMORY);
    private final LineChart overviewNetworkChart = new LineChart(false, Theme.NETWORK, Theme.MEMORY), cpuChart = new LineChart(true, Theme.CPU), memoryChart = new LineChart(true, Theme.MEMORY), networkChart = new LineChart(false, Theme.NETWORK, Theme.MEMORY);
    private final LineChart deviceCpuChart = new LineChart(true, Theme.CPU), deviceNetworkChart = new LineChart(false, Theme.NETWORK, Theme.MEMORY);
    private final JLabel deviceDetail = new JLabel("Select a client process to inspect its independent connection and host-level metrics.");
    private final DefaultTableModel devices = model("Hostname", "IP address", "Client ID", "CPU", "Memory", "Status", "Last seen");
    private final DefaultTableModel alertRows = model("Time", "Device", "Metric", "Value", "Threshold");
    private final JList<String> logs = new JList<>();
    private final CardLayout cards = new CardLayout(); private final JPanel content = new JPanel(cards); private final JPanel sidebar = new JPanel(); private final JPanel sidebarHeader = new JPanel(new BorderLayout()); private final JLabel sidebarBrand = new JLabel(); private final SidebarToggleButton sidebarToggle = new SidebarToggleButton();
    private final Map<String, SidebarNavButton> nav = new HashMap<>(); private boolean collapsed; private String activePage = "Overview"; private String selectedId;
    private final JSpinner cpuThreshold, memoryThreshold;

    private Dashboard(MonitoringServer server) {
        this.server = server;
        setTitle("LinkView — Real-Time Network Monitoring System"); setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setMinimumSize(new Dimension(1050, 680)); setSize(1280, 820); setLocationByPlatform(true);
        cpuThreshold = spinner(server.cpuThreshold()); memoryThreshold = spinner(server.memoryThreshold());
        setContentPane(root()); new Timer(1000, event -> refresh()).start(); refresh();
    }
    public static void show(MonitoringServer server) { SwingUtilities.invokeLater(() -> new Dashboard(server).setVisible(true)); }

    private JPanel root() {
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(Theme.BACKGROUND); root.add(sidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout(0, 14)); main.setBackground(Theme.BACKGROUND); main.setBorder(BorderFactory.createEmptyBorder(18, 22, 20, 24)); main.add(header(), BorderLayout.NORTH);
        content.setBackground(Theme.BACKGROUND); content.add(overview(), "Overview"); content.add(devicesPage(), "Devices"); content.add(performancePage(), "Performance"); content.add(networkPage(), "Network Activity"); content.add(alertsPage(), "Alerts"); content.add(logPage(), "Activity Logs"); main.add(content, BorderLayout.CENTER); root.add(main); return root;
    }
    private JPanel sidebar() {
        sidebar.setLayout(new javax.swing.BoxLayout(sidebar, javax.swing.BoxLayout.Y_AXIS)); sidebar.setBackground(Theme.SIDEBAR); sidebar.setPreferredSize(new Dimension(230, 0)); sidebar.setMinimumSize(new Dimension(68, 0)); sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        sidebarHeader.setOpaque(false); sidebarHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); sidebarHeader.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 8)); sidebarHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        sidebarBrand.setText("LINKVIEW"); sidebarBrand.setForeground(Theme.TEXT); sidebarBrand.setFont(Theme.BRAND); sidebarHeader.add(sidebarBrand, BorderLayout.CENTER);
        sidebarToggle.addActionListener(event -> toggleSidebar()); sidebarHeader.add(sidebarToggle, BorderLayout.EAST); sidebar.add(sidebarHeader);
        navHeading("MONITOR"); navigation("Overview", SidebarNavButton.Icon.OVERVIEW); navigation("Devices", SidebarNavButton.Icon.DEVICES); navHeading("ANALYTICS"); navigation("Performance", SidebarNavButton.Icon.PERFORMANCE); navigation("Network Activity", SidebarNavButton.Icon.NETWORK); navHeading("MANAGEMENT"); navigation("Alerts", SidebarNavButton.Icon.ALERTS); navigation("Activity Logs", SidebarNavButton.Icon.LOGS); return sidebar;
    }
    private void navHeading(String label) { JLabel heading = new JLabel(label); heading.setName("heading"); heading.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); heading.setForeground(Theme.MUTED); heading.setFont(Theme.SMALL); heading.setBorder(BorderFactory.createEmptyBorder(12, 18, 4, 0)); heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 33)); sidebar.add(heading); }
    private void navigation(String page, SidebarNavButton.Icon icon) { SidebarNavButton button = new SidebarNavButton(page, icon); button.addActionListener(event -> selectPage(page)); nav.put(page, button); sidebar.add(button); }
    private void styleButton(JButton button) { button.setForeground(Theme.MUTED); button.setFont(Theme.BODY); button.setFocusPainted(false); button.setBorderPainted(false); button.setOpaque(true); button.setBackground(Theme.SIDEBAR); }
    private void toggleSidebar() { collapsed = !collapsed; sidebar.setPreferredSize(new Dimension(collapsed ? 68 : 230, 0)); sidebarHeader.removeAll(); if (collapsed) { sidebarHeader.setBorder(BorderFactory.createEmptyBorder(7, 8, 6, 8)); sidebarHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82)); sidebarBrand.setText("LV"); sidebarBrand.setHorizontalAlignment(JLabel.CENTER); sidebarHeader.add(sidebarBrand, BorderLayout.NORTH); sidebarHeader.add(sidebarToggle, BorderLayout.SOUTH); sidebarToggle.setToolTipText("Expand sidebar"); } else { sidebarHeader.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 8)); sidebarHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54)); sidebarBrand.setText("LINKVIEW"); sidebarBrand.setHorizontalAlignment(JLabel.LEFT); sidebarHeader.add(sidebarBrand, BorderLayout.CENTER); sidebarHeader.add(sidebarToggle, BorderLayout.EAST); sidebarToggle.setToolTipText("Collapse sidebar"); } for (java.awt.Component c : sidebar.getComponents()) if (c instanceof JLabel label && "heading".equals(label.getName())) label.setVisible(!collapsed); for (SidebarNavButton button : nav.values()) button.setCollapsed(collapsed); sidebar.revalidate(); getContentPane().revalidate(); sidebar.repaint(); }
    private void selectPage(String page) { activePage = page; title.setText(page); cards.show(content, page); for (Map.Entry<String, SidebarNavButton> item : nav.entrySet()) item.getValue().setActive(item.getKey().equals(page)); }
    private JPanel header() { JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false); title.setFont(Theme.TITLE); title.setForeground(Theme.TEXT); summary.setForeground(Theme.MUTED); summary.setFont(Theme.BODY); header.add(title); header.add(summary, BorderLayout.EAST); return header; }

    private JPanel overview() { JPanel page = page(new BorderLayout(0, 14)); JPanel stats = new JPanel(new GridLayout(1, 4, 10, 0)); stats.setOpaque(false); stats.setPreferredSize(new Dimension(0, 94)); stats.add(metric("CONNECTED PROCESSES", deviceCount)); stats.add(metric("AVERAGE CPU", avgCpu)); stats.add(metric("AVERAGE MEMORY", avgMemory)); stats.add(metric("HOST NETWORK RATE", network)); page.add(stats, BorderLayout.NORTH); JPanel lower = new JPanel(new GridLayout(1, 2, 14, 0)); lower.setOpaque(false); JPanel rings = page(new GridLayout(1, 2, 4, 0)); rings.add(overviewCpuRing); rings.add(overviewMemoryRing); lower.add(section("System overview", "Aggregated only across connected reporting processes", rings)); lower.add(section("Host network throughput", "Download blue  •  Upload green  •  OS host counters", overviewNetworkChart)); page.add(lower); return page; }
    private JPanel performancePage() { JPanel page = page(new BorderLayout(0, 14)); JPanel rings = page(new GridLayout(1, 2, 16, 0)); rings.setPreferredSize(new Dimension(0, 210)); rings.add(performanceCpuRing); rings.add(performanceMemoryRing); page.add(section("Performance", "Live averages from available client samples", rings), BorderLayout.NORTH); JPanel history = new JPanel(new GridLayout(1, 2, 14, 0)); history.setOpaque(false); history.add(section("CPU history", "Aggregate received samples", cpuChart)); history.add(section("Memory history", "Aggregate received samples", memoryChart)); page.add(history); return page; }
    private JPanel networkPage() { return section("Network activity", "Host-level counters; each client process may report the same physical host", networkChart); }
    private JPanel devicesPage() { JPanel page = page(new BorderLayout(0, 14)); JTable table = table(devices); table.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting() && table.getSelectedRow() >= 0) { selectedId = (String) devices.getValueAt(table.getSelectedRow(), 2); refreshDeviceDetail(); } }); page.add(section("Connected clients", "Each row is a monitoring client process; host metrics are not attributed to a process", darkScroll(table)), BorderLayout.CENTER); JPanel detail = panel(new BorderLayout(0, 10)); deviceDetail.setForeground(Theme.MUTED); deviceDetail.setFont(Theme.BODY); detail.add(deviceDetail, BorderLayout.NORTH); JPanel charts = new JPanel(new GridLayout(1, 2, 12, 0)); charts.setOpaque(false); charts.add(section("CPU history", "Selected client", deviceCpuChart)); charts.add(section("Host network history", "Download / upload collected by selected client", deviceNetworkChart)); detail.add(charts); detail.setPreferredSize(new Dimension(0, 235)); page.add(detail, BorderLayout.SOUTH); return page; }
    private JPanel alertsPage() { JPanel page = page(new BorderLayout(0, 14)); JPanel controls = panel(new FlowLayout(FlowLayout.LEFT, 12, 4)); controls.add(label("CPU threshold")); controls.add(cpuThreshold); controls.add(label("Memory threshold")); controls.add(memoryThreshold); JButton apply = new JButton("Apply thresholds"); styleButton(apply); apply.setBackground(Theme.ACCENT); apply.setForeground(Theme.TEXT); apply.addActionListener(event -> server.setThresholds(((Number) cpuThreshold.getValue()).doubleValue(), ((Number) memoryThreshold.getValue()).doubleValue())); controls.add(apply); page.add(controls, BorderLayout.NORTH); page.add(section("Threshold alerts", "Alerts occur on threshold crossings, not every polling cycle", darkScroll(table(alertRows))), BorderLayout.CENTER); return page; }
    private JPanel logPage() { JPanel page = page(new BorderLayout(0, 12)); JPanel action = new JPanel(new BorderLayout()); action.setOpaque(false); JLabel copy = label("Server, connection, export and alert activity"); JButton export = new JButton("Export CSV…"); styleButton(export); export.setBackground(Theme.ACCENT); export.setForeground(Theme.TEXT); export.addActionListener(event -> exportCsv()); action.add(copy); action.add(export, BorderLayout.EAST); page.add(action, BorderLayout.NORTH); logs.setBackground(Theme.PANEL); logs.setForeground(Theme.TEXT); logs.setFont(Theme.BODY); logs.setFixedCellHeight(27); page.add(darkScroll(logs)); return page; }
    private void exportCsv() { JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Export LinkView monitoring data"); chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv")); chooser.setSelectedFile(new java.io.File("linkview-monitoring.csv")); if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return; Path file = chooser.getSelectedFile().toPath(); if (!file.getFileName().toString().toLowerCase().endsWith(".csv")) file = file.resolveSibling(file.getFileName() + ".csv"); try { server.exportCsv(file); } catch (IOException exception) { javax.swing.JOptionPane.showMessageDialog(this, "Unable to export CSV: " + exception.getMessage(), "LinkView", javax.swing.JOptionPane.ERROR_MESSAGE); } }

    private void refresh() { Collection<MonitoringServer.DeviceState> states = server.devices(); int connected = 0, cc = 0, mc = 0, dc = 0, uc = 0; double ct = 0, mt = 0, down = 0, up = 0; String keep = selectedId; devices.setRowCount(0); for (MonitoringServer.DeviceState state : states) { ClientMetrics m = state.metrics(); if (ClientMetrics.CONNECTED.equals(state.status())) { connected++; if (m.cpuPercent()!=null) {ct+=m.cpuPercent();cc++;} if(m.memoryPercent()!=null){mt+=m.memoryPercent();mc++;} if(m.downloadBytesPerSecond()!=null){down+=m.downloadBytesPerSecond();dc++;} if(m.uploadBytesPerSecond()!=null){up+=m.uploadBytesPerSecond();uc++;} } devices.addRow(new Object[]{m.hostName(),m.ipAddress(),m.deviceId(),percent(m.cpuPercent()),percent(m.memoryPercent()),displayStatus(state),time(state.lastSeen())}); } Double cpu=cc==0?null:ct/cc, memory=mc==0?null:mt/mc, download=dc==0?null:down, upload=uc==0?null:up; deviceCount.setText(Integer.toString(connected)); avgCpu.setText(percent(cpu)); avgMemory.setText(percent(memory)); network.setText(download==null&&upload==null?"N/A":rate((download==null?0:download)+(upload==null?0:upload))); overviewCpuRing.setValue(cpu); overviewMemoryRing.setValue(memory); performanceCpuRing.setValue(cpu); performanceMemoryRing.setValue(memory); cpuChart.add(cpu); memoryChart.add(memory); networkChart.add(download,upload); overviewNetworkChart.add(download,upload); logs.setListData(server.events().toArray(String[]::new)); alertRows.setRowCount(0); for (MonitoringServer.Alert alert : server.alerts()) alertRows.addRow(new Object[]{time(alert.timestamp()),alert.hostName(),alert.metric(),percent(alert.value()),percent(alert.threshold())}); summary.setText(server.isRunning()?"● Server online  •  TCP " + server.port():"● Server stopped"); if (keep != null) refreshDeviceDetail(); selectPage(activePage); }
    private void refreshDeviceDetail() { if (selectedId == null) return; for (MonitoringServer.DeviceState state : server.devices()) if (selectedId.equals(state.metrics().deviceId())) { ClientMetrics m=state.metrics(); deviceDetail.setText(""+m.hostName()+"  •  "+m.ipAddress()+"  •  "+m.deviceId()+"  •  "+displayStatus(state)+"  •  connected "+duration(state.connectedAt())+"  •  RAM "+memory(m.availableMemoryBytes(),m.totalMemoryBytes())); List<ClientMetrics> h=state.history(); deviceCpuChart.setSamples(h.stream().map(ClientMetrics::cpuPercent).toList()); deviceNetworkChart.setSamples(h.stream().map(ClientMetrics::downloadBytesPerSecond).toList(), h.stream().map(ClientMetrics::uploadBytesPerSecond).toList()); return; } deviceDetail.setText("Selected client is no longer retained."); }
    private JPanel section(String heading,String subtitle,JComponent component){JPanel p=panel(new BorderLayout(0,10));JPanel l=new JPanel(new BorderLayout(0,2));l.setOpaque(false);JLabel h=label(heading);h.setForeground(Theme.TEXT);JLabel s=label(subtitle);s.setFont(Theme.SMALL);l.add(h,BorderLayout.NORTH);l.add(s,BorderLayout.SOUTH);p.add(l,BorderLayout.NORTH);p.add(component);return p;}
    private JPanel metric(String name,JLabel value){JPanel p=panel(new BorderLayout(0,5));JLabel l=label(name);l.setFont(Theme.SMALL);p.add(l,BorderLayout.NORTH);p.add(value);return p;}
    private JPanel panel(java.awt.LayoutManager layout){JPanel p=new JPanel(layout);p.setBackground(Theme.PANEL);p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),BorderFactory.createEmptyBorder(13,14,13,14)));return p;}
    private JPanel page(java.awt.LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    private JTable table(DefaultTableModel model){JTable t=new JTable(model);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);t.setBackground(Theme.PANEL);t.setForeground(Theme.TEXT);t.setSelectionBackground(Theme.SELECTION);t.setSelectionForeground(Theme.TEXT);t.setGridColor(Theme.BORDER);t.setShowVerticalLines(false);t.setRowHeight(30);t.setFillsViewportHeight(true);t.setFont(Theme.BODY);t.getTableHeader().setBackground(Theme.PANEL);t.getTableHeader().setForeground(Theme.MUTED);t.getTableHeader().setFont(Theme.SMALL);t.getTableHeader().setReorderingAllowed(false);for(int i=0;i<t.getColumnCount();i++){TableColumn c=t.getColumnModel().getColumn(i);c.setPreferredWidth(i==2?240:120);}return t;}
    private JScrollPane darkScroll(JComponent c){JScrollPane s=new JScrollPane(c);s.setBorder(BorderFactory.createEmptyBorder());s.getViewport().setBackground(Theme.PANEL);return s;}
    private static DefaultTableModel model(String... names){return new DefaultTableModel(names,0){@Override public boolean isCellEditable(int r,int c){return false;}};}
    private static JLabel valueLabel(){JLabel l=new JLabel("N/A");l.setForeground(Theme.TEXT);l.setFont(Theme.VALUE);return l;} private static JLabel label(String text){JLabel l=new JLabel(text);l.setForeground(Theme.MUTED);l.setFont(Theme.BODY);return l;} private static JSpinner spinner(double value){JSpinner s=new JSpinner(new SpinnerNumberModel(value,1,100,1));s.setFont(Theme.BODY);return s;}
    private static String displayStatus(MonitoringServer.DeviceState state){return ClientMetrics.DISCONNECTED.equals(state.status())&&Instant.now().toEpochMilli()-state.lastSeen()<3_500?ClientMetrics.RECONNECTING:state.status();}
    private static String percent(Double v){return v==null?"N/A":String.format("%.1f%%",v);} private static String rate(double v){return v>=1_048_576?String.format("%.2f MB/s",v/1_048_576):String.format("%.1f KB/s",v/1024);} private static String time(long millis){return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));} private static String duration(long start){Duration d=Duration.between(Instant.ofEpochMilli(start),Instant.now());return "%dh %02dm %02ds".formatted(d.toHours(),d.toMinutesPart(),d.toSecondsPart());} private static String memory(Long free,Long total){return free==null||total==null?"N/A":String.format("%.1f / %.1f GB free",free/1_073_741_824.0,total/1_073_741_824.0);}
}
