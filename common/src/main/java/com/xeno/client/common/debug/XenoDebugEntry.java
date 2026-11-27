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

import com.xeno.client.common.render.XenoChunkRenderer;
import com.xeno.client.common.render.chunk.XenoChunkExecutorService;
import com.xeno.client.common.render.culling.XenoOcclusionCuller;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class XenoDebugEntry implements DebugScreenEntry {
    public static final Identifier DEBUG_GROUP = Identifier.fromNamespaceAndPath("xeno", "debug_group");
    public static final Identifier DEBUG_ENTRY_FULL = Identifier.fromNamespaceAndPath("xeno", "debug_full");
    public static final Identifier DEBUG_ENTRY_REDUCED = Identifier.fromNamespaceAndPath("xeno", "debug_reduced");
    public static final Identifier FPS_PERCENTILES = Identifier.fromNamespaceAndPath("xeno", "fps_percentiles");

    private static volatile String version = "0.2.4";

    private final boolean verbose;

    public XenoDebugEntry(boolean verbose) {
        this.verbose = verbose;
    }

    public static void setVersion(String modVersion) {
        if (modVersion != null && !modVersion.isEmpty()) {
            version = modVersion;
        }
    }

    private static ChatFormatting getVersionColor() {
        String v = version;
        if (v.contains("-local")) {
            return ChatFormatting.RED;
        }
        return ChatFormatting.LIGHT_PURPLE;
    }

    @Override
    public void display(
            DebugScreenDisplayer displayer,
            @Nullable Level serverOrClientLevel,
            @Nullable LevelChunk clientChunk,
            @Nullable LevelChunk serverChunk
    ) {
        displayer.addToGroup(DEBUG_GROUP, String.format("%sXeno Renderer (%s)", getVersionColor(), version));

        int scheduled = XenoChunkExecutorService.getScheduledJobCount();
        int busy = XenoChunkExecutorService.getBusyThreadCount();
        int total = XenoChunkExecutorService.getTotalThreadCount();

        int unoccluded = XenoOcclusionCuller.INSTANCE.getUnoccludedCount();
        long avgNanos = XenoOcclusionCuller.INSTANCE.getAveragePassNanos();
        String cullState = XenoOcclusionCuller.INSTANCE.isWorkerBusy() ? "Running" : (avgNanos >= 0L ? "Done" : "Idle");
        String avgDuration = avgNanos < 0L ? "?" : String.format(Locale.ROOT, "%.1fms", avgNanos / 1_000_000.0);

        if (this.verbose) {
            int busyPct = total > 0 ? (busy * 100) / total : 0;
            displayer.addToGroup(DEBUG_GROUP, String.format(
                    Locale.ROOT,
                    "Chunk Builder: Schd=%02d | Busy=%02d (%03d%%) | Total=%02d",
                    scheduled, busy, busyPct, total
            ));
            displayer.addToGroup(DEBUG_GROUP, String.format(
                    Locale.ROOT,
                    "AC: %s (avg %s) | Sections: %d",
                    cullState, avgDuration, unoccluded
            ));
            int iboIndices = XenoChunkRenderer.SHARED_INDEX_BUFFER.indexCount();
            long iboMib = XenoChunkRenderer.SHARED_INDEX_BUFFER.byteSize() / (1024L * 1024L);
            displayer.addToGroup(DEBUG_GROUP, String.format(
                    Locale.ROOT,
                    "Shared IBO: %d indices (%d MiB)",
                    iboIndices, iboMib
            ));
        } else {
            displayer.addToGroup(DEBUG_GROUP, String.format(
                    Locale.ROOT,
                    "B: S%02d/B%02d/T%02d | Async Culling: %s (avg %s) | %d",
                    scheduled, busy, total, cullState, avgDuration, unoccluded
            ));
        }
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return true;
    }
}
