package com.xeno.render.chunk.build.renderer;

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


import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.phys.Vec3;
import com.xeno.Initializer;
import com.xeno.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import com.xeno.render.chunk.build.light.LightPipeline;
import com.xeno.render.chunk.build.light.data.QuadLightData;
import com.xeno.render.chunk.build.thread.BuilderResources;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.model.quad.ModelQuadView;
import com.xeno.render.model.quad.QuadUtils;
import com.xeno.render.vertex.TerrainBufferBuilder;
import com.xeno.render.vertex.TerrainBuilder;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.util.ColorUtil;
import org.joml.Vector3f;

public class BlockRenderer extends AbstractBlockRenderContext {
   private Vector3f pos;
   private BuilderResources resources;
   BlockStateModelSet blockModels;
   private TerrainBuilder terrainBuilder;
   final boolean backFaceCulling;
   private TerrainRenderType renderType;

   public void setResources(BuilderResources resources) {
      this.resources = resources;
   }

   public BlockRenderer(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
      this.backFaceCulling = Initializer.CONFIG.backFaceCulling;
      this.setupLightPipelines(flatLightPipeline, smoothLightPipeline);
      this.random = new SingleThreadedRandomSource(42L);
      this.blockModels = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
   }

   public void renderBlock(BlockState blockState, BlockPos blockPos, Vector3f pos) {
      this.pos = pos;
      this.blockPos = blockPos;
      this.blockState = blockState;
      this.random.setSeed(blockState.getSeed(blockPos));
      this.resetTintCache();
      BlockStateModel model = this.blockModels.get(blockState);
      this.prepareForBlock(blockState, blockPos, true);
      BlockAndTintGetter renderRegion = this.renderRegion;
      Vec3 offset = blockState.getOffset(blockPos);
      pos.add((float)offset.x, (float)offset.y, (float)offset.z);
      model.emitQuads(this.getEmitter(), renderRegion, blockPos, blockState, this.random, this);
   }

   @Override
   protected void endRenderQuad(MutableQuadViewImpl quad) {
      TriState aoMode = quad.ambientOcclusion();
      boolean ao = this.useAO && (aoMode == TriState.TRUE || aoMode == TriState.DEFAULT && this.defaultAO);
      boolean emissive = quad.emissive();
      boolean vanillaShade = quad.shadeMode() == ShadeMode.VANILLA;
      ChunkSectionLayer layer = quad.chunkLayer();
      TerrainRenderType rawType = TerrainRenderType.get(layer);
      this.renderType = TerrainRenderType.getRemapped(rawType);
      TerrainBuilder terrainBuilder = this.getBufferBuilder(layer);
      terrainBuilder.setBlockAttributes(this.blockState);
      this.tintQuad(quad);
      this.shadeQuad(quad, ao, emissive, vanillaShade);
      this.bufferQuad(terrainBuilder, this.pos, quad, this.quadLightData);
   }

   private TerrainBuilder getBufferBuilder(ChunkSectionLayer layer) {
      if (layer == null) {
         return this.terrainBuilder;
      }

      // this.renderType is already remapped in endRenderQuad before this call
      TerrainBuilder bufferBuilder = this.resources.builderPack.builder(this.renderType);
      bufferBuilder.setBlockAttributes(this.blockState);
      return bufferBuilder;
   }

   public void bufferQuad(TerrainBuilder terrainBuilder, Vector3f pos, ModelQuadView quad, QuadLightData quadLightData) {
      QuadFacing quadFacing = quad.getQuadFacing();
      if (this.renderType == TerrainRenderType.TRANSLUCENT || !this.backFaceCulling) {
         quadFacing = QuadFacing.UNDEFINED;
      }

      TerrainBufferBuilder bufferBuilder = terrainBuilder.getBufferBuilder(quadFacing.ordinal());
      int packedNormal = quad.getNormal();
      float[] brightnessArr = quadLightData.br;
      int[] lights = quadLightData.lm;
      int idx0 = QuadUtils.getIterationStartIdx(brightnessArr, lights);
      bufferBuilder.ensureCapacity();
      // Cache pos components to avoid repeated getter calls across all 4 vertices
      float posX = pos.x();
      float posY = pos.y();
      float posZ = pos.z();
      float x0 = posX + quad.getX(idx0);
      float y0 = posY + quad.getY(idx0);
      float z0 = posZ + quad.getZ(idx0);
      int quadColor0 = quad.getColor(idx0);
      int color0 = ColorUtil.ARGB.toRGBA(quadColor0);
      int light0 = lights[idx0];
      float u0 = quad.getU(idx0);
      float v0 = quad.getV(idx0);
      bufferBuilder.vertex(x0, y0, z0, color0, u0, v0, light0, packedNormal);

      int idx1 = idx0 + 1 & 3;
      float x1 = posX + quad.getX(idx1);
      float y1 = posY + quad.getY(idx1);
      float z1 = posZ + quad.getZ(idx1);
      int quadColor1 = quad.getColor(idx1);
      int color1 = ColorUtil.ARGB.toRGBA(quadColor1);
      int light1 = lights[idx1];
      float u1 = quad.getU(idx1);
      float v1 = quad.getV(idx1);
      bufferBuilder.vertex(x1, y1, z1, color1, u1, v1, light1, packedNormal);

      int idx2 = idx1 + 1 & 3;
      float x2 = posX + quad.getX(idx2);
      float y2 = posY + quad.getY(idx2);
      float z2 = posZ + quad.getZ(idx2);
      int quadColor2 = quad.getColor(idx2);
      int color2 = ColorUtil.ARGB.toRGBA(quadColor2);
      int light2 = lights[idx2];
      float u2 = quad.getU(idx2);
      float v2 = quad.getV(idx2);
      bufferBuilder.vertex(x2, y2, z2, color2, u2, v2, light2, packedNormal);

      int idx3 = idx2 + 1 & 3;
      float x3 = posX + quad.getX(idx3);
      float y3 = posY + quad.getY(idx3);
      float z3 = posZ + quad.getZ(idx3);
      int quadColor3 = quad.getColor(idx3);
      int color3 = ColorUtil.ARGB.toRGBA(quadColor3);
      int light3 = lights[idx3];
      float u3 = quad.getU(idx3);
      float v3 = quad.getV(idx3);
      bufferBuilder.vertex(x3, y3, z3, color3, u3, v3, light3, packedNormal);
   }
}
