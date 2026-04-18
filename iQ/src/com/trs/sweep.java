package com.trs;

import com.jogamp.opengl.*;
import org.apache.commons.math3.complex.Complex;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.trs.DeviceHandlers.multiUsbManager;

import jcuda.CudaException;
import jcuda.Pointer;
import static jcuda.jcufft.JCufft.*;
import static jcuda.runtime.JCuda.*;
import static jcuda.runtime.cudaMemcpyKind.*;


import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.*;

import org.apache.commons.lang3.ArrayUtils;

import jcuda.jcufft.cufftHandle;
import jcuda.jcufft.cufftType;
import jcuda.runtime.JCuda;
import jcuda.runtime.cudaDeviceProp;
import jcuda.runtime.cudaEvent_t;
class DeviceSweepContext {

    ScheduledExecutorService scheduler;
    ScheduledFuture<?> future;

    ExecutorService fftExecutor = Executors.newSingleThreadExecutor();
    ExecutorService dataHandler = Executors.newSingleThreadExecutor();

    LooperThread uiLooper = new LooperThread();

    volatile boolean sweepFlag = false;
    final Object lock = new Object();

    int uiIndex;

    // 🔹 timing
    volatile long frameStartTimeNs = 0;
    volatile long lastFrameTimeMs = 0;
    
    long segStartFreq;
    long segEndFreq;
}

public class sweep implements multiUsbManager.UsbHelperListener{

	
	int MAX_CYCLES = 100;

	List<float[]> iqCycles = new ArrayList<>();
	List<double[]> phaseCycles = new ArrayList<>();
	
    JFrame frmTrs;

    JButton startBtn;
    JComboBox<String> fftSize;
    JComboBox<String> deviceList;
    private multiUsbManager usbHelper;
	int iteration_count = 100;
	int batch_size = 1;
	int time_threshold = 36000;
	
	int no_itr = 0;

	int avg_time = 0;
	int frame_warmup_skip = 3;
	int actual_frames = 0;
     int gbl_frequency, gbl_start_frequency, gbl_stop_frequency, gbl_bandwidth, gbl_sweep_point, gbl_gain, gbl_waterfallstep;

	 private volatile long lastReceiveTime = -1;
	 
	 private final List<SpectrumGLPanel> spectrumPanels = new ArrayList<>();
	 private final List<WaterfallPanel> waterfallPanels = new ArrayList<>();
	 
	// Keeps actual USB keys sorted numerically
	 private final List<String> sortedDeviceKeys = new ArrayList<>();

	 // Maps UI index -> actual key
	 private final List<String> indexToKey = new ArrayList<>();
	// Maps USB key -> UI index
	 private final Map<String, Integer> keyToIndexMap = new LinkedHashMap<>();

	 private final Map<String, DeviceSweepContext> deviceSweepMap = new ConcurrentHashMap<>();
	 
	 JTextField startFreq, stopFreq, bandwidth, gain, waterfallstep;
	 JLabel fpsLabel;
	 
	 // One stitched spectrum for panel 0
	    private volatile StitchBuffer stitchBuffer;

	 volatile boolean runningStatus = false;
    public static void main(String[] args) {
    	EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					sweep window = new sweep();
					window.frmTrs.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
    }

    public sweep() {
    	
        initialize();
        setListerns();
        
        usbHelper = new multiUsbManager(this);
        
		new Thread(usbHelper).start();
		System.out.println("started");
		
		  // Enable exceptions to make errors more obvious
//        JCuda.setExceptionsEnabled(true);
//
//        int[] deviceCount = new int[1];
//        try {
//            // Get the number of CUDA-capable devices
//            cudaGetDeviceCount(deviceCount);
//        } catch (CudaException e) {
//            System.err.println("Could not find any CUDA devices or an error occurred: " + e.getMessage());
//            return;
//        }
//
//        int nDevices = deviceCount[0];
//        System.out.println("Number of CUDA devices found: " + nDevices);
//
//        if (nDevices == 0) {
//            return;
//        }
//
//        for (int i = 0; i < nDevices; i++) {
//            cudaDeviceProp properties = new cudaDeviceProp();
//            // Get properties for the current device
//            cudaGetDeviceProperties(properties, i);
//
//            System.out.println("\n--- Device Number " + i + " ---");
//            System.out.println("  Device name: " + properties.name);
//            System.out.printf("  Total global memory: %.1f GBytes\n", (float) properties.totalGlobalMem / 1024.0 / 1024.0 / 1024.0);
//            System.out.println("  Compute capability: " + properties.major + "." + properties.minor);
//            System.out.printf("  Memory Clock Rate (MHz): %d\n", properties.memoryClockRate / 1024);
//        }
		
		
    }

    //SpectrumPhaseGLPanel spectrum_phase;
    XYSeries phase_series;
    XYPlot phase_plot;
    XYSeriesCollection phase_dataset;
    List<double[]> phase_max_amp_data = new ArrayList<double[]>();
    
    // ======== CONFIGURABLE PARAMETERS ========
    private static int TOTAL_BATCHES = 4096; // default value // 
    private static int SAMPLE_SHIFT= 2; // default value

    // ======== COLOR VARIABLES (CHANGE HERE ANYTIME) ========
    private static Color COLOR_DEFAULT = Color.WHITE;
    private static Color COLOR_RUNNING = Color.GREEN;
    private static Color COLOR_COMPLETED = Color.BLUE;
    
    private JFrame frame;
    private JPanel gridPanel;
    private JPanel[] cells;
    
