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

import java.util.List;
public final class XenoSectionTaskQueue extends SectionTaskDynamicQueue {
	private static final double NEARBY_RECOMPILE_DISTANCE = 256.0;
	private static final SectionRenderDispatcher.RenderSection.SectionTask[] EMPTY_TASKS = new SectionRenderDispatcher.RenderSection.SectionTask[0];

	private final List<SectionRenderDispatcher.RenderSection.SectionTask> pending = Lists.newArrayList();
	private final List<SectionRenderDispatcher.RenderSection.SectionTask> staging = Lists.newArrayList();
	private SectionRenderDispatcher.RenderSection.SectionTask[] order = EMPTY_TASKS;
	private int cursor;
	private long cachedCameraSection = Long.MIN_VALUE;
	private boolean hasPendingImportant;

	public XenoSectionTaskQueue() {
	}

	@Override
	public synchronized SectionRenderDispatcher.RenderSection.SectionTask poll(@NonNull Vec3 cameraPos) {
		long cameraSection = cameraSectionKey(cameraPos);
		boolean cameraMoved = cameraSection != this.cachedCameraSection;

		if (cameraMoved || this.hasPendingImportant || this.cursor >= this.order.length) {
			if (cameraMoved || this.hasPendingImportant || !this.pending.isEmpty()) {
				this.rebuild(cameraPos, cameraSection);
			}
		}

		while (this.cursor < this.order.length) {
			SectionRenderDispatcher.RenderSection.SectionTask task = this.order[this.cursor++];
			if (!task.isCancelled.get()) {
				return task;
			}
		}

		if (!this.pending.isEmpty()) {
			this.rebuild(cameraPos, cameraSection);
			while (this.cursor < this.order.length) {
				SectionRenderDispatcher.RenderSection.SectionTask task = this.order[this.cursor++];
				if (!task.isCancelled.get()) {
					return task;
				}
			}
		}

		return null;
	}

	@Override
	public synchronized void add(SectionRenderDispatcher.RenderSection.@NonNull SectionTask task) {
		this.pending.add(task);
		if (!task.isRecompile()) {
			this.hasPendingImportant = true;
		}
	}

	@Override
	public synchronized int size() {
		return Math.max(0, this.order.length - this.cursor) + this.pending.size();
	}

	@Override
	public synchronized void clear() {
		for (int i = this.cursor; i < this.order.length; i++) {
			this.order[i].cancel();
		}
		for (SectionRenderDispatcher.RenderSection.SectionTask task : this.pending) {
			task.cancel();
		}
		this.pending.clear();
		this.staging.clear();
		this.order = EMPTY_TASKS;
		this.cursor = 0;
		this.cachedCameraSection = Long.MIN_VALUE;
		this.hasPendingImportant = false;
	}

	private void rebuild(Vec3 cameraPos, long cameraSection) {
		this.staging.clear();

		for (int i = this.cursor; i < this.order.length; i++) {
			SectionRenderDispatcher.RenderSection.SectionTask task = this.order[i];
			if (!task.isCancelled.get()) {
				this.staging.add(task);
			}
		}

		for (SectionRenderDispatcher.RenderSection.SectionTask task : this.pending) {
			if (!task.isCancelled.get()) {
				this.staging.add(task);
			}
		}
		this.pending.clear();

		if (this.staging.isEmpty()) {
			this.order = EMPTY_TASKS;
			this.cursor = 0;
			this.cachedCameraSection = cameraSection;
			this.hasPendingImportant = false;
			return;
		}

		double camX = cameraPos.x;
		double camY = cameraPos.y;
		double camZ = cameraPos.z;

		this.staging.sort((taskA, taskB) -> {
			boolean impA = isImportant(taskA, camX, camY, camZ);
			boolean impB = isImportant(taskB, camX, camY, camZ);
			if (impA != impB) {
				return impA ? -1 : 1;
			}
			double distA = taskA.getRenderOrigin().distToCenterSqr(camX, camY, camZ);
			double distB = taskB.getRenderOrigin().distToCenterSqr(camX, camY, camZ);
			return Double.compare(distA, distB);
		});

		this.order = this.staging.toArray(new SectionRenderDispatcher.RenderSection.SectionTask[0]);
		this.cursor = 0;
		this.cachedCameraSection = cameraSection;
		this.hasPendingImportant = false;
		this.staging.clear();
	}

	private static boolean isImportant(SectionRenderDispatcher.RenderSection.SectionTask task, double camX, double camY, double camZ) {
		return !task.isRecompile() || task.getRenderOrigin().distToCenterSqr(camX, camY, camZ) < NEARBY_RECOMPILE_DISTANCE;
	}

	private static long cameraSectionKey(Vec3 cameraPos) {
		return SectionPos.asLong(
				SectionPos.blockToSectionCoord(cameraPos.x),
				SectionPos.blockToSectionCoord(cameraPos.y),
				SectionPos.blockToSectionCoord(cameraPos.z)
		);
	}
}
