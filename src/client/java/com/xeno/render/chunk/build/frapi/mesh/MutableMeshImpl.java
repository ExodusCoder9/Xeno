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


import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

public class MutableMeshImpl extends MeshViewImpl implements MutableMesh {
   private final MutableQuadViewImpl emitter = new MutableQuadViewImpl() {
      @Override
      protected void emitDirectly() {
         this.computeGeometry();
         MutableMeshImpl.this.limit = MutableMeshImpl.this.limit + EncodingFormat.TOTAL_STRIDE;
         MutableMeshImpl.this.ensureCapacity(EncodingFormat.TOTAL_STRIDE);
         this.baseIndex = MutableMeshImpl.this.limit;
      }
   };

   public MutableMeshImpl() {
      this.data = new int[8 * EncodingFormat.TOTAL_STRIDE];
      this.limit = 0;
      this.ensureCapacity(EncodingFormat.TOTAL_STRIDE);
      this.emitter.data = this.data;
      this.emitter.baseIndex = this.limit;
      this.emitter.clear();
   }

   private void ensureCapacity(int stride) {
      if (stride > this.data.length - this.limit) {
         int[] bigger = new int[this.data.length * 2];
         System.arraycopy(this.data, 0, bigger, 0, this.limit);
         this.data = bigger;
         this.emitter.data = this.data;
      }
   }

   public QuadEmitter emitter() {
      this.emitter.clear();
      return this.emitter;
   }

   public void forEachMutable(Consumer<? super MutableQuadView> action) {
      this.forEach(action, this.emitter);
      this.emitter.data = this.data;
      this.emitter.baseIndex = this.limit;
   }

   public Mesh immutableCopy() {
      int[] packed = new int[this.limit];
      System.arraycopy(this.data, 0, packed, 0, this.limit);
      return new MeshImpl(packed);
   }

   public void clear() {
      this.limit = 0;
      this.emitter.baseIndex = this.limit;
      this.emitter.clear();
   }
}
