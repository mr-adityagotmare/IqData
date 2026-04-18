package com.trs;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LooperThread extends Thread {
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    public void post(Runnable r) {
        queue.offer(r);
    }

    @Override
    public void run() {
        
        while (running) {
            try {
                Runnable task = queue.take();
                task.run(); // Execute on this thread
            } catch (InterruptedException ignored) {}
        }
        System.out.println("Looper stopped");
    }

    public void quit() {
        running = false;
        this.interrupt();
    }
}
