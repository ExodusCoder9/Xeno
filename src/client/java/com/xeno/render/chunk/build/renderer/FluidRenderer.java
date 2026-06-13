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


import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering.DefaultRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer.Output;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.xeno.render.chunk.build.light.LightPipeline;
import com.xeno.render.chunk.build.light.data.QuadLightData;
import com.xeno.render.chunk.build.thread.BuilderResources;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.chunk.util.Util;
import com.xeno.render.model.quad.ModelQuad;
import com.xeno.render.model.quad.QuadUtils;
import com.xeno.render.vertex.TerrainBufferBuilder;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.util.ColorUtil;

public class FluidRenderer implements DefaultRenderer {
   private static final float MAX_FLUID_HEIGHT = 0.8888889F;
   private final MutableBlockPos mBlockPos = new MutableBlockPos();
   private final ModelQuad modelQuad = new ModelQuad();
   BuilderResources resources;
   FluidStateModelSet fluidModels;
   boolean ambientOcclusion;
   private final LightPipeline smoothLightPipeline;
   private final LightPipeline flatLightPipeline;
   private final int[] quadColors = new int[4];
   private final float[] avgHeightFs = new float[2];

   public FluidRenderer(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
      this.smoothLightPipeline = smoothLightPipeline;
      this.flatLightPipeline = flatLightPipeline;
      this.fluidModels = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
      this.ambientOcclusion = Minecraft.getInstance().gameRenderer.getGameRenderState().optionsRenderState.ambientOcclusion;
   }

   public void setResources(BuilderResources resources) {
      this.resources = resources;
   }

   public void renderLiquid(BlockState blockState, FluidState fluidState, BlockPos blockPos) {
      FluidRenderHandler handler = FluidRenderingRegistry.get(fluidState.getType());
      if (handler == null) {
         boolean isLava = fluidState.is(FluidTags.LAVA);
         handler = FluidRenderingRegistry.get(isLava ? Fluids.LAVA : Fluids.WATER);
      }

      FluidRendering.render(null, handler, this.resources.getRegion(), blockPos, null, blockState, fluidState, this);
   }

   private boolean isFaceOccludedByState(BlockGetter blockGetter, float h, Direction direction, BlockPos blockPos, BlockState blockState) {
      if (blockState.canOcclude()) {
         VoxelShape occlusionShape = blockState.getOcclusionShape();
         if (occlusionShape == Shapes.block()) {
            return direction != Direction.UP;
         }

         if (occlusionShape.isEmpty()) {
            return false;
         }

         VoxelShape voxelShape = Shapes.box(0.0, 0.0, 0.0, 1.0, h, 1.0);
         return Shapes.blockOccludes(voxelShape, occlusionShape, direction);
      } else {
         return false;
      }
   }

   public static boolean shouldRenderFace(
      BlockAndTintGetter blockAndTintGetter, BlockPos blockPos, FluidState fluidState, BlockState blockState, Direction direction, BlockState adjBlockState
   ) {
      if (adjBlockState.getFluidState().getType().isSame(fluidState.getType())) {
         return false;
      } else {
         return blockState.canOcclude() ? !blockState.isFaceSturdy(blockAndTintGetter, blockPos, direction) : true;
      }
   }

   public BlockState getAdjBlockState(BlockAndTintGetter blockAndTintGetter, int x, int y, int z, Direction dir) {
      this.mBlockPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
      return blockAndTintGetter.getBlockState(this.mBlockPos);
   }

   public void render(
      net.minecraft.client.renderer.block.FluidRenderer fluidRenderer,
      FluidRenderHandler handler,
      BlockAndTintGetter level,
      BlockPos pos,
      Output output,
      BlockState blockState,
      FluidState fluidState
   ) {
      this.render(blockState, fluidState, pos);
   }

