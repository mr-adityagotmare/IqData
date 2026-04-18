package com.trs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RecieveDataStructModel {

    private long ui_mode;               // Python <I → unsigned int
    private long ui_nspecms;            // unsigned int
    private long ui_idx;                // unsigned int
    private long ui_nfft;               // unsigned int
    private long ui_centerFreq_khz;     // unsigned int
    private long ui_bandwidth_khz;      // unsigned int

    private byte[] ui_spectrum;         // remaining bytes
    private byte[] ui_table_data;       // currently unused (Python: None)

    public RecieveDataStructModel(byte[] data) {
        if (data == null || data.length < 24) {
            throw new IllegalArgumentException("Data must be at least 24 bytes long");
        }

        // First 24 bytes: 6 unsigned integers (little endian)
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, 24);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Convert to long because Java has no unsigned int
        this.ui_mode            = Integer.toUnsignedLong(buffer.getInt());
        this.ui_nspecms         = Integer.toUnsignedLong(buffer.getInt());
        this.ui_idx             = Integer.toUnsignedLong(buffer.getInt());
        this.ui_nfft            = Integer.toUnsignedLong(buffer.getInt());
        this.ui_centerFreq_khz  = Integer.toUnsignedLong(buffer.getInt());
        this.ui_bandwidth_khz   = Integer.toUnsignedLong(buffer.getInt());

        // Remaining bytes = spectrum
        this.ui_spectrum = new byte[data.length - 24];
        System.arraycopy(data, 24, this.ui_spectrum, 0, this.ui_spectrum.length);

        this.ui_table_data = null; // same as Python
    }

    // ------- Getter methods ---------

    public long getUiMode() {
        return ui_mode;
    }

    public long getUiNspecms() {
        return ui_nspecms;
    }

    public long getUiIdx() {
        return ui_idx;
    }

    public long getUiNfft() {
        return ui_nfft;
    }

    public long getUiCenterFreqKHz() {
        return ui_centerFreq_khz;
    }

    public long getUiBandwidthKHz() {
        return ui_bandwidth_khz;
    }

    public byte[] getUiSpectrum() {
        return ui_spectrum;
    }

    public byte[] getUiTableData() {
        return ui_table_data;
    }
}
