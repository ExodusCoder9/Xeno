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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.SectionPos;
import net.minecraft.client.renderer.chunk.SectionMesh;

public final class XenoRenderRegion {
	public static final int SECTION_COUNT = XenoWorldRenderManager.REGION_SECTIONS_XZ
		* XenoWorldRenderManager.REGION_SECTIONS_Y
		* XenoWorldRenderManager.REGION_SECTIONS_XZ;
	private static final int BITS_PER_WORD = 64;
	private static final int DIRTY_WORDS = SECTION_COUNT / BITS_PER_WORD;
	private final int regionX;
	private final int regionY;
	private final int regionZ;
	private final long[] dirtyBits = new long[DIRTY_WORDS];
	private final long[] playerDirtyBits = new long[DIRTY_WORDS];
	@SuppressWarnings("unchecked")
	private final AtomicReference<SectionMesh>[] meshes = new AtomicReference[SECTION_COUNT];
	private final long[] fadeDurationMillis = new long[SECTION_COUNT];
	private final long[] fadeStartMillis = new long[SECTION_COUNT];
	public final AtomicBoolean alive = new AtomicBoolean(true);
	private long lastSeenFrame;
	private int loadedChunkCount;

	public XenoRenderRegion(int regionX, int regionY, int regionZ) {
		this.regionX = regionX;
		this.regionY = regionY;
		this.regionZ = regionZ;
	}

	public static long key(int regionX, int regionY, int regionZ) {
		return SectionPos.asLong(regionX, regionY, regionZ);
	}

	public int regionX() {
		return this.regionX;
	}

	public int regionY() {
		return this.regionY;
	}

	public int regionZ() {
		return this.regionZ;
	}

	public int localIndexOf(int sectionX, int sectionY, int sectionZ) {
		int lx = sectionX - this.minSectionX();
		int ly = sectionY - this.baseSectionY();
		int lz = sectionZ - this.minSectionZ();
		if ((lx | ly | lz) < 0
			|| lx >= XenoWorldRenderManager.REGION_SECTIONS_XZ
			|| ly >= XenoWorldRenderManager.REGION_SECTIONS_Y
			|| lz >= XenoWorldRenderManager.REGION_SECTIONS_XZ
		) {
			return -1;
		}

		return ly * (XenoWorldRenderManager.REGION_SECTIONS_XZ * XenoWorldRenderManager.REGION_SECTIONS_XZ)
			+ lz * XenoWorldRenderManager.REGION_SECTIONS_XZ
			+ lx;
	}

	public int minSectionX() {
		return this.regionX * XenoWorldRenderManager.REGION_SECTIONS_XZ;
	}

	public int minSectionY() {
		return this.baseSectionY();
	}

	public int minSectionZ() {
		return this.regionZ * XenoWorldRenderManager.REGION_SECTIONS_XZ;
	}

	private int baseSectionY() {
		return this.regionY * XenoWorldRenderManager.REGION_SECTIONS_Y;
	}

	public boolean markSectionDirty(int localIndex, boolean playerChanged) {
		int word = localIndex >> 6;
		long bit = 1L << (localIndex & 63);
		boolean wasClean = (this.dirtyBits[word] & bit) == 0L;
		this.dirtyBits[word] |= bit;
		if (playerChanged) {
			this.playerDirtyBits[word] |= bit;
		}

		return wasClean;
	}

	public int pollNextDirtySection(boolean[] playerChangedOut) {
		for (int i = 0; i < DIRTY_WORDS; i++) {
			long word = this.dirtyBits[i];
			if (word != 0L) {
				int bit = Long.numberOfTrailingZeros(word);
				int index = (i << 6) + bit;
				playerChangedOut[0] = (this.playerDirtyBits[i] & (1L << bit)) != 0L;
				this.dirtyBits[i] &= ~(1L << bit);
				this.playerDirtyBits[i] &= ~(1L << bit);
				return index;
			}
		}

		return -1;
	}

	public boolean hasDirtySections() {
		for (long word : this.dirtyBits) {
			if (word != 0L) {
				return true;
			}
		}

		return false;
	}

	public void markAllSectionsDirty() {
		for (int i = 0; i < SECTION_COUNT; i++) {
			this.markSectionDirty(i, false);
		}
	}

	public AtomicReference<SectionMesh> meshSlot(int localIndex) {
		AtomicReference<SectionMesh> slot = this.meshes[localIndex];
		if (slot == null) {
			slot = new AtomicReference<>();
			this.meshes[localIndex] = slot;
		}

		return slot;
	}

	public void setFadeDuration(int localIndex, long durationMillis) {
		this.fadeDurationMillis[localIndex] = durationMillis;
	}

	public void noteMeshUploaded(int localIndex, long nowMillis) {
		this.fadeStartMillis[localIndex] = nowMillis;
	}

	public float visibilityAt(int localIndex, long nowMillis) {
		long duration = this.fadeDurationMillis[localIndex];
		long elapsed = nowMillis - this.fadeStartMillis[localIndex];
		return elapsed >= duration ? 1.0F : (float)elapsed / (float)duration;
	}

	public int loadedChunkCount() {
		return this.loadedChunkCount;
	}

	public void incrementLoadedChunkCount() {
		this.loadedChunkCount++;
	}

	public void decrementLoadedChunkCount() {
		if (this.loadedChunkCount > 0) {
			this.loadedChunkCount--;
		}
	}

	public boolean isUnused() {
		return this.loadedChunkCount == 0;
	}

	public void markSeen(long frame) {
		this.lastSeenFrame = frame;
	}

	public long lastSeenFrame() {
		return this.lastSeenFrame;
	}

	@Override
	public String toString() {
		return "XenoRenderRegion[" + this.regionX + ", " + this.regionY + ", " + this.regionZ + "]";
	}
}