   public void render(BlockState blockState, FluidState fluidState, BlockPos pos) {
      BlockAndTintGetter region = this.resources.getRegion();
      FluidModel model = this.fluidModels.get(fluidState);
      int color = model.tintSource() != null ? model.tintSource().colorInWorld(blockState, region, pos) : -1;
      TerrainRenderType renderType = TerrainRenderType.get(model.layer());
      renderType = TerrainRenderType.getRemapped(renderType);
      TerrainBufferBuilder bufferBuilder = this.resources.builderPack.builder(renderType).getBufferBuilder(QuadFacing.UNDEFINED.ordinal());
      CardinalLighting cardinalLighting = region.cardinalLighting();
      float r = ColorUtil.ARGB.unpackR(color);
      float g = ColorUtil.ARGB.unpackG(color);
      float b = ColorUtil.ARGB.unpackB(color);
      int posX = pos.getX();
      int posY = pos.getY();
      int posZ = pos.getZ();
      boolean useAO = blockState.getLightEmission() == 0 && this.ambientOcclusion;
      LightPipeline lightPipeline = useAO ? this.smoothLightPipeline : this.flatLightPipeline;
      BlockState downState = this.getAdjBlockState(region, posX, posY, posZ, Direction.DOWN);
      BlockState upState = this.getAdjBlockState(region, posX, posY, posZ, Direction.UP);
      BlockState northState = this.getAdjBlockState(region, posX, posY, posZ, Direction.NORTH);
      BlockState southState = this.getAdjBlockState(region, posX, posY, posZ, Direction.SOUTH);
      BlockState westState = this.getAdjBlockState(region, posX, posY, posZ, Direction.WEST);
      BlockState eastState = this.getAdjBlockState(region, posX, posY, posZ, Direction.EAST);
      boolean rUf = shouldRenderFace(region, pos, fluidState, blockState, Direction.UP, upState);
      boolean rDf = shouldRenderFace(region, pos, fluidState, blockState, Direction.DOWN, downState)
         && !this.isFaceOccludedByState(region, 0.8888889F, Direction.DOWN, pos, downState);
      boolean rNf = shouldRenderFace(region, pos, fluidState, blockState, Direction.NORTH, northState);
      boolean rSf = shouldRenderFace(region, pos, fluidState, blockState, Direction.SOUTH, southState);
      boolean rWf = shouldRenderFace(region, pos, fluidState, blockState, Direction.WEST, westState);
      boolean rEf = shouldRenderFace(region, pos, fluidState, blockState, Direction.EAST, eastState);
      if (rUf || rDf || rEf || rWf || rNf || rSf) {
         float brightnessUp = cardinalLighting.up();
         Fluid fluid = fluidState.getType();
         float height = this.getHeight(region, fluid, pos, blockState);
         float neHeight;
         float nwHeight;
         float seHeight;
         float swHeight;
         if (height >= 1.0F) {
            neHeight = 1.0F;
            nwHeight = 1.0F;
            seHeight = 1.0F;
            swHeight = 1.0F;
         } else {
            float s = this.getHeight(region, fluid, this.mBlockPos.set(pos).offset(Direction.NORTH.getUnitVec3i()), northState);
            float t = this.getHeight(region, fluid, this.mBlockPos.set(pos).offset(Direction.SOUTH.getUnitVec3i()), southState);
            float u = this.getHeight(region, fluid, this.mBlockPos.set(pos).offset(Direction.EAST.getUnitVec3i()), eastState);
            float v = this.getHeight(region, fluid, this.mBlockPos.set(pos).offset(Direction.WEST.getUnitVec3i()), westState);
            neHeight = this.calculateAverageHeight(
               region, fluid, height, s, u, this.mBlockPos.set(pos).offset(Direction.NORTH.getUnitVec3i()).offset(Direction.EAST.getUnitVec3i())
            );
            nwHeight = this.calculateAverageHeight(
               region, fluid, height, s, v, this.mBlockPos.set(pos).offset(Direction.NORTH.getUnitVec3i()).offset(Direction.WEST.getUnitVec3i())
            );
            seHeight = this.calculateAverageHeight(
               region, fluid, height, t, u, this.mBlockPos.set(pos).offset(Direction.SOUTH.getUnitVec3i()).offset(Direction.EAST.getUnitVec3i())
            );
            swHeight = this.calculateAverageHeight(
               region, fluid, height, t, v, this.mBlockPos.set(pos).offset(Direction.SOUTH.getUnitVec3i()).offset(Direction.WEST.getUnitVec3i())
            );
         }

         float x0 = posX & 15;
         float y0 = posY & 15;
         float z0 = posZ & 15;
         float y = rDf ? 0.001F : 0.0F;
         this.modelQuad.setFlags(2); // parallel-to-block-face for optimized lighting
         if (rUf && !this.isFaceOccludedByState(region, Math.min(Math.min(nwHeight, swHeight), Math.min(seHeight, neHeight)), Direction.UP, pos, upState)) {
            nwHeight -= 0.001F;
            swHeight -= 0.001F;
            seHeight -= 0.001F;
            neHeight -= 0.001F;
            Vec3 vec3 = fluidState.isSource() ? Vec3.ZERO : fluidState.getFlow(region, pos);
            float u0;
            float u1;
            float u2;
            float u3;
            float v0;
            float v1;
            float v2;
            float v3;
            if (vec3.x == 0.0 && vec3.z == 0.0) {
               TextureAtlasSprite sprite = model.stillMaterial().sprite();
               u0 = sprite.getU(0.0F);
               v0 = sprite.getV(0.0F);
               u1 = u0;
               v1 = sprite.getV(1.0F);
               u2 = sprite.getU(1.0F);
               v2 = v1;
               u3 = u2;
               v3 = v0;
            } else {
               TextureAtlasSprite sprite = model.flowingMaterial().sprite();
               float ah = (float)Mth.atan2(vec3.z, vec3.x) - (float) (Math.PI / 2);
               float ai = Mth.sin(ah) * 0.25F;
               float aj = Mth.cos(ah) * 0.25F;
               u0 = sprite.getU(0.5F + (-aj - ai));
               v0 = sprite.getV(0.5F - aj + ai);
               u1 = sprite.getU(0.5F - aj + ai);
               v1 = sprite.getV(0.5F + aj + ai);
               u2 = sprite.getU(0.5F + aj + ai);
               v2 = sprite.getV(0.5F + (aj - ai));
               u3 = sprite.getU(0.5F + (aj - ai));
               v3 = sprite.getV(0.5F + (-aj - ai));
            }

            float brightness = brightnessUp;
            this.setVertex(this.modelQuad, 0, 0.0F, nwHeight, 0.0F, u0, v0);
            this.setVertex(this.modelQuad, 1, 0.0F, swHeight, 1.0F, u1, v1);
            this.setVertex(this.modelQuad, 2, 1.0F, seHeight, 1.0F, u2, v2);
            this.setVertex(this.modelQuad, 3, 1.0F, neHeight, 0.0F, u3, v3);
            this.updateQuad(this.modelQuad, pos, lightPipeline, Direction.UP);
            this.updateColor(r, g, b, brightness);
            this.putQuad(this.modelQuad, bufferBuilder, x0, y0, z0, false);
            if (fluidState.shouldRenderBackwardUpFace(region, pos.above())) {
               this.putQuad(this.modelQuad, bufferBuilder, x0, y0, z0, true);
            }
         }

         if (rDf) {
            TextureAtlasSprite sprite = model.stillMaterial().sprite();
            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();
            float brightness = cardinalLighting.down();
            this.setVertex(this.modelQuad, 0, 0.0F, y, 1.0F, u0, v1);
            this.setVertex(this.modelQuad, 1, 0.0F, y, 0.0F, u0, v0);
            this.setVertex(this.modelQuad, 2, 1.0F, y, 0.0F, u1, v0);
            this.setVertex(this.modelQuad, 3, 1.0F, y, 1.0F, u1, v1);
            this.updateQuad(this.modelQuad, pos, lightPipeline, Direction.DOWN);
            this.updateColor(r, g, b, brightness);
            this.putQuad(this.modelQuad, bufferBuilder, x0, y0, z0, false);
         }

         this.modelQuad.setFlags(6);

         boolean hasOverlay = model.overlayMaterial() != null;

         for (Direction direction : Util.XZ_DIRECTIONS) {
            float E = 0.001F;
            float E2 = 0.999F;
            float h1;
            float h2;
            float x1;
            float z1;
            float x2;
            float z2;
            BlockState adjState;
            switch (direction) {
               case NORTH:
                  if (!rNf) {
                     continue;
                  }

                  h1 = nwHeight;
                  h2 = neHeight;
                  x1 = 0.0F;
                  x2 = 1.0F;
                  z1 = 0.001F;
                  z2 = 0.001F;
                  adjState = northState;
                  break;
               case SOUTH:
                  if (!rSf) {
                     continue;
                  }

                  h1 = seHeight;
                  h2 = swHeight;
                  x1 = 1.0F;
                  x2 = 0.0F;
                  z1 = 0.999F;
                  z2 = 0.999F;
                  adjState = southState;
                  break;
               case WEST:
                  if (!rWf) {
                     continue;
                  }

                  h1 = swHeight;
                  h2 = nwHeight;
                  x1 = 0.001F;
                  x2 = 0.001F;
                  z1 = 1.0F;
                  z2 = 0.0F;
                  adjState = westState;
                  break;
               case EAST:
                  if (rEf) {
                     h1 = neHeight;
                     h2 = seHeight;
                     x1 = 0.999F;
                     x2 = 0.999F;
                     z1 = 0.0F;
                     z2 = 1.0F;
                     adjState = eastState;
                     break;
                  }
               default:
                  continue;
            }

            if (!this.isFaceOccludedByState(region, Math.max(h1, h2), direction, pos, adjState)) {
               TextureAtlasSprite sprite = model.flowingMaterial().sprite();
               boolean isOverlay = false;
               if (hasOverlay) {
                  Block relativeBlock = adjState.getBlock();
                  if (relativeBlock instanceof HalfTransparentBlock || relativeBlock instanceof LeavesBlock) {
                     sprite = model.overlayMaterial().sprite();
                     isOverlay = true;
                  }
               }

               float u0 = sprite.getU(0.0F);
               float u1 = sprite.getU(0.5F);
               float v0 = sprite.getV((1.0F - h1) * 0.5F);
               float v1 = sprite.getV((1.0F - h2) * 0.5F);
               float v2 = sprite.getV(0.5F);
               float brightness = direction.getAxis() == Axis.Z ? cardinalLighting.north() : cardinalLighting.west();
               this.setVertex(this.modelQuad, 0, x2, h2, z2, u1, v1);
               this.setVertex(this.modelQuad, 1, x2, y, z2, u1, v2);
               this.setVertex(this.modelQuad, 2, x1, y, z1, u0, v2);
               this.setVertex(this.modelQuad, 3, x1, h1, z1, u0, v0);
               this.updateQuad(this.modelQuad, pos, lightPipeline, direction);
               this.updateColor(r, g, b, brightness);
               this.putQuad(this.modelQuad, bufferBuilder, x0, y0, z0, false);
               if (!isOverlay) {
                  this.putQuad(this.modelQuad, bufferBuilder, x0, y0, z0, true);
               }
            }
         }
      }
   }

