package com.trs;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class WaterfallPanel extends JPanel {

    private BufferedImage waterfallImage;
    private int[] colorMap;
    private int[] pixelBuffer;

    private int waterfallWidth, waterfallHeight;
    private int leftOffset = 80;
    private int rightOffset = 10;
    private int topOffset = 10;
    private int bottomOffset = 30;
    
 // ----- Time Scale Settings -----
    private float totalTimeSec = 10f; // total display time in seconds
    private int steps = 5;            // number of divisions on the scale


    public WaterfallPanel() {
        setBackground(Color.BLACK);
    }

    // ========================= INIT =========================
    public void init(int width, int height) {
        waterfallWidth = width;
        waterfallHeight = height;

        if (waterfallWidth > 0 && waterfallHeight > 0) {
            waterfallImage = new BufferedImage(waterfallWidth, waterfallHeight, BufferedImage.TYPE_INT_RGB);
            pixelBuffer = new int[waterfallWidth * waterfallHeight];
        }

        createColorMap();
    }
    
	 // ================= PHASE COLOR =================
	 // phaseDeg: -180 .. +180 (cyclic)
	 private int phaseDegToColor(double phaseDeg) {
	
	   	  if (phaseDeg == 0) {
	   		  
	       // Zero phase → BLACK
	       return Color.BLACK.getRGB();
	       
	   	  }
	   	  else {
		     // Normalize [-180, +180] → [0,1)
		     float hue = (float)((phaseDeg + 180.0) / 360.0);
		
		     // wrap safety
		     hue = hue - (float)Math.floor(hue);
		
		     // Full saturation & brightness
		     return Color.HSBtoRGB(hue, 1.0f, 1.0f);
	   	  }
	   	  
	 }
    
    private void createColorMap() {
    	
        int maxIndex = 180;
        colorMap = new int[maxIndex + 1];

        int segment = maxIndex / 3; // = 66 for maxIndex=200

        for (int i = 0; i <= maxIndex; i++) {
            if (i <= segment) {
                // ---- Black -> Blue ----
                float k = (float)i / segment;
                int r = 0;
                int g = 0;
                int b = (int)(255 * k);
                colorMap[i] = new Color(r, g, b).getRGB();

            } else if (i <= 2 * segment) {
                // ---- Blue -> Green ----
                float k = (float)(i - segment) / segment;
                int r = 0;
                int g = (int)(255 * k);
                int b = (int)(255 * (1f - k));
                colorMap[i] = new Color(r, g, b).getRGB();

            } else {
                // ---- Green -> Red ----
                float k = (float)(i - 2 * segment) / (maxIndex - 2 * segment);
                int r = (int)(255 * k);
                int g = (int)(255 * (1f - k));
                int b = 0;
                colorMap[i] = new Color(r, g, b).getRGB();
            }
        }

//        System.out.println("Color map generated: Black → Blue → Green → Red (equal segments)");
    }


    // ---- utility to keep values in safe 0-255 range ----
    private int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    // ======================= SETTINGS =======================
    public void setOffsets(int left, int right) {
        leftOffset = Math.max(0, left);
        rightOffset = Math.max(0, right);
    }
    
    public synchronized void feedData(float[][] newSpectrumRow) {
        if (newSpectrumRow == null || newSpectrumRow.length == 0 || newSpectrumRow[0].length == 0)
            return;

        int visibleWidth = Math.max(0, waterfallWidth - leftOffset - rightOffset);
        int centerOffset = (waterfallWidth - visibleWidth) / 2;

        System.out.println("feedData called");
        System.out.println("waterfallWidth=" + waterfallWidth + " height=" + waterfallHeight);
     // prints ALL values of first row
        System.out.println("row 0 = " +
                Arrays.toString(newSpectrumRow[0]));
        System.out.println("row last = " +
                Arrays.toString(newSpectrumRow[newSpectrumRow.length - 1]));


        System.out.println("pixelBuffer len=" + pixelBuffer.length);

        // --- iterate over waterfall height and width ---
        for (int y = 0; y < waterfallHeight; y++) {
            int rowOffset = y * waterfallWidth;

            for (int x = 0; x < waterfallWidth; x++) {

                int sourceX = x - centerOffset + leftOffset;

                if (sourceX >= 0 && sourceX < newSpectrumRow[0].length) {

                    // ❗ newSpectrumRow is expected to be of size [1][width]
                    // but your call feedData(row) passed row: float[height][width]
                    // so many 'y' rows have default 0 → black output
                    float value = newSpectrumRow[y][sourceX];

                    // clamp
                    value = Math.max(Resources.waterfall_lowerlimit,
                            Math.min(Resources.waterfall_upperlimit, value));

                    int norm = normalize(value);     // (-200..0) → 0..200
                    if (norm < 0) norm = 0;
                    if (norm > 255) norm = 255;

                    // ❗ FIX: write to actual pixel position
                    pixelBuffer[rowOffset + x] = colorMap[norm];

                } else {
                    pixelBuffer[rowOffset + x] = colorMap[0];
                }
            }
        }

        waterfallImage.setRGB(0, 0, waterfallWidth, waterfallHeight, pixelBuffer, 0, waterfallWidth);
        repaint();
    }

    // ============ INTERPOLATE SAME LOGIC AS ANDROID ============
    private int interpolate(float value) {
        float oldMin = Resources.waterfall_lowerlimit;
        float oldMax = Resources.waterfall_upperlimit;
        int newMin = 0, newMax = 255;

        value = Math.max(oldMin, Math.min(oldMax, value));
        return (int) ((value - oldMin) / (oldMax - oldMin) * (newMax - newMin) + newMin);
    }
    
    // ========================= CLEAR =========================
    public synchronized void clear() {
        if (pixelBuffer != null) {
            for (int i = 0; i < pixelBuffer.length; i++) {
                pixelBuffer[i] = Color.BLACK.getRGB();
            }
        }

        if (waterfallImage != null) {
            waterfallImage.setRGB(0, 0, waterfallWidth, waterfallHeight,
                                  pixelBuffer, 0, waterfallWidth);
        }

        repaint();
    }
    
    /**
     * Insert ONE phase frame (degrees) into waterfall.
     * phaseDeg length must match FFT bins.
     */
    public synchronized void updatePhaseSpectrum(double[] phaseDeg) {

        if (phaseDeg == null || phaseDeg.length == 0) return;
        if (waterfallWidth <= 0 || waterfallHeight <= 0) return;

        // lazy init
        if (waterfallImage == null || pixelBuffer == null ||
            pixelBuffer.length != waterfallWidth * waterfallHeight) {
            init(getWidth(), getHeight());
            clear();
        }
        
        //System.out.println("ARRAY Data: "+Arrays.toString(phaseDeg));

        int[] rowPixels = new int[waterfallWidth];

        int step = Math.max(1, phaseDeg.length / waterfallWidth);
        //double phaseMaskThreshold = -45.0; // dB

        // ---- downsample (average phase carefully) ----
        for (int x = 0; x < waterfallWidth; x++) {

            int start = x * step;
            int end   = Math.min(start + step, phaseDeg.length);

            double sumSin = 0;
            double sumCos = 0;
            int count = 0;

            for (int i = start; i < end; i++) {

                // ---- MASK NOISE ----
//                if (magDb[i] < phaseMaskThreshold)
//                    continue;
            	
                double rad = Math.toRadians(phaseDeg[i]);
                sumSin += Math.sin(rad);
                sumCos += Math.cos(rad);
                count++;
                
            }
            
            if (count == 0) {
                // No valid signal → BLACK
                rowPixels[x] = Color.BLACK.getRGB();
                continue;
            }
            
            double avgPhaseRad = Math.atan2(sumSin, sumCos);
            double avgPhaseDeg = Math.toDegrees(avgPhaseRad);

            rowPixels[x] = phaseDegToColor(avgPhaseDeg);
            
        }

        // ---- shift old rows DOWN ----
        for (int y = waterfallHeight - 1; y > 0; y--) {
            int src = (y - 1) * waterfallWidth;
            int dst = y * waterfallWidth;
            System.arraycopy(pixelBuffer, src, pixelBuffer, dst, waterfallWidth);
        }

        // ---- insert new row at TOP ----
        System.arraycopy(rowPixels, 0, pixelBuffer, 0, waterfallWidth);

        waterfallImage.setRGB(
                0, 0,
                waterfallWidth, waterfallHeight,
                pixelBuffer, 0, waterfallWidth
        );

        repaint();
        
    }
    
    
    /**
     * Called when a full sweep (stitched spectrum) is ready.
     * Converts 1D finalAmp[] into a 2D waterfall row and feeds it.
     */
    
    public synchronized void updateSpectrum(double[] freq, double[] amp) {
        if (amp == null || amp.length == 0) return;
        if (waterfallWidth <= 0 || waterfallHeight <= 0) return;

        if (waterfallImage == null || pixelBuffer == null ||
            pixelBuffer.length != waterfallWidth * waterfallHeight) {
            init(getWidth(), getHeight());
            clear();
        }

        int[] rowPixels = new int[waterfallWidth];
        int step = Math.max(1, amp.length / waterfallWidth);

        // ---- downsample using max pooling ----
        for (int x = 0; x < waterfallWidth; x++) {
        	
            int start = x * step;
            int end   = Math.min(start + step, amp.length);

            float maxAmp = -9999;
            for (int i = start; i < end; i++) {
                if (amp[i] > maxAmp) maxAmp = (float) amp[i];
            }

            int norm = interpolate(maxAmp);
//            System.out.println("norm=" + norm);
            if (norm < 0) norm = 0;
            if (norm > 200) norm = 200;

            rowPixels[x] = colorMap[norm]; 
            
        }

        // ---- shift rows down ----
        for (int y = waterfallHeight - 1; y > 0; y--) {
            int src = (y - 1) * waterfallWidth;
            int dst = y * waterfallWidth;
            System.arraycopy(pixelBuffer, src, pixelBuffer, dst, waterfallWidth);
        }

        // ---- insert new row at top ----
        System.arraycopy(rowPixels, 0, pixelBuffer, 0, waterfallWidth);

        waterfallImage.setRGB(0, 0, waterfallWidth, waterfallHeight,
                pixelBuffer, 0, waterfallWidth);

        repaint();
    }

    int normalize(float v) {
        return (int) (v + 200);  // since range size is 200
    }
    
    
    /**
     * Generates a random spectrum sweep (-200..0) and inserts ONE new row
     * into the waterfall each time it is called.
     */
    public synchronized void pushRandomRow() {

        // --- wait until panel is actually sized ---
        if (waterfallWidth <= 0 || waterfallHeight <= 0 ||
            getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        // --- lazy init ---
        if (waterfallImage == null || pixelBuffer == null ||
            pixelBuffer.length != getWidth() * getHeight()) {
            init(getWidth(), getHeight());
            clear();
        }

        // ----- shift old rows DOWN -----
        for (int y = waterfallHeight - 1; y > 0; y--) {
            int src = (y - 1) * waterfallWidth;
            int dst = y * waterfallWidth;
            System.arraycopy(pixelBuffer, src, pixelBuffer, dst, waterfallWidth);
        }

        // ----- generate one random row -----
        for (int x = 0; x < waterfallWidth; x++) {
            float v = -200f + (float)(Math.random() * 200.0); // -200..0
            int norm = normalize(v);
            if (norm < 0) norm = 0;
            if (norm > 255) norm = 255;
            pixelBuffer[x] = colorMap[norm]; // <-- row inserted at TOP (index 0)
        }

        waterfallImage.setRGB(0, 0,
                waterfallWidth, waterfallHeight, pixelBuffer, 0, waterfallWidth);

        repaint();
    }


public void setTimeScale(float totalTimeSec, int steps) {
    this.totalTimeSec = totalTimeSec;
    this.steps = Math.max(1, steps);
    repaint();
}


    // ===================== RENDERING ========================
@Override
protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(Color.WHITE);

    int panelW = getWidth();
    int panelH = getHeight();

    int drawWidth  = panelW - leftOffset - rightOffset;
    int drawHeight = panelH - topOffset - bottomOffset;

    //---------------------- DRAW WATERFALL IMAGE -------------------------
    if (waterfallImage != null) {
        g2.drawImage(
            waterfallImage,
            leftOffset, topOffset,
            leftOffset + drawWidth, topOffset + drawHeight,
            0, 0,
            waterfallWidth, waterfallHeight,
            null
        );
    }

    //---------------------- DRAW LEFT TIME SCALE -------------------------
    // area inside leftOffset margin
    int scaleX = 5;  // 5px padding from left border
    int scaleTop = topOffset;
    int scaleHeight = drawHeight;

    g2.setFont(new Font("Arial", Font.PLAIN, 14));

    for (int i = 0; i <= steps; i++) {

        // y position of division
        float frac = (float) i / steps;
        int y = scaleTop + (int)(frac * scaleHeight);

        // corresponding time (0 at top, totalTimeSec at bottom)
        float timeVal = frac * totalTimeSec;
        String label = "";
        if(timeVal==0) {
            label = String.format("%.1fs", timeVal);
        }else
        {
        	label = String.format("%.2f", timeVal);
        }

        // Draw major tick
        g2.drawLine(leftOffset - 8, y, leftOffset - 2, y);

        // Draw label
        g2.drawString(label, scaleX, y + 5);
    }

    g2.dispose();
}

}
