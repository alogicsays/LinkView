package dashboard;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/** Header control drawn with Graphics2D so it remains crisp on Retina displays. */
final class SidebarToggleButton extends JButton {
    SidebarToggleButton() { setToolTipText("Collapse sidebar"); setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false); setPreferredSize(new java.awt.Dimension(40, 44)); }
    @Override protected void paintComponent(Graphics graphics) { Graphics2D g=(Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); if(getModel().isRollover()){g.setColor(Theme.SIDEBAR_HOVER);g.fillRoundRect(5,8,30,28,6,6);} g.setColor(Theme.MUTED);g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawLine(13,17,27,17);g.drawLine(13,22,27,22);g.drawLine(13,27,27,27);g.dispose(); }
}
