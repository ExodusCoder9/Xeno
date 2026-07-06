package com.xeno.client.renderer;

public class XenoEntityStats {
    private static final ThreadLocal<FrameStats> STATS = ThreadLocal.withInitial(FrameStats::new);

    public static FrameStats get() {
        return STATS.get();
    }

    public static void resetFrame() {
        STATS.get().reset();
    }

    public static class FrameStats {
        public int entitiesSubmitted;
        public int featurePhasesExecuted;
        public long featureExecutionNanos;
        public boolean featuresEnabled = true;

        public void reset() {
            entitiesSubmitted = 0;
            featurePhasesExecuted = 0;
            featureExecutionNanos = 0;
        }

        public void recordEntitySubmitted() {
            entitiesSubmitted++;
        }

        public void recordPhaseExecuted() {
            featurePhasesExecuted++;
        }

        public void addExecutionTime(long nanos) {
            featureExecutionNanos += nanos;
        }
    }
}
