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

import com.xeno.client.common.memory.MemoryAccess;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.core.SectionPos;

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
	private final long[] pendingBits = new long[DIRTY_WORDS];
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
		for (int i = 0; i < SECTION_COUNT; i++) {
			this.meshes[i] = new AtomicReference<>();
		}
	}

	public static long key(int regionX, int regionY, int regionZ) {
		return SectionPos.asLong(regionX, regionY, regionZ);
	}

	public int regionX() {
		return this.regionX;
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

	private static final long LOADED_CHUNK_COUNT_OFFSET = MemoryAccess.fieldOffset(XenoRenderRegion.class, "loadedChunkCount");

	public void markSectionDirty(int localIndex, boolean playerChanged) {
		int word = localIndex >> 6;
		long bit = 1L << (localIndex & 63);
		MemoryAccess.fetchAndBitwiseOrLong(this.dirtyBits, word, bit);
		if (playerChanged) {
			MemoryAccess.fetchAndBitwiseOrLong(this.playerDirtyBits, word, bit);
		}
	}

	public boolean markPending(int localIndex) {
		int word = localIndex >> 6;
		long bit = 1L << (localIndex & 63);
		long offset = MemoryAccess.LONG_ARRAY_BASE + ((long) word * MemoryAccess.LONG_ARRAY_INDEX_SCALE);
		long currentPending;
		do {
			currentPending = MemoryAccess.getLongVolatile(this.pendingBits, word);
			if ((currentPending & bit) != 0L) {
				return false;
			}
		} while (!MemoryAccess.compareAndSwapLong(this.pendingBits, offset, currentPending, currentPending | bit));

		MemoryAccess.fetchAndBitwiseAndLong(this.dirtyBits, word, ~bit);
		MemoryAccess.fetchAndBitwiseAndLong(this.playerDirtyBits, word, ~bit);
		return true;
	}

	public void clearPending(int localIndex) {
		int word = localIndex >> 6;
		long bit = 1L << (localIndex & 63);
		MemoryAccess.fetchAndBitwiseAndLong(this.pendingBits, word, ~bit);
	}

	public boolean isPending(int localIndex) {
		int word = localIndex >> 6;
		long bit = 1L << (localIndex & 63);
		return (MemoryAccess.getLongVolatile(this.pendingBits, word) & bit) != 0L;
	}

	public boolean hasDirtySections() {
		for (int i = 0; i < DIRTY_WORDS; i++) {
			long dirty = MemoryAccess.getLongVolatile(this.dirtyBits, i);
			long pending = MemoryAccess.getLongVolatile(this.pendingBits, i);
			if ((dirty & ~pending) != 0L) {
				return true;
			}
		}
		return false;
	}

	public void peekDirtySections(it.unimi.dsi.fastutil.longs.LongList out, int limit) {
		int collected = 0;
		for (int i = 0; i < DIRTY_WORDS && collected < limit; i++) {
			long dirty = MemoryAccess.getLongVolatile(this.dirtyBits, i);
			long pending = MemoryAccess.getLongVolatile(this.pendingBits, i);
			long playerDirty = MemoryAccess.getLongVolatile(this.playerDirtyBits, i);
			long word = dirty & ~pending;
			while (word != 0L && collected < limit) {
				int bit = Long.numberOfTrailingZeros(word);
				int index = (i << 6) + bit;
				out.add((long) index << 1 | ((playerDirty & (1L << bit)) != 0L ? 1L : 0L));
				word &= ~(1L << bit);
				collected++;
			}
		}
	}

	public AtomicReference<SectionMesh> meshSlot(int localIndex) {
		return this.meshes[localIndex];
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

	public void incrementLoadedChunkCount() {
		MemoryAccess.getAndAddInt(this, LOADED_CHUNK_COUNT_OFFSET, 1);
	}

	public void decrementLoadedChunkCount() {
		int current;
		do {
			current = MemoryAccess.getInt(this, LOADED_CHUNK_COUNT_OFFSET);
			if (current <= 0) {
				break;
			}
		} while (!MemoryAccess.compareAndSwapInt(this, LOADED_CHUNK_COUNT_OFFSET, current, current - 1));
	}

	public boolean isUnused() {
		return MemoryAccess.getInt(this, LOADED_CHUNK_COUNT_OFFSET) == 0;
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
