package com.trs;

class StitchBuffer {

    static class Segment {
        double[] amp;
        double[] phase;
        long startFreq;
        long endFreq;
    }

    private final Segment[] segments;
    private final boolean[] received;
    private final int parts;
    private int receivedCount = 0;

    StitchBuffer(int parts) {
        this.parts = parts;
        this.segments = new Segment[parts];
        this.received = new boolean[parts];
    }

    synchronized boolean addPart(int index, double[] amp, double[] phase, long start, long end) {
        if (index < 0 || index >= parts) return false;
        if (received[index]) return false;

        Segment s = new Segment();
        s.amp = amp;
        s.phase = phase;
        s.startFreq = start;
        s.endFreq = end;

        segments[index] = s;
        received[index] = true;
        receivedCount++;

        return receivedCount == parts;
    }

    synchronized Segment[] getSegments() {
        return segments.clone();
    }

    synchronized void reset() {
        for (int i = 0; i < parts; i++) {
            segments[i] = null;
            received[i] = false;
        }
        receivedCount = 0;
    }
}
