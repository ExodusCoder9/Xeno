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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.common.render.chunk;

import com.google.common.collect.Lists;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <p>This implementation snapshots the pending task set into a pre-decided schedule and
 * then polls from that schedule . Ordering follows the important/background split: <em>important</em> tasks (initial compiles, transparency
 * resorts, and recompile within a small radius of the camera,
 * {@code NEARBY_RECOMPILE_DISTANCE}) are scheduled ahead of <em>background</em> tasks (distant
 * recompiles), each tier ordered nearest-first. This supersedes vanillas recompile quota: all
 * initial compiles already outrank distant recompiles, and near recompile remain within the
 * important tier so nearby block updates stay responsive.
 *
 * <p>The schedule is rebuilt only when the camera moves to a different section, when the world
 * generation epoch advances, or when the snapshot finishes. Canceled tasks are pruned lazily
 * during a rebuild.
 *
 * <p>This class is thread-safe in the same way the vanilla queue is: all public entry points
 * are {@code synchronized}, because multiple {@code SectionRenderDispatcher} worker chains
 * (each resubmitted onto the background executor between tasks) poll from it concurrently.
 */
public class XenoSectionTaskQueue extends SectionTaskDynamicQueue {
	/** Squared distance (16 blocks, below which recompiles are important. */
	private static final double NEARBY_RECOMPILE_DISTANCE = 256.0;

	private final List<SectionRenderDispatcher.RenderSection.SectionTask> pending = Lists.newArrayList();

	private SectionRenderDispatcher.RenderSection.SectionTask[] order = new SectionRenderDispatcher.RenderSection.SectionTask[0];
	private int cursor;
	private long generation;
	private long cachedCameraSection;
	private long cachedGeneration = Long.MIN_VALUE;

	public XenoSectionTaskQueue() {
	}

	@Override
	public synchronized SectionRenderDispatcher.RenderSection.SectionTask poll(@NonNull Vec3 cameraPos) {
		if (this.cursor >= this.order.length && this.pending.size() > 0) {
			this.rebuild(cameraPos);
		}

		if (this.cursor >= this.order.length) {
			this.resetCaches();
			return null;
		}

		long cameraSection = cameraSectionKey(cameraPos);
		if (this.generation != this.cachedGeneration || cameraSection != this.cachedCameraSection) {
			this.rebuild(cameraPos);
		}

		SectionRenderDispatcher.RenderSection.SectionTask task = this.order[this.cursor++];
		this.pending.remove(task);
		return task;
	}

	@Override
	public synchronized void add(SectionRenderDispatcher.RenderSection.@NonNull SectionTask task) {
		this.pending.add(task);
		this.generation++;
	}

	@Override
	public synchronized int size() {
		return this.pending.size();
	}

	@Override
	public synchronized void clear() {
		this.pending.forEach(SectionRenderDispatcher.RenderSection.SectionTask::cancel);
		this.pending.clear();
		this.order = new SectionRenderDispatcher.RenderSection.SectionTask[0];
		this.cursor = 0;
		this.resetCaches();
	}

	private void rebuild(Vec3 cameraPos) {
		if (this.pending.size() == 0) {
			this.resetCaches();
			return;
		}

		int size = this.pending.size();
		SectionRenderDispatcher.RenderSection.SectionTask[] important = new SectionRenderDispatcher.RenderSection.SectionTask[size];
		SectionRenderDispatcher.RenderSection.SectionTask[] background = new SectionRenderDispatcher.RenderSection.SectionTask[size];
		int impCount = 0;
		int bgCount = 0;

		for (int i = 0; i < size; i++) {
			SectionRenderDispatcher.RenderSection.SectionTask task = this.pending.get(i);
			if (!task.isCancelled.get()) {
				if (isImportant(task, cameraPos)) {
					important[impCount++] = task;
				} else {
					background[bgCount++] = task;
				}
			}
		}

		this.pending.clear();
		Comparator<SectionRenderDispatcher.RenderSection.SectionTask> byDistance = byDistanceTo(cameraPos);
		
		java.util.Arrays.sort(important, 0, impCount, byDistance);
		java.util.Arrays.sort(background, 0, bgCount, byDistance);

		SectionRenderDispatcher.RenderSection.SectionTask[] rebuilt = new SectionRenderDispatcher.RenderSection.SectionTask[impCount + bgCount];
		System.arraycopy(important, 0, rebuilt, 0, impCount);
		System.arraycopy(background, 0, rebuilt, impCount, bgCount);

		for (int i = 0; i < rebuilt.length; i++) {
			this.pending.add(rebuilt[i]);
		}
		
		this.order = rebuilt;
		this.cursor = 0;
		this.cachedCameraSection = cameraSectionKey(cameraPos);
		this.cachedGeneration = this.generation;
	}

	private static boolean isImportant(SectionRenderDispatcher.RenderSection.SectionTask task, Vec3 cameraPos) {
		return !task.isRecompile() || task.getRenderOrigin().distToCenterSqr(cameraPos) < NEARBY_RECOMPILE_DISTANCE;
	}

	private static Comparator<SectionRenderDispatcher.RenderSection.SectionTask> byDistanceTo(Vec3 cameraPos) {
		return Comparator.comparingDouble(task -> task.getRenderOrigin().distToCenterSqr(cameraPos));
	}

	private void resetCaches() {
		this.cachedCameraSection = 0L;
		this.cachedGeneration = Long.MIN_VALUE;
	}

	private static long cameraSectionKey(Vec3 cameraPos) {
		return SectionPos.asLong(
			SectionPos.blockToSectionCoord(cameraPos.x),
			SectionPos.blockToSectionCoord(cameraPos.y),
			SectionPos.blockToSectionCoord(cameraPos.z)
		);
	}
}
