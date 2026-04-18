package com.trs.DeviceHandlers;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import org.usb4java.*;

import com.trs.UsbCommandSender;



public class multiUsbManager implements Runnable {

	    private final UsbHelperListener listener;
	    private Context context;

	    // Per-device tracking
	    private final Map<String, DeviceHandle> deviceHandles = new HashMap<>();
	    private final Map<String, Thread> receiveThreads = new HashMap<>();
	    private final Map<String, Boolean> deviceStates = new HashMap<>();
	    
	    private final Map<String, Byte> deviceInEndpoint = new HashMap<>();
	    private final Map<String, Byte> deviceOutEndpoint = new HashMap<>();


	    private volatile boolean running = true;

	    private static final int INTERFACE = 0;
	    private static final short VID = (short) 0x0d7d;
	    private static final short PID = (short) 0x0100;
	    
	    private byte outEndpoint = (byte) 0x01;


	    public interface UsbHelperListener {
	        void onUsbConnected( String key);
	        void onUsbDisconnected(String key, boolean flag);
	        void onNewDataReceived(String key, byte[] data);
	        void appendLog(String text);
	    }

	    public multiUsbManager(UsbHelperListener listener) {
	        this.listener = listener;
	        context = new Context();
	    }

	    @Override
	    public void run() {
	    	

	        int result = LibUsb.init(context);
	        if (result != LibUsb.SUCCESS) {
	        	System.out.println("multiUsbManager.run() STARTED");
	            throw new RuntimeException("Unable to initialize libusb: " + LibUsb.strError(result));
	        }

	        listener.appendLog("🔄 USB Monitor started");

	        while (running) {
	            Set<String> foundKeys = new HashSet<>();

	            DeviceList list = new DeviceList();
	            result = LibUsb.getDeviceList(context, list);
	            if (result < 0) {
	                listener.appendLog("❌ Failed to get device list: " + LibUsb.strError(result));
	                break;
	            }

	            try {
	                for (Device device : list) {
	                    DeviceDescriptor descriptor = new DeviceDescriptor();
	                    LibUsb.getDeviceDescriptor(device, descriptor);

	                    if (descriptor.idVendor() == VID && descriptor.idProduct() == PID) {
	                        String key = getDeviceKey(device, descriptor);
	                        if(key == null)
	                        {
	                        	continue;
	                        }
	                        foundKeys.add(key);
	                      

	                        if (!deviceHandles.containsKey(key)) {
	                            listener.appendLog("✅ New device detected: " + key);

	                            DeviceHandle handle = new DeviceHandle();
	                            int r = LibUsb.open(device, handle);
	                            if (r != LibUsb.SUCCESS) {
	                                listener.appendLog("❌ Failed to open device: " + LibUsb.strError(r));
	                                try{
		            					int result1 = LibUsb.open(device, handle);
		            					if(result1==LibUsb.ERROR_ACCESS) {
		            						
		            					}
		            				}catch (Exception e) {
		            					// TODO: handle exception
		            					System.out.println("error"+e.getMessage());
		            				}
	                                continue;
	                            }
	                            
	                           

	                            // Claim interface
	                            r = LibUsb.claimInterface(handle, INTERFACE);
	                            if (r != LibUsb.SUCCESS) {
	                                listener.appendLog("❌ Failed to claim interface: " + LibUsb.strError(r));
	                                LibUsb.close(handle);
	                                continue;
	                            }
	                            

	                            deviceHandles.put(key, handle);
	                            deviceStates.put(key, true);

	                            // Read and log USB descriptors
	                            logDeviceDetails(device, handle, descriptor);
	                            
	                            ConfigDescriptor config = new ConfigDescriptor();
	                            if (LibUsb.getActiveConfigDescriptor(device, config) == LibUsb.SUCCESS) {
	                            for (Interface iface : config.iface()) {
	                                for (InterfaceDescriptor ifDesc : iface.altsetting()) {
	                                    for (EndpointDescriptor epDesc : ifDesc.endpoint()) {
	                                        byte epAddr = epDesc.bEndpointAddress();
	                                        if ((epAddr & LibUsb.ENDPOINT_IN) != 0)
	                                            deviceInEndpoint.put(key, epAddr);
	                                        else
	                                            deviceOutEndpoint.put(key, epAddr);
	                                    }
	                                }
	                            }
	                            }

	                            StringBuffer productBuf = new StringBuffer();
	                            LibUsb.getStringDescriptorAscii(handle, descriptor.iProduct(), productBuf);
	                            // Start receiver thread
	                            startReceiveThread(key, handle);
	                            System.out.println("key is :"+ key);

	                            listener.onUsbConnected(key);
	                        }
	                    }
	                }
	            } finally {
	                LibUsb.freeDeviceList(list, true);
	            }

	            // Cleanup disconnected devices
	            for (String key : new HashSet<>(deviceHandles.keySet())) {
	                if (!foundKeys.contains(key)) {
	                    listener.appendLog("❌ Device disconnected: " + key);
	                    stopReceiveThread(key);

	                    DeviceHandle handle = deviceHandles.remove(key);
	                    if (handle != null) {
	                        LibUsb.releaseInterface(handle, INTERFACE);
	                        LibUsb.close(handle);
	                    }
	                    deviceStates.remove(key);
	                    boolean flag = deviceStates.size()==0 ? true: false;
	                 
	                    listener.onUsbDisconnected(key, flag);
	                  
	                    
	                }
	            }

	            try {
	                Thread.sleep(1000);
	            } catch (InterruptedException ignored) {}
	        }

	        shutdown();
	    }

