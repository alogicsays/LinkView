package dashboard;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

final class RingPanel extends JPanel {
    private final String label;
    private final Color color;
    private Double value;
    RingPanel(String label, Color color) { this.label = label; this.color = color; setOpaque(false); }
    void setValue(Double value) { this.value = value; repaint(); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D drawing = (Graphics2D) graphics.create();
        drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = Math.max(40, Math.min(getWidth(), getHeight()) - 34);
        int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
        drawing.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawing.setColor(Theme.BORDER); drawing.drawArc(x, y, size, size, 0, 360);
        if (value != null) { drawing.setColor(color); drawing.drawArc(x, y, size, size, 90, (int) -Math.round(360 * value / 100)); }
        String number = value == null ? "N/A" : String.format("%.0f%%", value);
        drawing.setFont(Theme.VALUE); drawing.setColor(Theme.TEXT); center(drawing, number, getHeight() / 2 - 2);
        drawing.setFont(Theme.BODY); drawing.setColor(Theme.MUTED); center(drawing, label, getHeight() / 2 + 21);
        drawing.dispose();
    }
    private void center(Graphics2D drawing, String text, int baseline) { FontMetrics metrics = drawing.getFontMetrics(); drawing.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2, baseline); }
}
