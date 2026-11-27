/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.common.debug;

import java.util.Arrays;

public final class XenoFrameTimeStatistics {
    private static final int SAMPLE_COUNT = 2048;
    public static final XenoFrameTimeStatistics INSTANCE = new XenoFrameTimeStatistics();
    private final long[] samples = new long[SAMPLE_COUNT];
    private final long[] scratch = new long[SAMPLE_COUNT];
    private int writeIndex;
    private int sampleSize;
    private volatile long p50Ns;
    private volatile long p98Ns;
    private volatile long p995Ns;
    private volatile boolean dirty = true;

    private XenoFrameTimeStatistics() {
    }

    public void logSample(long durationNs) {
        if (durationNs <= 0L) {
            return;
        }
        this.samples[this.writeIndex] = durationNs;
        this.writeIndex = (this.writeIndex + 1) % SAMPLE_COUNT;
        if (this.sampleSize < SAMPLE_COUNT) {
            this.sampleSize++;
        }
        this.dirty = true;
    }

    public boolean hasSamples() {
        return this.sampleSize > 0;
    }

    public void updateIfNeeded() {
        if (!this.dirty || this.sampleSize == 0) {
            return;
        }
        this.dirty = false;

        int n = this.sampleSize;
        int p50Count = Math.min(n, 200);
        int start = (this.writeIndex - p50Count + SAMPLE_COUNT) % SAMPLE_COUNT;
        for (int i = 0; i < p50Count; i++) {
            this.scratch[i] = this.samples[(start + i) % SAMPLE_COUNT];
        }
        Arrays.sort(this.scratch, 0, p50Count);
        this.p50Ns = this.scratch[(int) ((p50Count - 1) * 0.50f)];

        if (n == SAMPLE_COUNT) {
            System.arraycopy(this.samples, 0, this.scratch, 0, n);
        } else {
            System.arraycopy(this.samples, 0, this.scratch, 0, n);
        }
        Arrays.sort(this.scratch, 0, n);
        this.p98Ns = this.scratch[(int) ((n - 1) * 0.98f)];
        this.p995Ns = this.scratch[(int) ((n - 1) * 0.995f)];
    }

    public long p50Fps() {
        return nanosToFps(this.p50Ns);
    }

    public long p98Fps() {
        return nanosToFps(this.p98Ns);
    }

    public long p995Fps() {
        return nanosToFps(this.p995Ns);
    }

    private static long nanosToFps(long ns) {
        return ns > 0L ? Math.round(1_000_000_000.0 / ns) : 0L;
    }
}
