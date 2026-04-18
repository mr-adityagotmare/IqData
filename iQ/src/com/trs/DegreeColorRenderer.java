package com.trs;

import java.awt.Color;
import java.awt.Paint;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;

public class DegreeColorRenderer extends XYLineAndShapeRenderer {

    private double min = -180.0;
    private double max = 180.0;

    @Override
    public Paint getItemPaint(int series, int item) {

        XYDataset dataset = getPlot().getDataset();
        double y = dataset.getYValue(series, item);

        return getColorForValue(y);
    }
    
//    private Color getColorForValue(double value) {
//        float hue = (float)((value + 180) / 360.0); // 0–1
//        return Color.getHSBColor(hue, 1f, 1f);
//    }

    private Color getColorForValue(double value) {

        // Normalize to 0–1
        double norm = (value - min) / (max - min);

        // Clamp
        norm = Math.max(0, Math.min(1, norm));

        // Example gradient:
        // -180 = Blue
        // 0    = Green
        // 180  = Red

        if (norm < 0.5) {
            // Blue → Green
            double ratio = norm * 2;
            return new Color(
                0,
                (int)(255 * ratio),
                (int)(255 * (1 - ratio))
            );
        } else {
            // Green → Red
            double ratio = (norm - 0.5) * 2;
            return new Color(
                (int)(255 * ratio),
                (int)(255 * (1 - ratio)),
                0
            );
        }
    }
}