   private float calculateAverageHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, float f, float g, float h, BlockPos blockPos) {
      if (!(h >= 1.0F) && !(g >= 1.0F)) {
         avgHeightFs[0] = 0.0F;
         avgHeightFs[1] = 0.0F;
         if (h > 0.0F || g > 0.0F) {
            float i = this.getHeight(blockAndTintGetter, fluid, blockPos);
            if (i >= 1.0F) {
               return 1.0F;
            }

            this.addWeightedHeight(avgHeightFs, i);
         }

         this.addWeightedHeight(avgHeightFs, f);
         this.addWeightedHeight(avgHeightFs, h);
         this.addWeightedHeight(avgHeightFs, g);
         return avgHeightFs[0] / avgHeightFs[1];
      } else {
         return 1.0F;
      }
   }

   private void addWeightedHeight(float[] fs, float f) {
      if (f >= 0.8F) {
         fs[0] += f * 10.0F;
         fs[1] += 10.0F;
      } else if (f >= 0.0F) {
         fs[0] += f;
         fs[1]++;
      }
   }

   private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos) {
      BlockState blockState = blockAndTintGetter.getBlockState(blockPos);
      return this.getHeight(blockAndTintGetter, fluid, blockPos, blockState);
   }

   private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos, BlockState adjBlockState) {
      FluidState adjFluidState = adjBlockState.getFluidState();
      if (fluid.isSame(adjFluidState.getType())) {
         BlockState blockState2 = blockAndTintGetter.getBlockState(blockPos.offset(Direction.UP.getUnitVec3i()));
         return fluid.isSame(blockState2.getFluidState().getType()) ? 1.0F : adjFluidState.getOwnHeight();
      } else {
         return !adjBlockState.isSolid() ? 0.0F : -1.0F;
      }
   }

   private void putQuad(ModelQuad quad, TerrainBufferBuilder bufferBuilder, float xOffset, float yOffset, float zOffset, boolean flip) {
      QuadLightData quadLightData = this.resources.quadLightData;
      int k = QuadUtils.getIterationStartIdx(quadLightData.br);
      bufferBuilder.ensureCapacity();

      for (int j = 0; j < 4; j++) {
         int i = k;
         float x = xOffset + quad.getX(i);
         float y = yOffset + quad.getY(i);
         float z = zOffset + quad.getZ(i);
         bufferBuilder.vertex(x, y, z, this.quadColors[i], quad.getU(i), quad.getV(i), quadLightData.lm[i], 0);
         k += flip ? -1 : 1;
         k &= 3;
      }
   }

   private void setVertex(ModelQuad quad, int i, float x, float y, float z, float u, float v) {
      quad.setX(i, x);
      quad.setY(i, y);
      quad.setZ(i, z);
      quad.setU(i, u);
      quad.setV(i, v);
   }

   private void updateQuad(ModelQuad quad, BlockPos blockPos, LightPipeline lightPipeline, Direction dir) {
      lightPipeline.calculate(quad, blockPos, this.resources.quadLightData, null, dir, false);
   }

   private void updateColor(float r, float g, float b, float brightness) {
      QuadLightData quadLightData = this.resources.quadLightData;

      for (int i = 0; i < 4; i++) {
         float br = quadLightData.br[i] * brightness;
         float r1 = r * br;
         float g1 = g * br;
         float b1 = b * br;
         this.quadColors[i] = ColorUtil.RGBA.pack(r1, g1, b1, 1.0F);
      }
   }
}
