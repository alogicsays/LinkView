package dashboard;

import java.awt.Color;
import java.awt.Font;

final class Theme {
    static final Color BACKGROUND = Color.decode("#111214");
    static final Color SIDEBAR = Color.decode("#17181A");
    static final Color SIDEBAR_HOVER = Color.decode("#202226");
    static final Color SIDEBAR_ACTIVE = Color.decode("#303A4B");
    static final Color PANEL = Color.decode("#222326");
    static final Color BORDER = Color.decode("#35363A");
    static final Color GRID = Color.decode("#303136");
    static final Color SELECTION = Color.decode("#303B50");
    static final Color TEXT = Color.decode("#E8E8EA");
    static final Color MUTED = Color.decode("#929297");
    static final Color CPU = Color.decode("#FF375F");
    static final Color MEMORY = Color.decode("#30D158");
    static final Color NETWORK = Color.decode("#0A84FF");
    static final Color ACCENT = Color.decode("#0B6DC4");
    static final Font TITLE = new Font("SansSerif", Font.BOLD, 22);
    static final Font BRAND = new Font("SansSerif", Font.BOLD, 17);
    static final Font VALUE = new Font("SansSerif", Font.BOLD, 24);
    static final Font BODY = new Font("SansSerif", Font.PLAIN, 13);
    static final Font SMALL = new Font("SansSerif", Font.PLAIN, 11);
    private Theme() { }
}
