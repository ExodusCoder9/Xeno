package com.xeno.render.chunk.build.frapi.mesh;

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


import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import org.jetbrains.annotations.Range;

public class MeshViewImpl implements MeshView {
   private static final ThreadLocal<ObjectArrayList<QuadViewImpl>> CURSOR_POOLS = ThreadLocal.withInitial(ObjectArrayList::new);
   int[] data;
   int limit;

   MeshViewImpl() {
   }

   public @Range(from = 0L, to = 2147483647L) int size() {
      return this.limit / EncodingFormat.TOTAL_STRIDE;
   }

   public void forEach(Consumer<? super QuadView> action) {
      ObjectArrayList<QuadViewImpl> pool = CURSOR_POOLS.get();
      QuadViewImpl cursor;
      if (pool.isEmpty()) {
         cursor = new QuadViewImpl();
      } else {
         cursor = (QuadViewImpl)pool.pop();
      }

      this.forEach(action, cursor);
      pool.push(cursor);
   }

   <C extends QuadViewImpl> void forEach(Consumer<? super C> action, C cursor) {
      int limit = this.limit;
      int index = 0;

      for (cursor.data = this.data; index < limit; index += EncodingFormat.TOTAL_STRIDE) {
         cursor.baseIndex = index;
         cursor.load();
         action.accept(cursor);
      }

      cursor.data = null;
   }

   public void outputTo(QuadEmitter emitter) {
      MutableQuadViewImpl e = (MutableQuadViewImpl)emitter;
      int[] data = this.data;
      int limit = this.limit;

      for (int index = 0; index < limit; index += EncodingFormat.TOTAL_STRIDE) {
         System.arraycopy(data, index, e.data, e.baseIndex, EncodingFormat.TOTAL_STRIDE);
         e.load();
         e.transformAndEmit();
      }

      e.clear();
   }
}
