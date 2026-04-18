package com.trs;

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.awt.TextRenderer;

import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Arrays;

public class SpectrumGLPanel extends GLCanvas implements GLEventListener {

    private double[] freq;
    private double[] amp;

    private boolean maxHoldEnabled = false;
    private double[] maxHoldTrace = null;


    private double refLevel = 50;
    static double staticrefLevel = 10;
    private double dBPerDiv = 10;
    private int numDiv = 10;

    private double minAmp, maxAmp;
    private double minFreq = 0;
    private double maxFreq = 1;

    private TextRenderer renderer;

    // margins used for trace drawing
    private final float leftMargin = -0.9f;
    private final float rightMargin = 0.9f;
    private final float bottomMargin = -0.85f;
    private final float topMargin = 0.81f;

    // ------------------------
    // MARKERS
    // ------------------------
    private static class Marker {
        int index;
        boolean isDelta;
        int refIndex = -1;
    }

    private Marker[] markers = new Marker[3];
    private int markerCount = 0;
    private int activeMarker = -1;
    private int draggedMarker = -1;

    public SpectrumGLPanel(GLCapabilities caps) {
        super(caps);
        updateAmplitudeWindow();
        addGLEventListener(this);

        addMouseWheelListener(e -> shiftRefLevel(e.getWheelRotation() * 10));
        enableMarkerDrag();
    }

    private void updateAmplitudeWindow() {
        maxAmp = refLevel;
        minAmp = refLevel - dBPerDiv * numDiv;
    }
    
    void setreflevel(int level) {
        refLevel = staticrefLevel - (double) level;
        updateAmplitudeWindow();
        repaint();
    }
    

    public void shiftRefLevel(double delta) {
        this.refLevel += delta;
        updateAmplitudeWindow();
        repaint();
    }

    @Override public void init(GLAutoDrawable d) {
        d.getGL().getGL2().glClearColor(0, 0, 0, 1);
        renderer = new TextRenderer(new Font("Arial", Font.PLAIN, 14));
    }
    @Override public void dispose(GLAutoDrawable d) {}

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        GL2 gl = d.getGL().getGL2();
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrtho(-1, 1, -1, 1, -1, 1);
        gl.glViewport(0, 0, w, h);
    }

    private int frameCounter = 0;
    @Override
    public void display(GLAutoDrawable d) {
    	
    	long start = System.nanoTime();
        GL2 gl = d.getGL().getGL2();
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);

        drawGrid(gl);
        drawAxes(gl);
        drawLabels(gl);

        if (freq != null && amp != null) {
            drawTrace(gl, getDisplayedTrace(), 1f, 0.75f, 0f);
            drawMarkers(gl);
            drawMarkerInfo();
            peakSearch();
        }
        long now = System.nanoTime();
        long deltaNs = now - start;
        double deltaMs = deltaNs / 1_000_000.0;
