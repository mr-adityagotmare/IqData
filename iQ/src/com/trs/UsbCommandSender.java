package com.trs;

import org.usb4java.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class UsbCommandSender {

    private final int deviceCount;   // same as len(self.device_address)

	public UsbCommandSender(int deviceCount) {
        this.deviceCount = deviceCount;
    }

    public void sendMode(
            int devid,
            int mode,
            int frequency,
            int start_frequency,
            int end_frequency,
            int bandwidth,
            int sweep_point,
            int gbl_gain,
            DeviceHandle handle,
            byte endpointOut,
            String key
    ) {
        try {
            // -----------------------
            // Read configuration values
            // -----------------------
            int navg = 1;
            int nshots = 0;

            int nfft = sweep_point;
//            int fc_start_cfg = start_frequency;
//            int fc_end_input = end_frequency;
            int sBw = 15000;

            int Gain = gbl_gain;
            //System.out.println("Gain: "+ (Gain));

            int Filter = 0;
            

            int fc_start = 0;
            int fc_end   = 0;
           
            
            int totalSpan = end_frequency - start_frequency;
        
            // fractional span each device *should* cover
            int spanPerDevice = (int) totalSpan / deviceCount;
            int binPerDevice = (int) spanPerDevice / bandwidth;
            
            
            if(devid == 0) {
            	fc_start = start_frequency;
            }else {
            	fc_start =  start_frequency + (devid * binPerDevice * bandwidth);
            }        
            fc_end = start_frequency + ((devid+1) * binPerDevice * bandwidth);


            // -----------------------
            // Compute per-device frequency range
            // -----------------------
//            int comp_freq = (fc_end_input - fc_start_cfg) / deviceCount;
//
//            int tempFcStart = fc_start_cfg;
//
//            int fc_start = tempFcStart + (devid * comp_freq);
//            int fc_end   = tempFcStart + ((devid + 1) * comp_freq);
            
//            System.out.println("devid: "+ (devid));
//            System.out.println("fc_start: "+ (fc_start));
//            System.out.println("fc_end: "+ (fc_end));

            // -----------------------
            // Fixed arrays (same as Python)
            // -----------------------
            int[] fc = { frequency, 2000000, 3000000, 4000000, 5000000,
                           600, 700, 800, 900, 1000 };

            int[] Bw = { bandwidth, 5000, 5000, 5000, 5000,
                         5000, 5000, 5000, 5000, 5000 };

            int nfreq = 0;
            int nrecords = 0;
            float fth = 0.0f;
            int maxproccount = 1;
            float E_Gain = 0f;

            // -----------------------
            // Build struct and serialize
            // -----------------------
            byte[] payload = new SendDataStructModel(
                    mode, navg, nshots, nfft, Gain, Filter,
                    fc_start, fc_end, sBw,
                    fc, Bw,
                    nfreq, nrecords,
                    fth, maxproccount,
                    E_Gain
            ).serialize();

            // -----------------------
            // Send data to USB device (bulk OUT)
            // -----------------------
            if (handle != null) {

                ByteBuffer buffer = ByteBuffer.allocateDirect(payload.length);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(payload);
                buffer.rewind();

                IntBuffer transferred = IntBuffer.allocate(1);
                
                //int iface = 0;  // adjust if your device uses a different interface
                
                int result = LibUsb.bulkTransfer(
                        handle,
                        endpointOut,
                        buffer,
                        transferred,
                        2000  // timeout ms
                );

                if (result != LibUsb.SUCCESS) {
                    System.err.println("Error sending mode to device " + devid + ": " +
                            LibUsb.strError(result));
                } else {
//                    System.out.println("Mode sent to"+key+" device " + devid +
//                            ", bytes=" + transferred.get(0));
//                    
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}