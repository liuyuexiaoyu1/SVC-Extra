package com.liuyue.svcextra.audio;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AecReferenceMixer {
    private static final ConcurrentLinkedQueue<short[]> queue = new ConcurrentLinkedQueue<>();
    private static final int FRAME_SIZE = 480;

    public static void push(short[] audio) {
        for (int off = 0; off + FRAME_SIZE <= audio.length; off += FRAME_SIZE) {
            short[] frame = new short[FRAME_SIZE];
            System.arraycopy(audio, off, frame, 0, FRAME_SIZE);
            queue.offer(frame);
        }
        while (queue.size() > 300) queue.poll();
    }

    public static short[] poll() {
        return queue.poll();
    }

    public static void clear() {
        queue.clear();
    }
}