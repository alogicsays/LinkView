package dashboard;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JPanel;

/** A compact Graphics2D chart that retains only genuine samples received by the dashboard. */
final class LineChart extends JPanel {
    private static final int HISTORY_SIZE = 60;
    private final List<Color> colors;
    private final List<List<Double>> history;
    private final boolean percentage;

    LineChart(boolean percentage, Color... colors) {
        this.percentage = percentage;
        this.colors = Arrays.asList(colors);
        this.history = new ArrayList<>();
        for (int index = 0; index < colors.length; index++) history.add(new ArrayList<>());
        setOpaque(false);
    }

    void add(Double... values) {
        for (int index = 0; index < history.size(); index++) {
            List<Double> samples = history.get(index);
            if (samples.size() == HISTORY_SIZE) samples.remove(0);
            samples.add(index < values.length ? values[index] : null);
        }
        repaint();
    }

    /** Replaces a chart with genuine retained measurements for a selected client. */
    @SafeVarargs
    final void setSamples(List<Double>... series) {
        for (int index = 0; index < history.size(); index++) {
            List<Double> samples = history.get(index);
            samples.clear();
            if (index < series.length && series[index] != null) {
                int from = Math.max(0, series[index].size() - HISTORY_SIZE);
                samples.addAll(series[index].subList(from, series[index].size()));
            }
        }
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D drawing = (Graphics2D) graphics.create();
        drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int left = 46, right = 12, top = 10, bottom = 25;
        int chartWidth = Math.max(1, getWidth() - left - right);
        int chartHeight = Math.max(1, getHeight() - top - bottom);
        double maximum = maximum();
        drawing.setFont(Theme.SMALL);
        drawing.setColor(Theme.GRID);
        for (int line = 0; line <= 4; line++) {
            int y = top + chartHeight * line / 4;
            drawing.drawLine(left, y, left + chartWidth, y);
            drawing.setColor(Theme.MUTED);
            String label = axisLabel(maximum * (4 - line) / 4.0);
            FontMetrics metrics = drawing.getFontMetrics();
            drawing.drawString(label, left - metrics.stringWidth(label) - 7, y + 4);
            drawing.setColor(Theme.GRID);
        }
        for (int line = 1; line < 4; line++) drawing.drawLine(left + chartWidth * line / 4, top, left + chartWidth * line / 4, top + chartHeight);
        drawing.setColor(Theme.MUTED);
        drawing.drawString("−60s", left, getHeight() - 7);
        String now = "Now";
        drawing.drawString(now, left + chartWidth - drawing.getFontMetrics().stringWidth(now), getHeight() - 7);
        for (int index = 0; index < history.size(); index++) drawSeries(drawing, history.get(index), colors.get(index), maximum, left, top, chartWidth, chartHeight);
        drawing.dispose();
    }

    private double maximum() {
        if (percentage) return 100;
        double maximum = 1;
        for (List<Double> samples : history) for (Double sample : samples) if (sample != null) maximum = Math.max(maximum, sample);
        return maximum;
    }

    private void drawSeries(Graphics2D drawing, List<Double> samples, Color color, double maximum, int left, int top, int width, int height) {
        if (samples.size() < 2) return;
        drawing.setColor(color);
        drawing.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int previousX = 0, previousY = 0;
        boolean connected = false;
        for (int index = 0; index < samples.size(); index++) {
            Double value = samples.get(index);
            if (value == null) { connected = false; continue; }
            int x = left + (int) Math.round(index * width / (double) Math.max(1, samples.size() - 1));
            int y = top + height - (int) Math.round(Math.min(value, maximum) / maximum * height);
            if (connected) drawing.drawLine(previousX, previousY, x, y);
            previousX = x;
            previousY = y;
            connected = true;
        }
    }

    private String axisLabel(double value) {
        if (percentage) return String.format("%.0f%%", value);
        if (value >= 1_048_576) return String.format("%.1fM", value / 1_048_576);
        if (value >= 1024) return String.format("%.0fK", value / 1024);
        return String.format("%.0f", value);
    }
}
