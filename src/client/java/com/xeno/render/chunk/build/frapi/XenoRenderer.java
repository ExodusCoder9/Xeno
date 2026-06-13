package com.xeno.render.chunk.build.frapi;

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
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.minecraft.client.color.block.BlockColors;
import com.xeno.render.chunk.build.frapi.mesh.EncodingFormat;
import com.xeno.render.chunk.build.frapi.mesh.MutableMeshImpl;
import com.xeno.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import com.xeno.render.chunk.build.frapi.render.AltModelBlockRendererImpl;

public class XenoRenderer implements Renderer {
   public static final XenoRenderer INSTANCE = new XenoRenderer();
   public static AltModelBlockRendererImpl modelBlockRenderer;

   private XenoRenderer() {
   }

   static XenoRenderer getOrCreateInstance() {
      return INSTANCE;
   }

   public QuadEmitter quadEmitter(final Consumer<? super MutableQuadView> consumer) {
      return new MutableQuadViewImpl() {
         {
            this.data = new int[EncodingFormat.TOTAL_STRIDE];
            this.clear();
         }

         @Override
         protected void emitDirectly() {
            consumer.accept(this);
         }
      };
   }

   public MutableMesh mutableMesh() {
      return new MutableMeshImpl();
   }

   public AltModelBlockRenderer altModelBlockRenderer(boolean ambientOcclusion, boolean cull, BlockColors blockColors) {
      if (modelBlockRenderer == null) {
         modelBlockRenderer = new AltModelBlockRendererImpl(ambientOcclusion, cull, blockColors);
      }

      modelBlockRenderer.setup(ambientOcclusion, cull);
      return modelBlockRenderer;
   }
}
