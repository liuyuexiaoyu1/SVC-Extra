package com.liuyue.svcextra.audio;

import java.util.concurrent.ConcurrentLinkedQueue;

public class AecSyncBuffer {
    public record TimestampedFrame(long timestampMs, short[] audio) {}

    private final ConcurrentLinkedQueue<TimestampedFrame> queue = new ConcurrentLinkedQueue<>();
    private final int maxFrames;

    public AecSyncBuffer(int maxFrames) {
        this.maxFrames = maxFrames;
    }

    public void push(short[] audio, long timestampMs) {
        short[] copy = new short[audio.length];
        System.arraycopy(audio, 0, copy, 0, audio.length);
        queue.offer(new TimestampedFrame(timestampMs, copy));
        while (queue.size() > maxFrames) queue.poll();
    }

    public short[] consume(long targetMs, long windowMs) {
        long cutoff = targetMs - windowMs;
        TimestampedFrame best = null;
        long bestDiff = Long.MAX_VALUE;

        while (true) {
            TimestampedFrame head = queue.peek();
            if (head == null) break;
            if (head.timestampMs() < cutoff) {
                queue.poll();
                continue;
            }
            break;
        }

        for (TimestampedFrame frame : queue) {
            long diff = targetMs - frame.timestampMs();
            if (diff < 0 || diff > windowMs) continue;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = frame;
            }
        }

        if (best != null) {
            while (!queue.isEmpty() && queue.peek().timestampMs() <= best.timestampMs()) {
                queue.poll();
            }
            return best.audio();
        }
        return null;
    }

    public void clear() {
        queue.clear();
    }

    public int size() {
        return queue.size();
    }
}