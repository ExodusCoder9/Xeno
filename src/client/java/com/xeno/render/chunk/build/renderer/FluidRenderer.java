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
import com.xeno.render.chunk.build.light.flat.FlatLightPipeline;
import com.xeno.render.chunk.build.light.smooth.SmoothLightPipeline;
import com.xeno.render.chunk.build.light.smooth.NewSmoothLightPipeline;
import com.xeno.render.chunk.build.RenderRegion;
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
   private FlatLightPipeline flatLightPipeline;
   private SmoothLightPipeline smoothLightPipeline;
   private NewSmoothLightPipeline newSmoothLightPipeline;
   private final int[] quadColors = new int[4];
   private final float[] avgHeightFs = new float[2];
   private final MutableBlockPos tempPos = new MutableBlockPos();

   // Transient rendering context fields for JIT optimization
   private RenderRegion region;
   private FluidModel model;
   private TerrainBufferBuilder bufferBuilder;
   private boolean useAO;
   private float r, g, b;
   private int posX, posY, posZ;
   private float x0, y0, z0;
   private float y;
   private BlockPos pos;

   public FluidRenderer(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
      this.flatLightPipeline = (FlatLightPipeline) flatLightPipeline;
      if (smoothLightPipeline instanceof NewSmoothLightPipeline newSmooth) {
         this.newSmoothLightPipeline = newSmooth;
         this.smoothLightPipeline = null;
      } else if (smoothLightPipeline instanceof SmoothLightPipeline smooth) {
         this.smoothLightPipeline = smooth;
         this.newSmoothLightPipeline = null;
      }
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

   private boolean isFaceOccludedByState(float h, Direction direction, BlockPos blockPos, BlockState blockState) {
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
      RenderRegion region, BlockPos blockPos, FluidState fluidState, BlockState blockState, Direction direction, BlockState adjBlockState
   ) {
      if (adjBlockState.getFluidState().getType().isSame(fluidState.getType())) {
         return false;
      } else {
         return blockState.canOcclude() ? !blockState.isFaceSturdy(region, blockPos, direction) : true;
      }
   }

   public BlockState getAdjBlockState(RenderRegion region, int x, int y, int z, Direction dir) {
      this.mBlockPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
      return region.getBlockState(this.mBlockPos);
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
      this.pos = pos;
      this.region = this.resources.getRegion();
      this.model = this.fluidModels.get(fluidState);
      int color = model.tintSource() != null ? model.tintSource().colorInWorld(blockState, region, pos) : -1;
      TerrainRenderType renderType = TerrainRenderType.get(model.layer());
      renderType = TerrainRenderType.getRemapped(renderType);
      this.bufferBuilder = this.resources.builderPack.builder(renderType).getBufferBuilder(QuadFacing.UNDEFINED.ordinal());
      CardinalLighting cardinalLighting = region.cardinalLighting();
      this.r = ColorUtil.ARGB.unpackR(color);
      this.g = ColorUtil.ARGB.unpackG(color);
      this.b = ColorUtil.ARGB.unpackB(color);
      this.posX = pos.getX();
      this.posY = pos.getY();
      this.posZ = pos.getZ();
      this.useAO = blockState.getLightEmission() == 0 && this.ambientOcclusion;
      BlockState downState = this.getAdjBlockState(region, posX, posY, posZ, Direction.DOWN);
      BlockState upState = this.getAdjBlockState(region, posX, posY, posZ, Direction.UP);
      BlockState northState = this.getAdjBlockState(region, posX, posY, posZ, Direction.NORTH);
      BlockState southState = this.getAdjBlockState(region, posX, posY, posZ, Direction.SOUTH);
      BlockState westState = this.getAdjBlockState(region, posX, posY, posZ, Direction.WEST);
      BlockState eastState = this.getAdjBlockState(region, posX, posY, posZ, Direction.EAST);
      boolean rUf = shouldRenderFace(region, pos, fluidState, blockState, Direction.UP, upState);
      boolean rDf = shouldRenderFace(region, pos, fluidState, blockState, Direction.DOWN, downState)
         && !this.isFaceOccludedByState(0.8888889F, Direction.DOWN, pos, downState);
      boolean rNf = shouldRenderFace(region, pos, fluidState, blockState, Direction.NORTH, northState);
      boolean rSf = shouldRenderFace(region, pos, fluidState, blockState, Direction.SOUTH, southState);
      boolean rWf = shouldRenderFace(region, pos, fluidState, blockState, Direction.WEST, westState);
      boolean rEf = shouldRenderFace(region, pos, fluidState, blockState, Direction.EAST, eastState);
      if (rUf || rDf || rEf || rWf || rNf || rSf) {
         float brightnessUp = cardinalLighting.up();
         Fluid fluid = fluidState.getType();
         float height = this.getHeight(fluid, posX, posY, posZ, blockState);
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
            int nx = posX + Direction.NORTH.getStepX();
            int nz = posZ + Direction.NORTH.getStepZ();
            int sx = posX + Direction.SOUTH.getStepX();
            int sz = posZ + Direction.SOUTH.getStepZ();
            int ex = posX + Direction.EAST.getStepX();
            int ez = posZ + Direction.EAST.getStepZ();
            int wx = posX + Direction.WEST.getStepX();
            int wz = posZ + Direction.WEST.getStepZ();

            float s = this.getHeight(fluid, nx, posY, nz, northState);
            float t = this.getHeight(fluid, sx, posY, sz, southState);
            float u = this.getHeight(fluid, ex, posY, ez, eastState);
            float v = this.getHeight(fluid, wx, posY, wz, westState);
            neHeight = this.calculateAverageHeight(
               fluid, height, s, u, nx + Direction.EAST.getStepX(), posY, nz + Direction.EAST.getStepZ()
            );
            nwHeight = this.calculateAverageHeight(
               fluid, height, s, v, nx + Direction.WEST.getStepX(), posY, nz + Direction.WEST.getStepZ()
            );
            seHeight = this.calculateAverageHeight(
               fluid, height, t, u, sx + Direction.EAST.getStepX(), posY, sz + Direction.EAST.getStepZ()
            );
            swHeight = this.calculateAverageHeight(
               fluid, height, t, v, sx + Direction.WEST.getStepX(), posY, sz + Direction.WEST.getStepZ()
            );
         }

         this.x0 = posX & 15;
         this.y0 = posY & 15;
         this.z0 = posZ & 15;
         this.y = rDf ? 0.001F : 0.0F;
         this.modelQuad.setFlags(2); // parallel-to-block-face for optimized lighting
         
         if (rUf) {
            this.renderUpFace(fluidState, upState, brightnessUp, nwHeight, swHeight, seHeight, neHeight);
         }

         if (rDf) {
            this.renderDownFace(cardinalLighting.down());
         }

         this.modelQuad.setFlags(6);

         this.renderSideFaces(rNf, rSf, rWf, rEf, nwHeight, swHeight, seHeight, neHeight,
                              northState, southState, westState, eastState, cardinalLighting);
      }

      this.region = null;
      this.model = null;
      this.bufferBuilder = null;
      this.pos = null;
   }

   private void renderUpFace(FluidState fluidState, BlockState upState, float brightnessUp, float nwHeight, float swHeight, float seHeight, float neHeight) {
      if (!this.isFaceOccludedByState(Math.min(Math.min(nwHeight, swHeight), Math.min(seHeight, neHeight)), Direction.UP, this.pos, upState)) {
         nwHeight -= 0.001F;
         swHeight -= 0.001F;
         seHeight -= 0.001F;
         neHeight -= 0.001F;
         Vec3 vec3 = fluidState.isSource() ? Vec3.ZERO : fluidState.getFlow(this.region, this.pos);
         float u0;
         float u1;
         float u2;
         float u3;
         float v0;
         float v1;
         float v2;
         float v3;
         if (vec3.x == 0.0 && vec3.z == 0.0) {
            TextureAtlasSprite sprite = this.model.stillMaterial().sprite();
            u0 = sprite.getU(0.0F);
            v0 = sprite.getV(0.0F);
            u1 = u0;
            v1 = sprite.getV(1.0F);
            u2 = sprite.getU(1.0F);
            v2 = v1;
            u3 = u2;
            v3 = v0;
         } else {
            TextureAtlasSprite sprite = this.model.flowingMaterial().sprite();
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

         this.setVertex(this.modelQuad, 0, 0.0F, nwHeight, 0.0F, u0, v0);
         this.setVertex(this.modelQuad, 1, 0.0F, swHeight, 1.0F, u1, v1);
         this.setVertex(this.modelQuad, 2, 1.0F, seHeight, 1.0F, u2, v2);
         this.setVertex(this.modelQuad, 3, 1.0F, neHeight, 0.0F, u3, v3);
         this.updateQuad(this.modelQuad, this.pos, Direction.UP);
         this.updateColor(this.r, this.g, this.b, brightnessUp);
         this.putQuad(this.modelQuad, this.bufferBuilder, this.x0, this.y0, this.z0, false);
         this.mBlockPos.set(this.posX, this.posY + 1, this.posZ);
         if (fluidState.shouldRenderBackwardUpFace(this.region, this.mBlockPos)) {
            this.putQuad(this.modelQuad, this.bufferBuilder, this.x0, this.y0, this.z0, true);
         }
      }
   }

   private void renderDownFace(float brightnessDown) {
      TextureAtlasSprite sprite = this.model.stillMaterial().sprite();
      float u0 = sprite.getU0();
      float u1 = sprite.getU1();
      float v0 = sprite.getV0();
      float v1 = sprite.getV1();
      this.setVertex(this.modelQuad, 0, 0.0F, this.y, 1.0F, u0, v1);
      this.setVertex(this.modelQuad, 1, 0.0F, this.y, 0.0F, u0, v0);
      this.setVertex(this.modelQuad, 2, 1.0F, this.y, 0.0F, u1, v0);
      this.setVertex(this.modelQuad, 3, 1.0F, this.y, 1.0F, u1, v1);
      this.updateQuad(this.modelQuad, this.pos, Direction.DOWN);
      this.updateColor(this.r, this.g, this.b, brightnessDown);
      this.putQuad(this.modelQuad, this.bufferBuilder, this.x0, this.y0, this.z0, false);
   }

   private void renderSideFaces(boolean rNf, boolean rSf, boolean rWf, boolean rEf,
                                float nwHeight, float swHeight, float seHeight, float neHeight,
                                BlockState northState, BlockState southState, BlockState westState, BlockState eastState,
                                CardinalLighting cardinalLighting) {
      boolean hasOverlay = this.model.overlayMaterial() != null;

      for (Direction direction : Util.XZ_DIRECTIONS) {
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

         if (!this.isFaceOccludedByState(Math.max(h1, h2), direction, this.pos, adjState)) {
            TextureAtlasSprite sprite = this.model.flowingMaterial().sprite();
            boolean isOverlay = false;
            if (hasOverlay) {
               Block relativeBlock = adjState.getBlock();
               if (relativeBlock instanceof HalfTransparentBlock || relativeBlock instanceof LeavesBlock) {
                  sprite = this.model.overlayMaterial().sprite();
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
            this.setVertex(this.modelQuad, 1, x2, this.y, z2, u1, v2);
            this.setVertex(this.modelQuad, 2, x1, this.y, z1, u0, v2);
            this.setVertex(this.modelQuad, 3, x1, h1, z1, u0, v0);
            this.updateQuad(this.modelQuad, this.pos, direction);
            this.updateColor(this.r, this.g, this.b, brightness);
            this.putQuad(this.modelQuad, this.bufferBuilder, this.x0, this.y0, this.z0, false);
            if (!isOverlay) {
               this.putQuad(this.modelQuad, this.bufferBuilder, this.x0, this.y0, this.z0, true);
            }
         }
      }
   }

   private float calculateAverageHeight(Fluid fluid, float f, float g, float h, int x, int y, int z) {
      if (!(h >= 1.0F) && !(g >= 1.0F)) {
         avgHeightFs[0] = 0.0F;
         avgHeightFs[1] = 0.0F;
         if (h > 0.0F || g > 0.0F) {
            float i = this.getHeight(fluid, x, y, z);
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

   private float getHeight(Fluid fluid, int x, int y, int z) {
      this.tempPos.set(x, y, z);
      BlockState blockState = this.region.getBlockState(this.tempPos);
      return this.getHeight(fluid, x, y, z, blockState);
   }

   private float getHeight(Fluid fluid, int x, int y, int z, BlockState adjBlockState) {
      FluidState adjFluidState = adjBlockState.getFluidState();
      if (fluid.isSame(adjFluidState.getType())) {
         this.tempPos.set(x, y + 1, z);
         BlockState blockState2 = this.region.getBlockState(this.tempPos);
         return fluid.isSame(blockState2.getFluidState().getType()) ? 1.0F : adjFluidState.getOwnHeight();
      } else {
         return !adjBlockState.isSolid() ? 0.0F : -1.0F;
      }
   }

   private void putQuad(ModelQuad quad, TerrainBufferBuilder bufferBuilder, float xOffset, float yOffset, float zOffset, boolean flip) {
      QuadLightData quadLightData = this.resources.quadLightData;
      int k = QuadUtils.getIterationStartIdx(quadLightData.br);
      bufferBuilder.ensureCapacity();

      if (flip) {
         int i0 = k;
         float x0 = xOffset + quad.getX(i0);
         float y0 = yOffset + quad.getY(i0);
         float z0 = zOffset + quad.getZ(i0);
         bufferBuilder.vertex(x0, y0, z0, this.quadColors[i0], quad.getU(i0), quad.getV(i0), quadLightData.lm[i0], 0);

         int i1 = i0 - 1 & 3;
         float x1 = xOffset + quad.getX(i1);
         float y1 = yOffset + quad.getY(i1);
         float z1 = zOffset + quad.getZ(i1);
         bufferBuilder.vertex(x1, y1, z1, this.quadColors[i1], quad.getU(i1), quad.getV(i1), quadLightData.lm[i1], 0);

         int i2 = i1 - 1 & 3;
         float x2 = xOffset + quad.getX(i2);
         float y2 = yOffset + quad.getY(i2);
         float z2 = zOffset + quad.getZ(i2);
         bufferBuilder.vertex(x2, y2, z2, this.quadColors[i2], quad.getU(i2), quad.getV(i2), quadLightData.lm[i2], 0);

         int i3 = i2 - 1 & 3;
         float x3 = xOffset + quad.getX(i3);
         float y3 = yOffset + quad.getY(i3);
         float z3 = zOffset + quad.getZ(i3);
         bufferBuilder.vertex(x3, y3, z3, this.quadColors[i3], quad.getU(i3), quad.getV(i3), quadLightData.lm[i3], 0);
      } else {
         int i0 = k;
         float x0 = xOffset + quad.getX(i0);
         float y0 = yOffset + quad.getY(i0);
         float z0 = zOffset + quad.getZ(i0);
         bufferBuilder.vertex(x0, y0, z0, this.quadColors[i0], quad.getU(i0), quad.getV(i0), quadLightData.lm[i0], 0);

         int i1 = i0 + 1 & 3;
         float x1 = xOffset + quad.getX(i1);
         float y1 = yOffset + quad.getY(i1);
         float z1 = zOffset + quad.getZ(i1);
         bufferBuilder.vertex(x1, y1, z1, this.quadColors[i1], quad.getU(i1), quad.getV(i1), quadLightData.lm[i1], 0);

         int i2 = i1 + 1 & 3;
         float x2 = xOffset + quad.getX(i2);
         float y2 = yOffset + quad.getY(i2);
         float z2 = zOffset + quad.getZ(i2);
         bufferBuilder.vertex(x2, y2, z2, this.quadColors[i2], quad.getU(i2), quad.getV(i2), quadLightData.lm[i2], 0);

         int i3 = i2 + 1 & 3;
         float x3 = xOffset + quad.getX(i3);
         float y3 = yOffset + quad.getY(i3);
         float z3 = zOffset + quad.getZ(i3);
         bufferBuilder.vertex(x3, y3, z3, this.quadColors[i3], quad.getU(i3), quad.getV(i3), quadLightData.lm[i3], 0);
      }
   }

   private void setVertex(ModelQuad quad, int i, float x, float y, float z, float u, float v) {
      quad.setX(i, x);
      quad.setY(i, y);
      quad.setZ(i, z);
      quad.setU(i, u);
      quad.setV(i, v);
   }

   private void updateQuad(ModelQuad quad, BlockPos blockPos, Direction dir) {
      QuadLightData data = this.resources.quadLightData;
      if (this.useAO) {
         if (this.newSmoothLightPipeline != null) {
            this.newSmoothLightPipeline.calculate(quad, blockPos, data, null, dir, false);
         } else {
            this.smoothLightPipeline.calculate(quad, blockPos, data, null, dir, false);
         }
      } else {
         this.flatLightPipeline.calculate(quad, blockPos, data, null, dir, false);
      }
   }

   private void updateColor(float r, float g, float b, float brightness) {
      QuadLightData quadLightData = this.resources.quadLightData;

      float br0 = quadLightData.br[0] * brightness;
      this.quadColors[0] = ColorUtil.RGBA.pack(r * br0, g * br0, b * br0, 1.0F);

      float br1 = quadLightData.br[1] * brightness;
      this.quadColors[1] = ColorUtil.RGBA.pack(r * br1, g * br1, b * br1, 1.0F);

      float br2 = quadLightData.br[2] * brightness;
      this.quadColors[2] = ColorUtil.RGBA.pack(r * br2, g * br2, b * br2, 1.0F);

      float br3 = quadLightData.br[3] * brightness;
      this.quadColors[3] = ColorUtil.RGBA.pack(r * br3, g * br3, b * br3, 1.0F);
   }
}