    private void initialize() {

        frmTrs = new JFrame("Spectrum Analyzer");
        frmTrs.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frmTrs.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frmTrs.setLayout(new BorderLayout());

        int panelCount = 1;
        
        phase_series = new XYSeries("Phase Data");
        

        phase_dataset = new XYSeriesCollection();
        phase_dataset.addSeries(phase_series);

        // ---------- Spectrum + Waterfall Stack (CENTER) ----------
        JPanel parentPanel = new JPanel(new GridLayout(1, 0, 2, 2));
        parentPanel.setBackground(Color.BLACK);

        for (int i = 0; i < panelCount; i++) {

            JPanel holder = new JPanel(new BorderLayout());
            holder.setBackground(Color.BLACK);

            JPanel vbox = new JPanel(new GridLayout(2, 1));
            vbox.setBackground(Color.BLACK);

         // ---------------------------------------------------------
         // TOP: Spectrum + FPS label + Right placeholder
         // ---------------------------------------------------------
         SpectrumGLPanel spectrum = createSpectrumPanel();
         //spectrum_phase = createPhaseSpectrumPanel();
         spectrumPanels.add(spectrum);
         //spectrumPanels.add(spectrum_phase);
         
         // Create chart
         // Create chart
         JFreeChart chart = ChartFactory.createXYLineChart(
                 "PHASE DATA",
                 "Sample",
                 "Degree",
                 phase_dataset,
                 PlotOrientation.VERTICAL,
                 true,   // legend
                 true,   // tooltips
                 false   // URLs
         );
         
         phase_plot = chart.getXYPlot();
         
         DegreeColorRenderer renderer = new DegreeColorRenderer();
         renderer.setSeriesLinesVisible(0, false);
         renderer.setSeriesShapesVisible(0, true);

         //phase_plot.setRenderer(renderer);
         
         // Set Y-axis range -180 to 180
         NumberAxis yAxis = (NumberAxis) phase_plot.getRangeAxis();
         yAxis.setRange(-180, 180);

 
         ChartPanel chartPanel = new ChartPanel(chart);

         JPanel spectrumContainer = new JPanel(new BorderLayout(1,2));
         spectrumContainer.setBackground(Color.BLACK);

         JPanel spectraPanel = new JPanel(new GridLayout(1, 2));
         spectraPanel.add(spectrum);
           
//         int cols = (int) Math.ceil(Math.sqrt(TOTAL_BATCHES));
//         gridPanel = new JPanel(new GridLayout(0, cols, 1, 1));
//         cells = new JPanel[TOTAL_BATCHES];
//
//         for (int j = 0; j< TOTAL_BATCHES; j++) {
//             JPanel cell = new JPanel();
//             cell.setBackground(COLOR_DEFAULT);
//             cell.setBorder(BorderFactory.createLineBorder(Color.GRAY));
//             cells[j] = cell;
//             gridPanel.add(cell);
//         }
         
         //spectraPanel.add(gridPanel);
         
         //spectraPanel.add(chartPanel);
         
         // === FPS LABEL ===
         fpsLabel = new JLabel("FPS: 0");
         fpsLabel.setForeground(Color.GREEN);
         fpsLabel.setFont(new Font("Arial", Font.PLAIN, 18));
         fpsLabel.setBorder(BorderFactory.createEmptyBorder(10, 80, 0, 0));
         fpsLabel.setBackground(Color.BLACK);
         fpsLabel.setOpaque(true);

         // put FPS label above the spectrum panel
         spectrumContainer.add(fpsLabel, BorderLayout.NORTH);

         // === SPECTRUM PANEL ===
         //spectrumContainer.add(spectrum, BorderLayout.EAST);
         
         spectrumContainer.add(spectraPanel, BorderLayout.CENTER);

         // === RIGHT SPACE PLACEHOLDER ===
         JPanel spectrumColorbarSpace = new JPanel();
         spectrumColorbarSpace.setPreferredSize(new Dimension(0, 0));
         spectrumColorbarSpace.setBackground(Color.BLACK);
         spectrumContainer.add(spectrumColorbarSpace, BorderLayout.EAST);

         vbox.add(spectrumContainer);


////         // ---------------------------------------------------------
////         // BOTTOM: Waterfall + RIGHT colorbar
////         // ---------------------------------------------------------
//         WaterfallPanel waterfall = new WaterfallPanel();
//         waterfallPanels.add(waterfall);
//
//         JPanel waterfallContainer = new JPanel(new BorderLayout());
//         waterfallContainer.setBackground(Color.BLACK);
//
//         // center = waterfall panel
//         waterfallContainer.add(waterfall, BorderLayout.CENTER);
//
//         // right = color-bar image
//         JLabel colorBarLabel = new JLabel(
//                 new ImageIcon(getClass().getResource("/images/spectrum_colorbar.jpg")));
//         colorBarLabel.setBackground(Color.BLACK);
//         colorBarLabel.setOpaque(true);
//         colorBarLabel.setPreferredSize(new Dimension(40, 0)); // fixed width (optional)
//
//         // add color bar on right side
//         waterfallContainer.add(colorBarLabel, BorderLayout.EAST);
//
//         vbox.add(waterfallContainer);
/////
      // ---------------------------------------------------------
      // BOTTOM: Waterfall + RIGHT colorbar (dynamic height)
      // ---------------------------------------------------------
      WaterfallPanel waterfall = new WaterfallPanel();
      waterfallPanels.add(waterfall);

      JPanel waterfallContainer = new JPanel(new BorderLayout());
      waterfallContainer.setBackground(Color.BLACK);

      // center = waterfall panel
      //waterfallContainer.add(waterfall, BorderLayout.CENTER);
      waterfallContainer.add(chartPanel, BorderLayout.CENTER);

      // --- dynamic colorbar ---
      ImageIcon baseIcon = new ImageIcon(getClass().getResource("/images/spectrum_colorbar.jpg"));
      JLabel colorBarLabel = new JLabel();
      colorBarLabel.setBackground(Color.BLACK);
      colorBarLabel.setOpaque(true);

      // fixed width, height will be auto-scaled
      int colorbarWidth = 70;
      colorBarLabel.setPreferredSize(new Dimension(colorbarWidth, 0));

      // resize icon whenever container height changes
      waterfallContainer.addComponentListener(new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
              int h = waterfallContainer.getHeight();  // match waterfall visible height
              if (h <= 0) return;

              Image scaledImg = baseIcon.getImage().getScaledInstance(
                      colorbarWidth,
                      h-60,
                      Image.SCALE_SMOOTH
              );

              colorBarLabel.setIcon(new ImageIcon(scaledImg));
          }
      });

      // add color bar on right side
      waterfallContainer.add(colorBarLabel, BorderLayout.EAST);

      // attach to UI
      vbox.add(waterfallContainer);

