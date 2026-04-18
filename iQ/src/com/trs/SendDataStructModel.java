package com.trs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SendDataStructModel {

 	private int mode;          // 4 bytes
    private int navg;
    private int nshots;
    private int nfft;
    private int Gain;
    private int Filter;
    private int fc_start;
    private int fc_end;
    private int sBw;

    private int[] fc = new int[10];    // 10 frequencies
    private int[] Bw = new int[10];    // 10 bandwidths

    private int nfreq;
    private int nrecords;
    private float fth;
    private int maxproccount;
    private float E_Gain;

    public SendDataStructModel(
            int mode,
            int navg,
            int nshots,
            int nfft,
            int Gain,
            int Filter,
            int fc_start,
            int fc_end,
            int sBw,
            int[] fc,
            int[] Bw,
            int nfreq,
            int nrecords,
            float fth,
            int maxproccount,
            float E_Gain
    ) {

        // -------- Mode encoding same as Python ----------
        switch (mode) {
            case 0: this.mode = 0xf0f0f0f0; break;
            case 1: this.mode = 0xf0f0f0f1; break; // hopping
            case 2: this.mode = 0xf0f0f0f2; break;
            case 4: this.mode = 0xf0f0f0f4; break;
            case 5: this.mode = 0xf0f0f0f5; break;
            case 8: this.mode = 0xf0f0f0f8; break;
            case 9: this.mode = 0xf0f0f0f9; break;
            case 10: this.mode = 0x01010101; break;
            default: this.mode = 0xf0f0f0f0; break;
        }

        this.navg = navg;
        this.nshots = nshots;
        this.nfft = nfft;
        this.Gain = Gain;
        this.Filter = Filter;
        this.fc_start = fc_start;
        this.fc_end = fc_end;
        this.sBw = sBw;

        // default length = 10
        if (fc != null) System.arraycopy(fc, 0, this.fc, 0, Math.min(fc.length, 10));
        if (Bw != null) System.arraycopy(Bw, 0, this.Bw, 0, Math.min(Bw.length, 10));

        this.nfreq = nfreq;
        this.nrecords = nrecords;
        this.fth = fth;
        this.maxproccount = maxproccount;
        this.E_Gain = E_Gain;
    }

    // ---------- Serialize to byte[] (Little Endian, identical to Python) ----------
    public byte[] serialize() {

        int totalSize = 136;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(mode);
        buffer.putInt(navg);
        buffer.putInt(nshots);
        buffer.putInt(nfft);
        buffer.putInt(Gain);
        buffer.putInt(Filter);
        buffer.putInt(fc_start);
        buffer.putInt(fc_end);
        buffer.putInt(sBw);

        for (int i = 0; i < 10; i++) {
            buffer.putInt(fc[i]);
            buffer.putInt(Bw[i]);
        }

        buffer.putInt(nfreq);
        buffer.putInt(nrecords);
        buffer.putFloat(fth);
        buffer.putInt(maxproccount);
        buffer.putFloat(E_Gain);

        return buffer.array();
    }
	
}