//        gl.glFinish();  // force GPU sync
//        if (++frameCounter % 30 == 0) {
//            System.out.printf("Plot time = %.2f ms%n",
//                    (now - start) / 1_000_000.0);
//        }
    }

    // ---------------------------------
    // FIXED MARKER → PIXEL CONVERSION
    // ---------------------------------
    private int glToPixelX(float glX, int width) {
        return (int)((glX + 1f) * 0.5f * width);
    }

    private int glToPixelY(float glY, int height) {
        return (int)((glY + 1f) * 0.5f * height);
    }

    // ---------------------------------
    // FIXED PIXEL → GL X (dragging)
    // ---------------------------------
    private float pixelToGLX(int pixelX, int width) {
        return (pixelX / (float) width) * 2f - 1f;
    }

    // ------------------------
    // UPDATE SPECTRUM
    // ------------------------
    public void updateSpectrum(double[] f, double[] a) {
        this.freq = f;
        this.amp = a;

        minFreq = f[0];
        maxFreq = f[f.length - 1];

        if (maxHoldEnabled) {
            if (maxHoldTrace == null || maxHoldTrace.length != a.length)
                maxHoldTrace = Arrays.copyOf(a, a.length);
            else
                for (int i = 0; i < a.length; i++)
                    if (a[i] > maxHoldTrace[i])
                        maxHoldTrace[i] = a[i];
        }

        repaint();
    }

    private double[] getDisplayedTrace() {
        return (maxHoldEnabled && maxHoldTrace != null) ? maxHoldTrace : amp;
    }

    // ------------------------
    // MARKER FUNCTIONS
    // ------------------------
    public void addMarker() {
        if (markerCount >= 3 || freq == null) return;
        Marker m = new Marker();
        m.index = findPeakIndex();
        markers[markerCount] = m;
        activeMarker = markerCount++;
        repaint();
    }

    public void setDeltaMode(boolean enabled) {
        if (activeMarker < 0 || markerCount < 2) return;
        Marker m = markers[activeMarker];
        m.isDelta = enabled;
        m.refIndex = enabled ? markers[0].index : -1;
        repaint();
    }

    private int findPeakIndex() {
        double[] trace = getDisplayedTrace();
        int idx = 0;
        for (int i = 1; i < trace.length; i++)
            if (trace[i] > trace[idx])
                idx = i;
        return idx;
    }

    private double deltaFreq(int i, int ri) {
        return freq[i] - freq[ri];
    }

    private double deltaAmp(int i, int ri) {
        double[] t = getDisplayedTrace();
        return t[i] - t[ri];
    }

    // ------------------------
    // DRAW MARKERS (FIXED)
    // ------------------------
    private void drawMarkers(GL2 gl) {
        if (markerCount == 0 || freq == null) return;

        int w = getWidth();
        int h = getHeight();

        renderer.beginRendering(w, h);
        float arrowH = 0.03f;
        float arrowW = 0.03f;

        double[] trace = getDisplayedTrace();
        if (trace == null) {
            renderer.endRendering();
            return;
        }

        for (int m = 0; m < markerCount; m++) {
            Marker mk = markers[m];
            if (mk == null) continue;

            // -------------------------
            // *** FIX: reset if invalid ***
            if (mk.index < 0 || mk.index >= freq.length) {
                mk.index = 0;      // <-- your requested behavior
            }
            // -------------------------

            int idx = mk.index;

            // now safe to use idx
            float glX = leftMargin + (float)((freq[idx] - minFreq) / (maxFreq - minFreq))
                    * (rightMargin - leftMargin);

            float glY = bottomMargin + (float)((trace[idx] - minAmp) / (maxAmp - minAmp))
                    * (topMargin - bottomMargin);

            // draw triangle marker
            gl.glColor3f(1, 1, 0);
            gl.glBegin(GL2.GL_TRIANGLES);
            gl.glVertex2f(glX, glY + arrowH);
            gl.glVertex2f(glX - arrowW, glY);
            gl.glVertex2f(glX + arrowW, glY);
            gl.glEnd();

            int px = glToPixelX(glX, w);
            int py = glToPixelY(glY + arrowH + 0.02f, h);
//
//            renderer.draw("M" + (m + 1), px - 6, py);


        }

        renderer.endRendering();
    }

    // ------------------------
    // HIT TEST (FIXED)
    // ------------------------
    private int findClosestMarker(int mouseX, int mouseY) {

        if (markerCount == 0) {
            System.out.println("❌ No markers available");
            return -1;
        }

        int w = getWidth();
        int h = getHeight();

        int closest = -1;
        double bestDist = Double.MAX_VALUE;

        for (int m = 0; m < markerCount; m++) {

            Marker mk = markers[m];
            int idx = mk.index;
            double[] trace = getDisplayedTrace();

            // Convert marker GL → pixel coords
            float glX = leftMargin + (float)((freq[idx] - minFreq) / (maxFreq - minFreq)) * (rightMargin - leftMargin);
            float glY = bottomMargin + (float)((trace[idx] - minAmp) / (maxAmp - minAmp)) * (topMargin - bottomMargin);

            int px = (int)((glX + 1f) * 0.5f * w);
            int py = (int)((glY + 1f) * 0.5f * h);

            System.out.println("Marker " + m + " pixelPos = " + px + ", " + py);

            double dist = Math.hypot(mouseX - px, mouseY - py);

            if (dist < 25 && dist < bestDist) {
                bestDist = dist;
                closest = m;
            }
        }

        System.out.println("Clicked = Marker " + closest);
        return closest;
    }

    