            holder.add(vbox, BorderLayout.CENTER);
            parentPanel.add(holder);
            
        }

        frmTrs.add(parentPanel, BorderLayout.CENTER);

        // ---------- Settings (RIGHT) ----------
        JPanel settingsPanel = createSettingsPanel();
        frmTrs.add(settingsPanel, BorderLayout.EAST);

        frmTrs.setVisible(true);
        //waitForWaterfallReady();

//        SwingUtilities.invokeLater(() -> {
//            for (WaterfallPanel w : waterfallPanels) {
//                w.init(w.getWidth(), w.getHeight());
//                w.clear();
//            }
//
//            // test generator
//            new Timer(60, e -> waterfallPanels.get(0).pushRandomRow()).start();
//        });


        frmTrs.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAllSweeps();
                System.out.println("window event");
            }
        });
    }

    public void setListerns() {
    	startBtn.addActionListener(e->sendSweep());
    }

    private SpectrumGLPanel createSpectrumPanel() {
        GLCapabilities caps =
                new GLCapabilities(GLProfile.get(GLProfile.GL2));
        caps.setSampleBuffers(true);
        caps.setNumSamples(8);

        return new SpectrumGLPanel(caps);
    }
    
    int op_counter = 0;
    private void startThreads(String key, DeviceSweepContext ctx) {
        for (int i = 0; i < globalArray.length / 2; i += SAMPLE_SHIFT) {
            final int index = i;  // 'index' can be used within the lambda

            Thread thread = new Thread(() -> {
                try {
                    if (runningStatus) {
                        long startop = System.currentTimeMillis();

                        processData(key, ctx, index);

                        long endop = System.currentTimeMillis();

                        System.out.println("Execution time: " + (endop - startop) + " ms");

                        op_counter++;

                        if (op_counter >= (globalArray.length / 4) - 2) {
                            runningStatus = false;

                            XYSeries phase_series_new = new XYSeries("Phase Data");

                            if (phase_dataset.getSeriesCount() > 0) {
                                phase_dataset.removeAllSeries();
                            }

                            long nonNegativeCount = Arrays.stream(phaseArray)
                                    .filter(element -> element != -1)
                                    .count();

                            for (int j = 0; j < nonNegativeCount; j++) {
                                phase_series_new.add(j + 1, phaseArray[j]);
                            }

                            if (phase_dataset.getSeriesCount() == 0) {
                                phase_dataset.addSeries(phase_series_new);

                                synchronized (this) {
                                    if (phaseCycles.size() < MAX_CYCLES) {
                                        // Extract phase data from phase_series_new and add to phaseCycles
                                        double[] phaseData = new double[phase_series_new.getItemCount()];

                                        for (int p = 0; p < phase_series_new.getItemCount(); p++) {  // Fix variable name from 'i' to 'p'
                                            phaseData[p] = phase_series_new.getY(p).doubleValue();  // Assuming getY() returns the phase value
                                        }

                                        phaseCycles.add(phaseData);  // Add phase data to phaseCycles
                                    }
                                }
                            }

                            Arrays.fill(phaseArray, -1);

                            offset_counter = 0;
                            op_counter = 0;

                            runningStatus = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            thread.start();
        }
    }
    
//    private void startThreads(String key, DeviceSweepContext ctx) {
//    	
//        for (int i = 0; i < globalArray.length/2; i+=SAMPLE_SHIFT) {
//        	
//            final int index = i;
//            
//
//            Thread thread = new Thread(() -> {
//                try {
//                    if(	runningStatus ) {
//                    	
//	                    // Mark as RUNNING (Green)
//	                    //updateColor(index/SAMPLE_SHIFT, COLOR_RUNNING);
//
//	    	        	long startop = System.currentTimeMillis();
//	    	        	
//	                    processData(key,ctx,index);
//	                      
//	    	           	long endop = System.currentTimeMillis();
//	    	            
//	    	            System.out.println("Execution time: " + (endop - startop) + " ms");
//	
//	                    // Mark as COMPLETED (Blue)
//	                    //updateColor(index/SAMPLE_SHIFT, COLOR_COMPLETED);
//	                    
//	                    op_counter++;
//	                    	                	
//	                    if(op_counter >= (globalArray.length/4)-2) {
//	                    	
//	                    	runningStatus = false;
//	        	        	
//	                    	// *******************
//	                    	// PHASE GRAPH DISPLAY
//	                    	// *******************
//	                    	
//	        	        	XYSeries phase_series_new = new XYSeries("Phase Data");
//	                    	
//	                    	if(phase_dataset.getSeriesCount() > 0) {
//	                    	
//	        	        		phase_dataset.removeAllSeries();
//	        	        	}
//
//	                    	long nonNegativeCount = Arrays.stream(phaseArray)
//	                      .filter(element -> element != -1)
//	                      .count();
//	        	        	
//	        	      		//phase_series_new.clear();
//	                    	
////	                    	double phase_array[] = new double[(int) nonNegativeCount];
//	        	          
//	        	        	for (int j = 0; j < nonNegativeCount; j++) {
//	        	        		 
//	        	        		phase_series_new.add(j+1, phaseArray[j]);
////	        	        		phase_array[j] = phaseArray[j];
//	        	            }
//
//	        	        	
//	        	        	if (phase_dataset.getSeriesCount() == 0) {
//	        	        	    phase_dataset.addSeries(phase_series_new);
//	        	        	    
//	        	        	    synchronized (this) {
//	        	        	        if (iqCycles.size() < MAX_CYCLES) {
//	        	        	            // Extract phase data from phase_series_new and add to phaseCycles
//	        	        	            double[] phaseData = new double[phase_series_new.getItemCount()];
//
//	        	        	            for (int p = 0; i < phase_series_new.getItemCount(); p++) {
//	        	        	                phaseData[p] = phase_series_new.getY(p).doubleValue();  // Assuming getY() returns the phase value
//	        	        	            }
//
//	        	        	            phaseCycles.add(phaseData);  // Add phase data to phaseCycles
//	        	        	        }
//	        	        	    }
//	        	        	}
//	                    	
//	                       	// *******************
//	                    	// PHASE GRAPH DISPLAY
//	                    	// *******************
//	        	        
//	        	        	Arrays.fill(phaseArray,-1);
//	        	        
//	                        offset_counter = 0;
//	                        op_counter = 0;
//	                        //System.out.println(" Counter reset: ");
//	                    	runningStatus = true;
//	                    	
//	                    }
//                    
//                    }
//                    
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//
//            thread.start();
//        }
//        
//    }
    
    private void updateColor(int index, Color color) {
        SwingUtilities.invokeLater(() -> {
            cells[index].setBackground(color);
            cells[index].repaint();
        });
    }
    
//    private SpectrumPhaseGLPanel createPhaseSpectrumPanel() {
//        GLCapabilities caps =
//                new GLCapabilities(GLProfile.get(GLProfile.GL2));
//        caps.setSampleBuffers(true);
//        caps.setNumSamples(8);
//        
//        return new SpectrumPhaseGLPanel(caps, gbl_sweep_point/2);
//    }

    private JPanel createSettingsPanel() {

        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(300, 0));
        panel.setBackground(new Color(40, 40, 40));
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        
        try {
            // replace "trs_logo.png" with your image path
            ImageIcon icon = new ImageIcon(sweep.class.getResource("/images/trs_logo.png"));

            // optional: scale logo to width 100px
            Image scaled = icon.getImage().getScaledInstance(80, -1, Image.SCALE_SMOOTH);
            JLabel logoLabel = new JLabel(new ImageIcon(scaled));

            gbc.anchor = GridBagConstraints.CENTER;
            panel.add(logoLabel, gbc);
            gbc.gridy++;

            // ---------- NAME BELOW LOGO ----------
            JLabel nameLabel = new JLabel("Tetra Radio Systems");
            nameLabel.setFont(new Font("Arial", Font.BOLD, 16));
            nameLabel.setForeground(new Color(168, 81, 138)); // <-- set your color here
            nameLabel.setHorizontalAlignment(SwingConstants.CENTER);

            panel.add(nameLabel, gbc);
            gbc.gridy++;

        } catch (Exception e) {
            System.out.println("Logo load failed: " + e.getMessage());
        }

        // ---------- Device Selection ----------
        panel.add(label("USB Device"), gbc);

        gbc.gridy++;
        deviceList = new JComboBox<>();
        deviceList.setPrototypeDisplayValue("Select Device");
        panel.add(deviceList, gbc);

        gbc.gridy++;

        // ---------- Start Frequency ----------
        panel.add(label("Center Frequency (KHz)"), gbc);
        gbc.gridy++;
        startFreq = field("3000000");
        panel.add(startFreq, gbc);

        //gbc.gridy++;
        //panel.add(label("Stop Frequency (KHz)"), gbc);
        //gbc.gridy++;
        stopFreq = field("6000000");
        //panel.add(stopFreq, gbc);

        gbc.gridy++;
        panel.add(label("Bandwidth (KHz)"), gbc);
        gbc.gridy++;
        bandwidth = field("4000");
        panel.add(bandwidth, gbc);

        gbc.gridy++;
        panel.add(label("FFT Size"), gbc);
        gbc.gridy++;
        fftSize = new JComboBox<>(new String[]{"256", "512", "1024", "2048", "4096"});
        fftSize.setSelectedIndex(0);
        panel.add(fftSize, gbc);
        
        gbl_sweep_point = Integer.parseInt(fftSize.getSelectedItem().toString());

        gbc.gridy++;
        panel.add(label("Gain (dBm)"), gbc);
        gbc.gridy++;
        gain = field("0");
        panel.add(gain, gbc);
        

        gbc.gridy++;
        panel.add(label("Waterfall Steps"), gbc);
        gbc.gridy++;
        waterfallstep = field("10");
        panel.add(waterfallstep, gbc);

        gbc.gridy++;
        startBtn = new JButton("START");
        panel.add(startBtn, gbc);

        return panel;
    }

    private void waitForWaterfallReady() {
        Timer t = new Timer(100, e -> {
            WaterfallPanel w = waterfallPanels.get(0);
            int wWidth  = w.getWidth();
            int wHeight = w.getHeight();

            if (wWidth > 0 && wHeight > 0) {
                System.out.println("INIT waterfall with size: " + wWidth + "x" + wHeight);
                w.init(wWidth, 20);
                w.clear();
                ((Timer)e.getSource()).stop();  // stop retrying once ready

                // test generator
//                new Timer(60, ev -> w.pushRandomRow()).start();
            } else {
                System.out.println("Waterfall NOT READY, retrying...");
            }
        });
        t.start();
    }
    
    private void changewaterfallstep(int step) {
//        Timer t = new Timer(100, e -> {
            WaterfallPanel w = waterfallPanels.get(0);
            int wWidth  = w.getWidth();
            int wHeight = w.getHeight();

            if (wWidth > 0 && wHeight > 0) {
                System.out.println("INIT waterfall with size: " + wWidth + "x" + step);
                w.init(wWidth, step);
                w.clear();
//                ((Timer)e.getSource()).stop();  // stop retrying once ready

                // test generator
//                new Timer(60, ev -> w.pushRandomRow()).start();
            } else {
                System.out.println("Waterfall NOT READY, retrying...");
            }
//        });
//        t.start();
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.WHITE);
        return l;
    }

    private JTextField field(String text) {
        JTextField f = new JTextField(text);
        return f;
    }
    
    public void sendSweep() {
    	
    	gbl_frequency = Integer.parseInt(startFreq.getText().trim());
    	gbl_start_frequency = Integer.parseInt(startFreq.getText().trim());
    	gbl_stop_frequency = Integer.parseInt(stopFreq.getText().trim());
    	gbl_bandwidth = Integer.parseInt(bandwidth.getText().trim());
    	gbl_sweep_point = Integer.parseInt(fftSize.getSelectedItem().toString());
    	gbl_gain = Integer.parseInt(gain.getText().trim());
    	gbl_waterfallstep = Integer.parseInt(waterfallstep.getText().trim());

        offset_counter = 0;
        op_counter = 0;
    			//    	gbl_sta = Integer.parseInt(startFreq.getText().trim());

        if (startBtn.getText().equals("START")) {
//        	spectrumPanels.get(0).clearMarkers();
        	
        	for (int i = 1; i <= gbl_sweep_point*2; i++) {
                phase_series.add(i, 0);
            }
        	
            // Set X-axis range 1 to maxX
            NumberAxis xAxis = (NumberAxis) phase_plot.getDomainAxis();
//            xAxis.setRange(1, gbl_sweep_point*2);
            xAxis.setRange(1, gbl_sweep_point/2);
            

        	runningStatus = true;
        	

            int deviceCount = deviceSweepMap.size();
            if (deviceCount == 0) return;
            
            //bufferShift = new BufferShift(gbl_sweep_point*4);
            
            if (gbl_gain < 0) gbl_gain = 0;
            else if (gbl_gain > 60) gbl_gain = 60;
            
            spectrumPanels.get(0).setreflevel(gbl_gain);
            
            gain.setText(String.valueOf(gbl_gain));
            
            if (gbl_waterfallstep < 5) gbl_gain = 5;
            else if (gbl_gain > 1000) gbl_gain = 1000;
            
            waterfallstep.setText(String.valueOf(gbl_waterfallstep));
            
//            changewaterfallstep(gbl_waterfallstep);

            stitchBuffer = new StitchBuffer(deviceCount);

            for (Map.Entry<String, DeviceSweepContext> e : deviceSweepMap.entrySet()) {
                int index = keyToIndexMap.get(e.getKey());
                long[] range = getDeviceFreqRange(index,deviceCount); //getDeviceFreqRange(index, deviceCount)

                e.getValue().segStartFreq = range[0];
                e.getValue().segEndFreq   = range[1];
            }

            for (String key : deviceSweepMap.keySet()) {
            	print("key: " + key);
                startDeviceSweep(key);
            }
            
            startBtn.setText("STOP");
            //startThreads();

        } else {

        	isInitialized = false;
            stopAllSweeps();
            startBtn.setText("START");
            runningStatus = false;
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            try {
				writeIQToCSV("iqdata_" + timestamp + ".csv");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            try {
				writePhaseToCSV("phase_" + timestamp + ".csv");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            phase_series.clear();
        }
    }
    
    void print(String text) {
    	System.out.println(text);
    }
    
    public static double[] fftShift(double[] in) {
        int N = in.length;
        double[] out = new double[N];
        int half = N / 2;

        System.arraycopy(in, half, out, 0, half);
        System.arraycopy(in, 0, out, half, half);

        return out;
    }
    
//    private long[] getDeviceFreqRangeSingleMode() {
//    	
//    	long globalStart = gbl_start_frequency;
//    	long startFreq = globalStart - (gbl_bandwidth/2);
//    	long endFreq = globalStart + (gbl_bandwidth/2);
//    	
//    	return new long[]{startFreq, endFreq};
//    	
//    }
    
    private long[] getDeviceFreqRange(int deviceIndex, int deviceCount) {
    	
    	long bandwidth   = gbl_bandwidth * 1000L;      // already in Hz
        long globalStart = gbl_start_frequency * 1000L  - (bandwidth/2);
        long globalStop  = gbl_start_frequency * 1000L  + (bandwidth/2);
        
        System.out.println(globalStart+" - "+globalStop);
        
        long deviceStart = 0;
        long totalSpan = globalStop - globalStart;

        // fractional span each device *should* cover
        double spanPerDevice = (double) totalSpan / deviceCount;
        double binPerDevice = (double) spanPerDevice / bandwidth;
        
        if(deviceIndex == 0) {
        	deviceStart = globalStart;
        }else {
        	deviceStart =  globalStart + (deviceIndex * (long) binPerDevice * bandwidth);
        }  
        
        long actualStop = globalStart + ((deviceIndex + 1) * (long) binPerDevice * bandwidth);

        // 🔥 LAST DEVICE FIX:
        // ensure total span is fully covered, no loss
//        if (deviceIndex == deviceCount - 1) {
//            actualStop = globalStop;
//        }
        
        System.out.println("deviceStart :"+deviceStart+" actualStop :"+actualStop);

        return new long[]{deviceStart, actualStop};
    }

    
    private void startDeviceSweep(String key) {
    	
        DeviceSweepContext ctx = deviceSweepMap.get(key);
//        
        
        if (ctx == null) 
    	{
        	System.out.println("key is empty"+" "+key+" ");
        	return;
        	
    	}

        ctx.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        ctx.future = ctx.scheduler.scheduleWithFixedDelay(() -> {
        	if(offset_counter <= 2 && runningStatus)
        		holdSend(key, ctx);
        }, 0, 7, TimeUnit.MILLISECONDS);
   
        
    }
    
    private boolean is_processing =false;
    private int batch_num =0;
    long startTime = System.nanoTime();
    long endTime = System.nanoTime();
    
    //private int complexSamples = 2048;
    DecimalFormat df = new DecimalFormat("#.0000");
    
    private void processData(String key, DeviceSweepContext ctx, int sample_shift) {

    	int start = sample_shift;
      	int end = sample_shift + (gbl_sweep_point*4)-1;
      	int length = end - start +1;
      	//System.out.println("Index size: " + globalArray.length);

	      	float[] hostIQ = new float[length];  // adjust if needed
	      	System.arraycopy(globalArray, start, hostIQ, 0, length);
	    	
    	is_processing = true;
    	int complexSamples = gbl_sweep_point*2;
    	//float[] hostIQ = processingArray;
    	
    	long startNs = System.nanoTime();
    	 double[] phase = new double[complexSamples];
    	 
    	 float[] rearray = new float[complexSamples];
    	 float[] imgarray = new float[complexSamples];
	        
	        for (int i = 0; i < complexSamples  ; i++) {
	        	
	        	double re = hostIQ[2 * i];
	        	rearray[i] = (float) re;
	        	double im = hostIQ[2 * i + 1];
	        	imgarray[i] = (float) im;
	            double phase_val = Math.atan2(re, im);  // -π … +π
	            
	            phase[i] = Math.toDegrees(phase_val);
	             
	        }
	        
        	synchronized (this) {
        	    if (iqCycles.size() < MAX_CYCLES) {
        	        iqCycles.add(Arrays.copyOf(rearray, rearray.length));
        	        iqCycles.add(Arrays.copyOf(imgarray, imgarray.length));
        	    }
        	}
	        
	      long endNs = System.nanoTime();
	      long execPhaseNs = endNs- startNs;
           //System.out.println("Phase Time: " + execPhaseNs + " ns");

	        /* =========================================================
	           2) Allocate GPU memory + copy
	           ========================================================= */
	        Pointer dData = new Pointer();
	        cudaMalloc(dData, (long) hostIQ.length * Float.BYTES);
	        
	        cudaMemcpy(
	            dData,
	            Pointer.to(hostIQ),
	            (long) hostIQ.length * Float.BYTES,
	            cudaMemcpyHostToDevice
	        );

	        /* =========================================================
	           3) FFT plan (C2C)
	           ========================================================= */
	        cufftHandle plan = new cufftHandle();
	        cufftPlan1d(plan, complexSamples, cufftType.CUFFT_C2C, batch_size);
	        
	        /* =========================================================
	           4) Execute FFT + timing
	           ========================================================= */
	        //long startNs = System.nanoTime();
	        cudaEvent_t startT = new cudaEvent_t();
            cudaEvent_t endT  = new cudaEvent_t();

            cudaEventCreate(startT);
            cudaEventCreate(endT);

	        cufftExecC2C(plan, dData, dData, CUFFT_FORWARD);
	        
	        cudaEventRecord(endT, null);
            cudaEventSynchronize(endT);

            float[] elapsedMs = new float[1];
            cudaEventElapsedTime(elapsedMs, startT, endT);
            
            //System.out.println("FFT Time : " + elapsedMs[0] + " ns"); 
            
            //System.out.println("Total Time : " + df.format(execPhaseNs+ (elapsedMs[0] *1e+6)) + " ns");  

	        //long execNs = 0; //System.nanoTime() - startNs;
	        

	        /* =========================================================
	           5) Copy FFT output back
	           ========================================================= */
	        float[] fftOut = new float[hostIQ.length];

	        cudaMemcpy(
	            Pointer.to(fftOut),
	            dData,
	            (long) fftOut.length * Float.BYTES,
	            cudaMemcpyDeviceToHost
	        );
	        
        cufftDestroy(plan);
        cudaFree(dData);
        
        
	        for (int i = 0; i < fftOut.length; i++) {
	        	fftOut[i] = Math.abs(fftOut[i]);
	        }
	          
	        /* =========================================================
	           6) Extract REAL data (N/2) → reinit amp
	           ========================================================= */
	        //int half = complexSamples / 2;
	        int full = complexSamples;
	        
	        double[] output = new double[full];
	
	        double fADCGain = 20*Math.log10(4096);
	        double fFFTGain = 20*Math.log10(full);
	        int n_avg = 1;
	        double fAveragingGain =
         10.0 * Math.log10((double) n_avg)
       + 0.45 * Math.log((double) n_avg) / Math.log(2.0);
	        
	        double fUnknownGain = 16.42;
	        
	        for (int i = 0; i < full ; i++) {
	        	
	        	float re = fftOut[2 * i];
	        	float im = fftOut[2 * i + 1];

	            // Real-only output
	            output[i] = (20*Math.log10(Math.sqrt(re*re+im*im))-fADCGain-fFFTGain-fAveragingGain * -1)+fUnknownGain;
	  
	        }
	        
	        output = trs_app_ConvertSingle2TwoSided(output,complexSamples);

	        double[] final_output = new double[gbl_sweep_point];
	        
	        int tc = 0;
	        for(int k = gbl_sweep_point/2; k < (gbl_sweep_point/2)+gbl_sweep_point; k++) {
	        	
	        	final_output[tc] = output[k];
	        	tc++;
	        	
	        }
	        	
        	int maxindex = findMaxIndex(final_output);
        	print(String.valueOf(maxindex));
	        double phase_degrees = phase[(gbl_sweep_point/2)+maxindex];
	        
        	phaseArray[(sample_shift/2) ] = phase_degrees;
        	

	        
	        /* =========================================================
	           7) Stitching logic (unchanged)
	           ========================================================= */
	        Integer index = keyToIndexMap.get(key);
	        if (index == null || stitchBuffer == null) return;

	        boolean sweepComplete = stitchBuffer.addPart(
	                index,
	                final_output,
	                phase,
	                ctx.segStartFreq,
	                ctx.segEndFreq
	        );

	        if (!sweepComplete) return;

	        StitchBuffer.Segment[] segments = stitchBuffer.getSegments();
	        stitchBuffer.reset();

	        /* =========================================================
	           8) FPS calculation
	           ========================================================= */
	        long maxFrameTimeMs = 0;
	        String slowestKey = null;

	        for (Map.Entry<String, DeviceSweepContext> e : deviceSweepMap.entrySet()) {
	            long t = e.getValue().lastFrameTimeMs;
	            if (t > maxFrameTimeMs) {
	                maxFrameTimeMs = t;
	                slowestKey = e.getKey();
	            }
	        }

	        double fps = maxFrameTimeMs > 0 ? 1000.0 / maxFrameTimeMs : 0;
	        //System.out.println("FPS = " + fps);

	        long finalMaxFrameTimeMs = maxFrameTimeMs;
	        double finalFps = fps;

	        ctx.uiLooper.post(() ->
	            fpsLabel.setText(
	                String.format("FPS: %.1f (%d ms)", finalFps, finalMaxFrameTimeMs)
	            )
	        );

	        /* =========================================================
	           9) Build final stitched arrays
	           ========================================================= */
	        List<Double> freqList = new ArrayList<>();
	        List<Double> ampList  = new ArrayList<>();
	        //List<Double> phaseList  = new ArrayList<>();
	      

	        for (StitchBuffer.Segment s : segments) {
	            if (s == null) continue;

	            int bins = s.amp.length;
	            double step = (s.endFreq - s.startFreq) / (bins - 1);

	            for (int i = 0; i < bins; i++) {
	                freqList.add(s.startFreq + i * step);
	                ampList.add(s.amp[i]);
	                //double degrees = Math.toDegrees(s.phase[i]);
	                //System.out.println(degrees+" "+s.amp[i]);
	                //phaseList.add(degrees);
	            }
	        }

	        double[] finalFreq = freqList.stream().mapToDouble(Double::doubleValue).toArray();
	        double[] finalAmp  = ampList.stream().mapToDouble(Double::doubleValue).toArray();
	        //double[] finalPhase = phaseList.stream().mapToDouble(Double::doubleValue).toArray();

	        /* =========================================================
	           10) UI update
	           ========================================================= */
	        spectrumPanels.get(0).updateSpectrum(finalFreq, finalAmp);

    }
    public void writeIQToCSV(String filename) throws IOException {

        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

        int numCycles = iqCycles.size();
        int maxLength = iqCycles.get(0).length;

        // Header
        for (int c = 0; c < numCycles; c++) {
            writer.write("i" + (c + 1));
            writer.write(",");
            writer.write("q" + (c + 1));
            if (c != numCycles - 1) writer.write(",");
        }
        writer.write("\n");

        // Data (row-wise across cycles)
        for (int i = 0; i < maxLength; i++) {
            for (int c = 0; c < numCycles; c++) {
                float[] cycle = iqCycles.get(c);

                if (i < cycle.length*2) {
                    writer.write(String.valueOf(cycle[i]));
                }

                if (c != numCycles - 1) writer.write(",");
            }
            writer.write("\n");
        }

        writer.close();
    }
    
    public void writePhaseToCSV(String filename) throws IOException {

        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

        int numCycles = phaseCycles.size();
        int maxLength = phaseCycles.get(0).length;

        // Header
        for (int c = 0; c < numCycles; c++) {
            writer.write("phase_cycle" + (c + 1));
            if (c != numCycles - 1) writer.write(",");
        }
        writer.write("\n");

        // Data
        for (int i = 0; i < maxLength; i++) {
            for (int c = 0; c < numCycles; c++) {
                double[] cycle = phaseCycles.get(c);

                if (i < cycle.length) {
                    writer.write(String.valueOf(cycle[i]));
                }

                if (c != numCycles - 1) writer.write(",");
            }
            writer.write("\n");
        }

        writer.close();
    }

    private void holdSend(String key, DeviceSweepContext ctx) {

    	//System.out.println("Hold Send");
    	
        try {
        	Future<?> future = ctx.fftExecutor.submit(() -> {
        	    holdSend1(key, ctx);
        	});

            future.get();   // wait for this device cycle to finish

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void holdSend1(String key, DeviceSweepContext ctx) {

    	 Integer index = keyToIndexMap.get(key);
    	 if (index == null) 
		 {
			 System.out.println("index is null "+ key);
			 return;
		 }

    	 usbHelper.sendData(key, index, gbl_frequency, gbl_start_frequency, gbl_stop_frequency, gbl_bandwidth, gbl_sweep_point, deviceSweepMap.size(), gbl_gain);

        synchronized (ctx.lock) {

            ctx.sweepFlag = false;

            while (!ctx.sweepFlag) {
                try {
                    ctx.lock.wait();   // waits ONLY for this device
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public void stopSingleDevice(String key)
    {
    	DeviceSweepContext ctx = deviceSweepMap.get(key);	
    	 synchronized (ctx.lock) {
             ctx.sweepFlag = true;   // unblock waiting threads
             ctx.lock.notifyAll();
         }

         if (ctx.future != null && !ctx.future.isCancelled()) {
             ctx.future.cancel(true);
         }

         if (ctx.scheduler != null && !ctx.scheduler.isShutdown()) {
             ctx.scheduler.shutdownNow();
         }
 
    }
	
    private void stopAllSweeps() {

        for (DeviceSweepContext ctx : deviceSweepMap.values()) {

            synchronized (ctx.lock) {
                ctx.sweepFlag = true;   // unblock waiting threads
                ctx.lock.notifyAll();
            }

            if (ctx.future != null && !ctx.future.isCancelled()) {
                ctx.future.cancel(true);
            }

            if (ctx.scheduler != null && !ctx.scheduler.isShutdown()) {
                ctx.scheduler.shutdownNow();
            }
        }
    }

	
//	public double[] arange(double start, double stop, double step) {
//	    int size = (int) Math.ceil((stop - start) / step);
//	    double[] result = new double[size];
//
//	    for (int i = 0; i < size; i++) {
//	        result[i] = start + step * i;
//	    }
//	    return result;
//	}
	
	private void rebuildDeviceList() {

	    deviceList.removeAllItems();
	    indexToKey.clear();
	    keyToIndexMap.clear();  // clear old mapping

	    for (int i = 0; i < sortedDeviceKeys.size(); i++) {
	        String key = sortedDeviceKeys.get(i);
	        deviceList.addItem(String.valueOf(i));   // UI shows 0,1,2,3
	        indexToKey.add(key);                     // map index -> real key
	        keyToIndexMap.put(key, i);               // map key -> index
	    }

	    if (deviceList.getItemCount() > 0) {
	        deviceList.setSelectedIndex(0);
	    }

	    // ----- DEBUG PRINT -----
	    System.out.println("===== Devices connected =====");
	    System.out.println("Sorted keys (numeric order): " + sortedDeviceKeys);
	    System.out.println("Key -> UI Index map: " + keyToIndexMap);
	    
	    for (int i = 0; i < indexToKey.size(); i++) {
	    	
	        System.out.println("UI Index: " + i + " -> Real Key: " + indexToKey.get(i));
	        
	    }
	    
	    System.out.println("============================");
	    
	}

	@Override
	public void onUsbConnected(String key) {

	    SwingUtilities.invokeLater(() -> {

	        if (sortedDeviceKeys.contains(key)) return;

	        sortedDeviceKeys.add(key);

	        sortedDeviceKeys.sort((a, b) ->
	                Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));

	        DeviceSweepContext ctx = new DeviceSweepContext();

	        deviceSweepMap.put(key, ctx);
	        
	        ctx.uiLooper.start();   // 🔥 start per-device UI looper
	        
	        rebuildDeviceList();

	        // 🔹 assign UI index AFTER rebuild
	        Integer index = keyToIndexMap.get(key);
	        if (index != null) {
	            ctx.uiIndex = index;
	        }
//	        new Thread(()->{
//	        	try {
//	        		if(runningStatus)
//			        {
//	        			Thread.sleep(5000);
//			        	startDeviceSweep(key);
//			        }
//					
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//	        	
//	        }).start();
	        
	    });
	}


	@Override
	public void onUsbDisconnected(String key, boolean flag) {

	    SwingUtilities.invokeLater(() -> {
	        sortedDeviceKeys.remove(key);
	        rebuildDeviceList();
	        stopSingleDevice(key);
	    });
	}
	
	public boolean isInitialized = false;
	//public BufferShift bufferShift;
	public int static_freq = 1000;
	public float[] glb_processPhase = new float[4096];
	public int offset_counter = 0;
	float[] globalArray = new float[5120000];
	float[] processingArray = new float[4096];
	double[] phaseArray = new double[16384]; // total data size received from the card
	double[] phaseWaterFallArray = new double[1024];
	int phase_counter =0 ;
	
//	public static int findMaxIndex(double[] data) {
//		
//        if (data == null || data.length == 0) {
//            return -1; // Handle empty or null array case
//        }
//        
//
//        int maxIndex = 0;
//        double maxValue = data[0]; // Assume the first element is initially the maximum
//        
//        int maxIndex2 = 0;
//        double maxValue2 = data[0]; // Assume the first element is initially the maximum
//
//        for (int i = 1; i < data.length; i++) {
//        	
//            if (data[i] > maxValue) {
//                maxValue = data[i]; // Update max value
//                maxIndex = i;       // Update max index
//            }
//            
//        }
//
//        return maxIndex;
//    }
	
	public static int findMaxIndex(double[] data) {

	    if (data == null || data.length < 2) {
	        return -1;
	    }

	    int maxIndex1 = -1;
	    int maxIndex2 = -1;

	    for (int i = 0; i < data.length; i++) {

	        if (maxIndex1 == -1 || data[i] > data[maxIndex1]) {
	            maxIndex2 = maxIndex1;
	            maxIndex1 = i;
	        } 
	        else if (i != maxIndex1 && 
	                (maxIndex2 == -1 || data[i] > data[maxIndex2])) {
	            maxIndex2 = i;
	        }
	    }

	    return Math.min(maxIndex1, maxIndex2);
	}
	
	
	//private float[] hostIQ = new float[4096];processingArray
	@Override
	public void onNewDataReceived(String key, byte[] data) {

	    DeviceSweepContext ctx = deviceSweepMap.get(key);
	    if (ctx == null) return;

	    /* ---------- timing ---------- */
	    long now = System.nanoTime();
	    ctx.lastFrameTimeMs = (now - ctx.frameStartTimeNs) / 1_000_000;
	    ctx.frameStartTimeNs = now;

	    //System.out.println("Device " + key + " time = " + ctx.lastFrameTimeMs + " ms");

	    /* ---------- unblock only this device ---------- */
	    synchronized (ctx.lock) {
	        ctx.sweepFlag = true;
	        ctx.lock.notifyAll();
	    }

	    ctx.dataHandler.submit(() -> {
	        if(offset_counter < 2) {
	        RecieveDataStructModel model = new RecieveDataStructModel(data);
	        byte[] spectrumBytes = model.getUiSpectrum();
	        System.out.println("Spectrum data Length: "+spectrumBytes.length);
	        if (spectrumBytes == null || spectrumBytes.length < 4) return;
	        
	        //System.out.println("spectrumBytes.length: "+spectrumBytes.length);
//	        return;
//
//	        /* =========================================================
//	           1) Parse IQ int16 → complex float
//	           ========================================================= */
	        ByteBuffer bb = ByteBuffer.wrap(spectrumBytes)
	                                  .order(ByteOrder.LITTLE_ENDIAN);
//
	        int complexSamples = spectrumBytes.length/4; // I + Q (int16) 8192/4 = 2048
//	        
	        float[] hostIQ = new float[spectrumBytes.length/2]; // 8192/2 = 4096
	        
	        for (int i = 0; i < complexSamples; i++) {  // Iterations of 2048
	        	hostIQ[2 * i]     = bb.getShort(); // I 2048
	        	hostIQ[2 * i + 1] = bb.getShort(); // Q 2048
	        }									   // Total 4096
	      	
	        System.out.println("hostIQ.length: "+hostIQ.length);
	        
	        if(offset_counter == 0) {
	        	
	        	Arrays.fill(globalArray,0);
	        	
	        	globalArray = Arrays.copyOf(hostIQ, hostIQ.length);
	        	
	        }
	        else {
	        	
	        	float[] slide_data = globalArray.clone();
	        	
		      	globalArray = ArrayUtils.addAll(slide_data, hostIQ);
		        
	        }
	        
	        offset_counter++;
	        
	        if(offset_counter == 2) {

	        	System.out.println("spectrumBytes.length: "+globalArray.length);
	        	
	        	startThreads(key,ctx);
	        	
	        }
	        
	        }
	        
	        
	    });
 
	}
	
	public double[] trs_app_Convert2dB(double[] fFFTMag, int uiNFFT, double fNormFact)
	{
		
		double[] iFFTMagIndB = new double[uiNFFT];
		double fdb;

		for(int i=0;i<uiNFFT;i++)
		{
			fdb = (10*Math.log(Math.sqrt(fFFTMag[i])) - fNormFact); //multiply by 100
			fdb = 100*(fdb*0.897 - 3.0);//2.418);
			iFFTMagIndB[i] = fdb;
		}
		return iFFTMagIndB;
	}
	
	public double[] trs_app_ConvertSingle2TwoSided(double[] fFFTMag, int uiNFFT)
	{
		//System.out.println("Size: "+uiNFFT);
		double[] output = new double[uiNFFT];
		
		int idx =0;
		int half = uiNFFT/2;
		
		for(int i=0;i<half;i++)
		{
			output[i+half] = fFFTMag[idx++];
		}
		
		for(int i=0;i<half;i++)
		{
			output[i] = fFFTMag[idx++];
		}
		
		return output;
		
	}

	
	@Override
	public void appendLog(String text) {
		System.out.println(text);
	}
	

}