	    public void sendData(String key, int index,int frequency, int start_frequency, int end_frequency, int bandwidth, int sweep_point, int num_devices, int gbl_gain) {
	    	DeviceHandle handle = deviceHandles.get(key);
	    	UsbCommandSender commandSender = new UsbCommandSender(num_devices);
			commandSender.sendMode(index, 9,frequency, start_frequency, end_frequency, bandwidth, sweep_point, gbl_gain, handle, deviceOutEndpoint.get(key), key);
//			System.out.println("sendings");
			
		}
	    
	    /** Send data to a specific device */
	    public void sendData(String key, ByteBuffer sendBuffer) {
	        DeviceHandle handle = deviceHandles.get(key);
	        if (handle == null) {
	            listener.appendLog("⚠ Device " + key + " not found");
	            return;
	        }

	        IntBuffer transferred = IntBuffer.allocate(1);
	        sendBuffer.rewind();
	        byte out = deviceOutEndpoint.get(key);
	        int result = LibUsb.bulkTransfer(handle, out , sendBuffer, transferred, 2000);
	        if (result == LibUsb.SUCCESS) {
	            listener.appendLog("✅ Sent " + transferred.get(0) + " bytes to " + key);
	        } else {
	            listener.appendLog("❌ Send failed for " + key + ": " + LibUsb.strError(result));
	        }
	    }

	    /** Generate unique key for each USB device */
	    private String getDeviceKey(Device device, DeviceDescriptor descriptor) {
	        StringBuffer serialBuf = new StringBuffer();
	        DeviceHandle tmpHandle = new DeviceHandle();
	        if (LibUsb.open(device, tmpHandle) == LibUsb.SUCCESS) {
	            if (descriptor.iSerialNumber() != 0) {
	                LibUsb.getStringDescriptorAscii(tmpHandle, descriptor.iSerialNumber(), serialBuf);
	            }
	            LibUsb.close(tmpHandle);
	        }

	        String key;
	        if (serialBuf.length() > 0) {
	            key = serialBuf.toString();
	            // Take last 2 or 3 digits
	            int len = key.length();
	            if (len > 3) key = key.substring(len - 3); // last 3 digits
	            else if (len > 2) key = key.substring(len - 2); // last 2 digits
	            
	           return key;
	        }

	        return null;
	    }

	    /** Log all descriptor info */
	    private void logDeviceDetails(Device device, DeviceHandle handle, DeviceDescriptor descriptor) {
	        StringBuffer manufacturerBuf = new StringBuffer();
	        StringBuffer productBuf = new StringBuffer();
	        StringBuffer serialBuf = new StringBuffer();

	        if (descriptor.iManufacturer() != 0)
	            LibUsb.getStringDescriptorAscii(handle, descriptor.iManufacturer(), manufacturerBuf);
	        if (descriptor.iProduct() != 0)
	            LibUsb.getStringDescriptorAscii(handle, descriptor.iProduct(), productBuf);
	        if (descriptor.iSerialNumber() != 0)
	            LibUsb.getStringDescriptorAscii(handle, descriptor.iSerialNumber(), serialBuf);

	        listener.appendLog(String.format("""
	-------------------------------
	USB Device Info:
	  idVendor      : 0x%04x
	  idProduct     : 0x%04x
	  bcdDevice     : %x.%02x
	  iManufacturer : %s
	  iProduct      : %s
	  iSerial       : %s
	  bNumConfig    : %d
	-------------------------------
	""",
	            descriptor.idVendor(),
	            descriptor.idProduct(),
	            (descriptor.bcdDevice() >> 8), (descriptor.bcdDevice() & 0xff),
	            manufacturerBuf.toString(),
	            productBuf.toString(),
	            serialBuf.toString(),
	            descriptor.bNumConfigurations()
	        ));
	    }



	    /** Start per-device receiver thread */
	    private void startReceiveThread(String key, DeviceHandle handle) {
	        Thread thread = new Thread(() -> {
	            ByteBuffer recvBuffer = ByteBuffer.allocateDirect(500000);
	            IntBuffer transferred = IntBuffer.allocate(1);

	            listener.appendLog("📥 Listening on device " + key);

	            while (deviceStates.getOrDefault(key, false)) {
	                transferred.rewind();
	                recvBuffer.clear();
	                byte in = deviceInEndpoint.get(key);
	                int res = LibUsb.bulkTransfer(handle, in, recvBuffer, transferred, 1000);
	                if (res == LibUsb.SUCCESS) {
	                    byte[] data = new byte[transferred.get()];
	                    recvBuffer.rewind();
	                    recvBuffer.get(data, 0, data.length);
	                    listener.onNewDataReceived(key, data);
	                } else if (res == LibUsb.ERROR_TIMEOUT) {
	                    continue;
	                } else if (res == LibUsb.ERROR_NO_DEVICE) {
	                    listener.appendLog("❌ Device lost: " + key);
	                    break;
	                }
	            }
	        });
	        receiveThreads.put(key, thread);
	        thread.start();
	    }

	    /** Stop per-device receiver thread */
	    private void stopReceiveThread(String key) {
	        deviceStates.put(key, false);
	        Thread t = receiveThreads.remove(key);
	        if (t != null) {
	            try { t.join(500); } catch (InterruptedException ignored) {}
	        }
	    }

	    /** Stop everything and cleanup */
	    public void stop() {
	        running = false;
	    }

	    public void shutdown() {
	        for (String key : new ArrayList<>(deviceHandles.keySet())) {
	            stopReceiveThread(key);
	            DeviceHandle handle = deviceHandles.remove(key);
	            if (handle != null) {
	                LibUsb.releaseInterface(handle, INTERFACE);
	                LibUsb.close(handle);
	            }
	        }
	        deviceStates.clear();
	        if (context != null) {
//	            LibUsb.exit(context);
	            context = null;
	        }
	        listener.appendLog("🛑 USB Monitor stopped");
	    }
	}