//    private void moveMarkerToMouse(MouseEvent e) {
//        int w = getWidth();
//
//        // Convert pixel → GL
//        float glX = (e.getX() / (float) w) * 2f - 1f;
//
//        // Clamp inside graph margins
//        if (glX < leftMargin) glX = leftMargin;
//        if (glX > rightMargin) glX = rightMargin;
//
//        // Convert GL → frequency
//        float frac = (glX - leftMargin) / (rightMargin - leftMargin);
//        double newFreq = minFreq + frac * (maxFreq - maxFreq);
//
//        // Find nearest bin
//        int idx = 0;
//        for (int i = 1; i < freq.length; i++) {
//            if (Math.abs(freq[i] - newFreq) < Math.abs(freq[idx] - newFreq)) {
//                idx = i;
//            }
//        }
//
//        markers[draggedMarker].index = idx;
//        repaint();
//    }

    
    // ------------------------
    // DRAGGING (FIXED)
    // ------------------------
    private void enableMarkerDrag() {
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                draggedMarker = findClosestMarker(e.getX(), e.getY());
            }
            @Override public void mouseReleased(MouseEvent e) {
                draggedMarker = -1;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (draggedMarker < 0) return;

                float glX = pixelToGLX(e.getX(), getWidth());

                float frac = (glX - leftMargin) / (rightMargin - leftMargin);
                double newFreq = minFreq + frac * (maxFreq - minFreq);

                int nearest = 0;
                for (int i = 1; i < freq.length; i++)
                    if (Math.abs(freq[i] - newFreq) < Math.abs(freq[nearest] - newFreq))
                        nearest = i;

                markers[draggedMarker].index = nearest;
                repaint();
            }
        });
    }

    // ------------------------
    // GRID, AXES, LABELS
    // (unchanged)
    // ------------------------
    // ------------------------
    // DRAW FUNCTIONS
    // ------------------------
    private void drawGrid(GL2 gl) {
        gl.glColor3f(0.25f, 0.25f, 0.25f);
        gl.glBegin(GL2.GL_LINES);

        for (int i = 0; i <= numDiv; i++) {
            double dB = maxAmp - i * dBPerDiv;
            float y = bottomMargin + (float)((dB - minAmp) / (maxAmp - minAmp) * (topMargin - bottomMargin));
            gl.glVertex2f(leftMargin, y);
            gl.glVertex2f(rightMargin, y);
        }

        double step = (maxFreq - minFreq) / 10;
        for (double f = minFreq; f <= maxFreq; f += step) {
            float x = leftMargin + (float)((f - minFreq) / (maxFreq - minFreq) * (rightMargin - leftMargin));
            gl.glVertex2f(x, bottomMargin);
            gl.glVertex2f(x, topMargin);
        }

        gl.glEnd();
    }

    private void drawAxes(GL2 gl) {
        gl.glColor3f(1, 1, 1);
        gl.glBegin(GL2.GL_LINES);

        gl.glVertex2f(leftMargin, bottomMargin);
        gl.glVertex2f(leftMargin, topMargin);

        gl.glVertex2f(leftMargin, bottomMargin);
        gl.glVertex2f(rightMargin, bottomMargin);

        gl.glEnd();
    }

    private void drawTrace(GL2 gl, double[] yVals, float r, float g, float b) {
        gl.glColor3f(r, g, b);
        gl.glBegin(GL2.GL_LINE_STRIP);

        for (int i = 0; i < freq.length; i++) {
            float x = leftMargin + (float)((freq[i] - minFreq) / (maxFreq - minFreq) * (rightMargin - leftMargin));
            float y = bottomMargin + (float)((yVals[i] - minAmp) / (maxAmp - minAmp) * (topMargin - bottomMargin));
            if (yVals[i] < minAmp) {
                gl.glEnd();
                gl.glBegin(GL2.GL_LINE_STRIP);
                continue;
            }

            gl.glVertex2f(x, y);
        }

        gl.glEnd();
    }
    
 // ------------------------
    private void drawLabels(GL2 gl) {
        if (renderer == null) return;

        int w = getWidth();
        int h = getHeight();
        renderer.beginRendering(w, h);
        renderer.setColor(1, 1, 1, 1);

//        // Amplitude labels
        for (int i = 0; i <= numDiv; i++) {
            double dB = maxAmp - i * dBPerDiv;
            float y = bottomMargin + (float)((dB - minAmp) / (maxAmp - minAmp) * (topMargin - bottomMargin));
            int py = (int)((y + 1) * h / 2);
            if(i==0) {
            	Resources.waterfall_upperlimit = (int) dB;
            }
            if (i==numDiv){
            	Resources.waterfall_lowerlimit = (int) dB;
            }
            
            if(dB == 0) {
            	renderer.draw(String.format("%.0f dBm", dB), 5, py);
            }else {
            	renderer.draw(String.format("%.0f", dB), 5, py);
            }
            
        }
//     // ---- Amplitude labels (existing) ----
//        for (int i = 0; i <= numDiv; i++) {
//            double dB = maxAmp - i * dBPerDiv;
//            float y = bottomMargin + (float)((dB - minAmp) / (maxAmp - minAmp) *
//                    (topMargin - bottomMargin));
//            int py = (int)((y + 1) * h / 2);
//
//            if (dB == 0)
//                renderer.draw(String.format("%.0f dBm", dB), 5, py);
//            else
//                renderer.draw(String.format("%.0f", dB), 5, py);
//        }
//
//        // ---- Rotated amplitude axis label ----
//        String ampLabel = "Amplitude (dBm)";
//        int textWidth = (int) renderer.getBounds(ampLabel).getWidth();
//
//        gl.glPushMatrix();
//        gl.glTranslatef(20f, h / 2f, 0f);   // move to left + center vertically
//        gl.glRotatef(-90f, 0f, 0f, 1f);     // rotate CCW
//        renderer.draw(ampLabel, 0 / 2, 0);
//        gl.glPopMatrix();


        FreqUnit unit = getFreqUnit();
        
        // Frequency labels
        double step = (maxFreq - minFreq) / 10;
        for (double f = minFreq; f <= maxFreq; f += step) {
            float x = leftMargin + (float)((f - minFreq) / (maxFreq - minFreq) * (rightMargin - leftMargin));
            int px = (int)((x + 1) * w / 2);

            String line1 = String.format("%.3f", f / unit.scale); // frequency value
            String line2 = unit.label;                             // MHz / GHz
            // draw first line
            renderer.draw(line1, px - 20, 14);
        }
     // ---- draw the unit ONLY ONCE in the center ----
        double midFreq = (minFreq + maxFreq) / 2.0;
        float glMidX = leftMargin + (float)((midFreq - minFreq) / (maxFreq - minFreq)) *
                (rightMargin - leftMargin);

        int pxMid = (int)((glMidX + 1f) * w / 2f);
        
        String line2 = "Frequency (" + unit.label+")";     

        renderer.draw(line2, pxMid - 50, 2);

        renderer.endRendering();
    }
    
    
    // ------------------------
    // MARKER FUNCTIONS
    // ------------------------
  
