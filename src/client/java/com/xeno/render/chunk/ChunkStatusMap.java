/*
 * Original Codebase: Copyright XCollateral (VulkanMod)
 * Refactored Codebase: Copyright ExodusCoder9 (Xeno)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Refactored, Renamed and Optimized by ExodusCoder9.
 */
package com.xeno.render.chunk;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.world.level.ChunkPos;

public class ChunkStatusMap {
   public static final byte DATA_READY = 1;
   public static final byte LIGHT_READY = 2;
   public static final byte NEIGHBOURS_READY = 4;
   public static final byte CHUNK_READY = 3;
   public static final byte ALL_FLAGS = 7;
   public static ChunkStatusMap INSTANCE;
   private final Long2ByteOpenHashMap map;

   public static void createInstance(int renderDistance) {
      INSTANCE = new ChunkStatusMap(renderDistance);
   }

   public ChunkStatusMap(int renderDistance) {
      int diameter = renderDistance * 2 + 1;
      this.map = new Long2ByteOpenHashMap(diameter * diameter);
      this.map.defaultReturnValue((byte)0);
   }

   public void updateDistance(int renderDistance) {
      int diameter = renderDistance * 2 + 1;
      this.map.ensureCapacity(diameter * diameter);
   }

   public void setChunkStatus(int x, int z, byte flag) {
      long l = ChunkPos.pack(x, z);
      byte current = this.map.get(l);
      current = (byte)(current | flag);
      this.map.put(l, current);
      if ((current & 3) == 3) {
         this.updateNeighbours(x, z);
      }
   }

   public void resetChunkStatus(int x, int z, byte flag) {
      long l = ChunkPos.pack(x, z);
      byte current = this.map.get(l);
      current = (byte)(current & ~flag);
      this.map.put(l, current);
      this.updateNeighbours(x, z);
   }

   public void updateNeighbours(int x, int z) {
      for (int x1 = x - 1; x1 <= x + 1; x1++) {
         for (int z1 = z - 1; z1 <= z + 1; z1++) {
            if (this.checkNeighbours(x1, z1)) {
               this.map.put(ChunkPos.pack(x1, z1), (byte)7);
            } else {
               long l = ChunkPos.pack(x1, z1);
               byte current = this.map.get(l);
               byte n = (byte)(current & -5);
               if (current == 0) {
                  this.map.remove(l);
               } else if (current != n) {
                  this.map.put(l, n);
               }
            }
         }
      }
   }

   public boolean checkNeighbours(int x, int z) {
      byte flags = 3;

      for (int x1 = x - 1; x1 <= x + 1; x1++) {
         for (int z1 = z - 1; z1 <= z + 1; z1++) {
            flags &= this.map.get(ChunkPos.pack(x1, z1));
            if (flags != 3) {
               return false;
            }
         }
      }

      return true;
   }

   public boolean chunkRenderReady(int x, int z) {
      byte status = this.map.get(ChunkPos.pack(x, z));
      return status == 7;
   }

   public void reset() {
   }
}
