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

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xeno.client.common.memory.MemoryAccess;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jspecify.annotations.Nullable;

public final class XenoSharedQuadIndexBuffer {
	private static final int INDICES_PER_QUAD = 6;
	private static final int VERTICES_PER_QUAD = 4;
	private static final int INITIAL_INDEX_COUNT = 65536;

	private @Nullable GpuBuffer buffer;
	private IndexType type = IndexType.SHORT;
	private int indexCount;

	public void ensureCapacity(int requiredIndexCount) {
		if (requiredIndexCount <= this.indexCount && this.buffer != null) {
			return;
		}

		int newIndexCount = Math.max(requiredIndexCount, Math.max(this.indexCount * 2, INITIAL_INDEX_COUNT));
		newIndexCount = (newIndexCount + INDICES_PER_QUAD - 1) / INDICES_PER_QUAD * INDICES_PER_QUAD;
		int quadCount = newIndexCount / INDICES_PER_QUAD;
		int vertexCount = quadCount * VERTICES_PER_QUAD;
		IndexType newType = IndexType.least(vertexCount);
		int byteSize = roundUp4((long) newIndexCount * newType.bytes);

		ByteBuffer data = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder());
		long address = MemoryAccess.getAddress(data);
		if (newType == IndexType.SHORT) {
			this.fillQuadsShort(address, quadCount);
		} else {
			this.fillQuadsInt(address, quadCount);
		}

		GpuDevice device = RenderSystem.getDevice();
		if (this.buffer != null) {
			this.buffer.close();
		}

		this.buffer = device.createBuffer(() -> "Xeno shared quad index buffer", 64, data);
		this.type = newType;
		this.indexCount = newIndexCount;
	}

	private void fillQuadsShort(long address, int quadCount) {
		for (int i = 0; i < quadCount; i++) {
			int base = i * VERTICES_PER_QUAD;
			long offset = address + (long) i * 12L;
			MemoryAccess.putLong(offset, MemoryAccess.packShorts((short) base, (short) (base + 1), (short) (base + 2), (short) (base + 2)));
			MemoryAccess.putInt(offset + 8L, MemoryAccess.packShorts((short) (base + 3), (short) base));
		}
	}

	private void fillQuadsInt(long address, int quadCount) {
		for (int i = 0; i < quadCount; i++) {
			int base = i * VERTICES_PER_QUAD;
			long offset = address + (long) i * 24L;
			MemoryAccess.putLong(offset, MemoryAccess.packInts(base, base + 1));
			MemoryAccess.putLong(offset + 8L, MemoryAccess.packInts(base + 2, base + 2));
			MemoryAccess.putLong(offset + 16L, MemoryAccess.packInts(base + 3, base));
		}
	}

	private static int roundUp4(long bytes) {
		return (int) ((bytes + 3L) & ~3L);
	}

	public boolean hasCapacity(int indexCount) {
		return indexCount <= this.indexCount && this.buffer != null;
	}

	public @Nullable GpuBuffer buffer() {
		return this.buffer;
	}

	public IndexType type() {
		return this.type;
	}

	public int indexCount() {
		return this.indexCount;
	}
}
