package dashboard;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JButton;

/** Compact, DPI-independent Graphics2D navigation item for the LinkView sidebar. */
final class SidebarNavButton extends JButton {
    enum Icon { OVERVIEW, DEVICES, PERFORMANCE, NETWORK, ALERTS, LOGS }
    private final String page;
    private final Icon icon;
    private boolean collapsed;
    private boolean active;

    SidebarNavButton(String page, Icon icon) {
        this.page = page; this.icon = icon;
        setToolTipText(page); setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);
        setOpaque(false); setFont(Theme.BODY); setHorizontalAlignment(LEFT); setAlignmentX(Component.LEFT_ALIGNMENT);
    }
    void setCollapsed(boolean value) { collapsed = value; repaint(); }
    void setActive(boolean value) { active = value; repaint(); }
    @Override public java.awt.Dimension getMaximumSize() { return new java.awt.Dimension(Integer.MAX_VALUE, 38); }
    @Override public java.awt.Dimension getPreferredSize() { return new java.awt.Dimension(230, 38); }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean hover = getModel().isRollover();
        if (active || hover) { g.setColor(active ? Theme.SIDEBAR_ACTIVE : Theme.SIDEBAR_HOVER); g.fillRoundRect(8, 2, getWidth() - 16, getHeight() - 4, 6, 6); }
        Color foreground = active ? Theme.TEXT : Theme.MUTED;
        int iconX = collapsed ? (getWidth() - 18) / 2 : 19;
        drawIcon(g, iconX, 10, foreground);
        if (!collapsed) { g.setColor(foreground); g.setFont(Theme.BODY); g.drawString(page, 50, 24); }
        g.dispose();
    }
    private void drawIcon(Graphics2D g, int x, int y, Color color) {
        g.setColor(color); g.setStroke(new BasicStroke(1.55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (icon) {
            case OVERVIEW -> { g.drawOval(x + 2, y + 2, 14, 14); g.fillOval(x + 7, y + 7, 4, 4); }
            case DEVICES -> { g.drawRoundRect(x + 2, y + 2, 14, 14, 2, 2); g.drawLine(x + 6, y + 5, x + 12, y + 5); g.drawLine(x + 6, y + 9, x + 12, y + 9); g.drawLine(x + 6, y + 13, x + 10, y + 13); }
            case PERFORMANCE -> { g.drawLine(x + 2, y + 14, x + 6, y + 10); g.drawLine(x + 6, y + 10, x + 9, y + 12); g.drawLine(x + 9, y + 12, x + 15, y + 5); g.fillOval(x + 13, y + 3, 4, 4); }
            case NETWORK -> { g.drawLine(x + 5, y + 3, x + 5, y + 16); g.drawLine(x + 2, y + 6, x + 5, y + 3); g.drawLine(x + 8, y + 6, x + 5, y + 3); g.drawLine(x + 14, y + 16, x + 14, y + 3); g.drawLine(x + 11, y + 13, x + 14, y + 16); g.drawLine(x + 17, y + 13, x + 14, y + 16); }
            case ALERTS -> { Path2D p = new Path2D.Double(); p.moveTo(x + 9, y + 2); p.lineTo(x + 17, y + 16); p.lineTo(x + 1, y + 16); p.closePath(); g.draw(p); g.drawLine(x + 9, y + 7, x + 9, y + 11); g.fillOval(x + 8, y + 13, 2, 2); }
            case LOGS -> { for (int i = 0; i < 3; i++) { int line = y + 4 + i * 5; g.fillOval(x + 2, line - 1, 2, 2); g.drawLine(x + 6, line, x + 16, line); } }
        }
    }
}
