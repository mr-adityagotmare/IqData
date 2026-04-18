package com.trs;


public class BufferShift {

    private int WINDOW_SIZE = 512;

    private final float[] slidingBuffer;
    private int initializedElements = 0;

    public BufferShift(int window_size) {
    	this.WINDOW_SIZE = window_size;
        this.slidingBuffer = new float[WINDOW_SIZE];
    }

    /**
     * Initialize the buffer with the first chunk of data.
     * Must be at least WINDOW_SIZE elements.
     */
    public void initialize(float[] initialData) {
        if (initialData.length < WINDOW_SIZE) {
            throw new IllegalArgumentException(
                "Initial data must be at least " + WINDOW_SIZE + " elements"
            );
        }

        System.arraycopy(initialData, 0, slidingBuffer, 0, WINDOW_SIZE);
        initializedElements = WINDOW_SIZE;
    }

    /**
     * Process new incoming data.
     * For each new float, shift left by one and append it.
     */
    public float[] process(float[] newData) {
        ensureInitialized();

        for (float value : newData) {
            shiftLeftByOne();
            slidingBuffer[WINDOW_SIZE - 1] = value;
        }
        
        return slidingBuffer.clone();
        
    }

    /**
     * Returns the current sliding window.
     */
    public float[] getProcessedBuffer() {
        ensureInitialized();
        return slidingBuffer.clone();
    }

    /**
     * Shift buffer contents left by one element.
     */
    private void shiftLeftByOne() {
        System.arraycopy(slidingBuffer, 1, slidingBuffer, 0, WINDOW_SIZE - 1);
    }

    private void ensureInitialized() {
        if (initializedElements < WINDOW_SIZE) {
            throw new IllegalStateException("Buffer not initialized. Call initialize() first.");
        }
    }
}