//
    public void removeMarker() {
        if (markerCount == 0) return;

        markerCount--;
        markers[markerCount] = null;

        activeMarker = markerCount - 1;
        repaint();
    }
    
    public void clearMarkers() {
        Arrays.fill(markers, null);
        markerCount = 0;
        activeMarker = -1;
        repaint();
    }


    public void peakSearch() {
        if (freq == null) return;

        if (markerCount == 0) {
            addMarker();
            return;
        }

        if (activeMarker < 0) activeMarker = 0;
        markers[activeMarker].index = findPeakIndex();
        repaint();
    }
    
    // MAX HOLD
    // ------------------------
    public void setMaxHold(boolean enabled) {
        this.maxHoldEnabled = enabled;
        if (!enabled) maxHoldTrace = null;
        repaint();
    }
    
    
    private void drawMarkerInfo() {
        if (renderer == null || activeMarker < 0 || freq == null) return;

        Marker m = markers[activeMarker];
        int idx = m.index;

        double f = freq[idx];
        double a = getDisplayedTrace()[idx];

        int w = getWidth();
        int h = getHeight();

        renderer.beginRendering(w, h);

        // Optional scaling if needed
//         renderer.getTransform().setToScale(1.3, 1.3);

        renderer.setColor(1, 1, 0, 1); // yellow text

        FreqUnit unit = getFreqUnit();

        String line1 = String.format("M%d  %.6f %s",
            activeMarker + 1, f / unit.scale, unit.label);
        String line2 = String.format("%.2f dBm", a);

        renderer.draw(line1, w - 220, h - 80);
        renderer.draw(line2, w - 220, h - 100);

        renderer.endRendering();
    }

    
//    private void drawMarkerInfo() {
//        if (renderer == null || activeMarker < 0 || freq == null) return;
//
//        Marker m = markers[activeMarker];
//        int idx = m.index;
//
//        double f = freq[idx];
//        double a = getDisplayedTrace()[idx];
//
//        int w = getWidth();
//        int h = getHeight();
//
//        renderer.beginRendering(w, h);
//        renderer.setColor(1, 1, 0, 1); // yellow text
//
//        FreqUnit unit = getFreqUnit();
//
//        String line1 = String.format(
//            "M%d  %.6f %s",
//            activeMarker + 1,
//            f / unit.scale,
//            unit.label
//        );
//        String line2 = "";
//        
////        if(a==0) {
//        	line2 = String.format("%.2f dBm", a);
////        }else {
////        	line2 = String.format("%.2f", a);
////        }
//        
//        
//        
//
//        // TOP-RIGHT CORNER
//        renderer.draw(line1, w - 220, h - 50);
//        renderer.draw(line2, w - 220, h - 70);
//
//        renderer.endRendering();
//    }

    private enum FreqUnit {
        MHz(1e6, "MHz"),
        GHz(1e9, "GHz");

        final double scale;
        final String label;

        FreqUnit(double scale, String label) {
            this.scale = scale;
            this.label = label;
        }
    }

    private FreqUnit getFreqUnit() {
        return (maxFreq >= 1e9) ? FreqUnit.GHz : FreqUnit.MHz;
    }


}
