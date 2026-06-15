package com.xeno.render.chunk.build.task;

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


import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import com.xeno.Initializer;
import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.build.RenderRegion;
import com.xeno.render.chunk.build.UploadBuffer;
import com.xeno.render.chunk.build.GreedyMeshBuilder;
import com.xeno.render.chunk.build.renderer.BlockRenderer;
import com.xeno.render.chunk.build.renderer.FluidRenderer;
import com.xeno.render.chunk.build.thread.BuilderResources;
import com.xeno.render.chunk.build.thread.ThreadBuilderPack;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.vertex.GreedyTerrainBuilder;
import com.xeno.render.vertex.TerrainBuilder;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.render.vertex.VertexBuilder;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class BuildTask extends ChunkTask {
   @Nullable
   protected RenderRegion region;

   public BuildTask(RenderSection renderSection, RenderRegion renderRegion, Vector3d cameraPos, boolean highPriority) {
      super(renderSection, cameraPos);
      this.region = renderRegion;
      this.highPriority = highPriority;
   }

   @Override
   public String name() {
      return "rend_chk_rebuild";
   }

   @Override
   public ChunkTask.Result runTask(BuilderResources builderResources) {
      long startTime = System.nanoTime();
      if (this.cancelled) {
         return ChunkTask.Result.CANCELLED;
      } else {
         float x = (float)this.cameraPos.x;
         float y = (float)this.cameraPos.y;
         float z = (float)this.cameraPos.z;
         CompileResult compileResult = this.compile(x, y, z, builderResources);
         CompiledSection compiledSection = new CompiledSection();
         compiledSection.blockEntities.addAll(compileResult.blockEntities);
         compiledSection.transparencyState = compileResult.transparencyState;
         compiledSection.isCompletelyEmpty = compileResult.renderedLayers.isEmpty();
         compileResult.compiledSection = compiledSection;
         if (this.cancelled) {
            compileResult.renderedLayers.values().forEach(UploadBuffer::release);
            return ChunkTask.Result.CANCELLED;
         } else {
            taskDispatcher.scheduleSectionUpdate(compileResult);
            float buildTime = (float)(System.nanoTime() - startTime) * 1.0E-6F;
            builderResources.updateBuildStats((int)buildTime);
            return ChunkTask.Result.SUCCESSFUL;
         }
      }
   }

   private CompileResult compile(float camX, float camY, float camZ, BuilderResources builderResources) {
      CompileResult compileResult = new CompileResult(this.section, true);
      VisGraph visGraph = new VisGraph();
      if (this.region == null) {
         compileResult.visibilitySet = visGraph.resolve();
         return compileResult;
      }

      Vector3f pos = new Vector3f();
      ThreadBuilderPack bufferBuilders = builderResources.builderPack;
      this.setupBufferBuilders(bufferBuilders);
      this.region.setBlockData(builderResources.blockDataArray);
      this.region.loadBlockStates();
      this.region.initTintCache(builderResources.tintCache);
      builderResources.update(this.region, this.section);
      BlockRenderer blockRenderer = builderResources.blockRenderer;
      FluidRenderer fluidRenderer = builderResources.fluidRenderer;
      MutableBlockPos blockPos = new MutableBlockPos();
      int baseX = this.section.xOffset();
      int baseY = this.section.yOffset();
      int baseZ = this.section.zOffset();
      boolean useFastPath = !this.region.isDebug;
      BlockState[] blockData = this.region.blockData;

      for (int y = 0; y < 16; y++) {
         int absY = baseY + y;
         int yOffset = 400 * y + 842;
         for (int z = 0; z < 16; z++) {
            int absZ = baseZ + z;
            int yzOffset = yOffset + 20 * z;
            for (int x = 0; x < 16; x++) {
               BlockState blockState = useFastPath ? blockData[yzOffset + x] : this.region.getBlockState(baseX + x, absY, absZ);
               if (blockState.isAir()) {
                  continue;
               }

               int absX = baseX + x;
               boolean isOpaque = blockState.isSolidRender();
               boolean hasBlockEntity = blockState.hasBlockEntity();
               FluidState fluidState = blockState.getFluidState();
               boolean hasFluid = !fluidState.isEmpty();
               boolean hasModel = blockState.getRenderShape() == RenderShape.MODEL;

               if (isOpaque || hasBlockEntity || hasFluid || hasModel) {
                  blockPos.set(absX, absY, absZ);

                  if (isOpaque) {
                     visGraph.setOpaque(blockPos);
                  }

                  if (hasBlockEntity) {
                     BlockEntity blockEntity = this.region.getBlockEntity(blockPos);
                     if (blockEntity != null) {
                        this.handleBlockEntity(compileResult, blockEntity);
                     }
                  }

                  if (hasFluid) {
                     fluidRenderer.renderLiquid(blockState, fluidState, blockPos);
                  }

                  if (hasModel) {
                     pos.set(x, y, z);
                     blockRenderer.renderBlock(blockState, blockPos, pos);
                  }
               }
            }
         }
      }

      TerrainBuilder trasnlucentTerrainBuilder = bufferBuilders.builder(TerrainRenderType.TRANSLUCENT);
      if (trasnlucentTerrainBuilder.getBufferBuilder(QuadFacing.UNDEFINED.ordinal()).getVertices() > 0) {
         trasnlucentTerrainBuilder.setupQuadSortingPoints();
         trasnlucentTerrainBuilder.setupQuadSorting(camX - this.section.xOffset(), camY - this.section.yOffset(), camZ - this.section.zOffset());
         compileResult.transparencyState = trasnlucentTerrainBuilder.getSortState();
      }

      for (TerrainRenderType renderType : TerrainRenderType.VALUES) {
         TerrainBuilder builder = bufferBuilders.builder(renderType);
         TerrainBuilder.DrawState drawState = builder.endDrawing();
         TerrainBuilder uploadSrc = (realBuilders != null)
            ? realBuilders[renderType.ordinal()]
            : builder;
         UploadBuffer uploadBuffer = new UploadBuffer(uploadSrc, drawState);
         compileResult.renderedLayers.put(renderType, uploadBuffer);
         builder.clear();
      }

      compileResult.visibilitySet = visGraph.resolve();
      this.region = null;
      return compileResult;
   }

   private TerrainBuilder[] realBuilders;

   private void setupBufferBuilders(ThreadBuilderPack builderPack) {
      boolean greedy = Initializer.CONFIG.greedyMeshing;
      if (greedy) {
         realBuilders = new TerrainBuilder[TerrainRenderType.VALUES.length];
      }
      for (TerrainRenderType renderType : TerrainRenderType.VALUES) {
         TerrainBuilder bufferBuilder = builderPack.builder(renderType);
         bufferBuilder.begin();
         if (greedy) {
            realBuilders[renderType.ordinal()] = bufferBuilder;
            int size = TerrainRenderType.getLayer(renderType).bufferSize() / DefaultVertexFormat.BLOCK.getVertexSize();
            VertexBuilder vb = new VertexBuilder.CompressedVertexBuilder();
            GreedyTerrainBuilder greedyBuilder = new GreedyTerrainBuilder(bufferBuilder, size, vb);
            builderPack.replaceBuilder(renderType, greedyBuilder);
            Initializer.LOGGER.debug("Greedy: replaced {} builder with GreedyTerrainBuilder", renderType);
         }
      }
   }

   private TerrainBuilder getTerrainBuilder(ThreadBuilderPack bufferBuilders, TerrainRenderType renderType) {
      renderType = this.compactRenderTypes(renderType);
      return bufferBuilders.builder(renderType);
   }

   private TerrainRenderType compactRenderTypes(TerrainRenderType renderType) {
      if (Initializer.CONFIG.uniqueOpaqueLayer) {
         renderType = switch (renderType) {
            case SOLID, CUTOUT -> TerrainRenderType.CUTOUT;
            case TRANSLUCENT, TRIPWIRE -> TerrainRenderType.TRANSLUCENT;
         };
      } else {
         renderType = switch (renderType) {
            case SOLID -> TerrainRenderType.SOLID;
            case CUTOUT -> TerrainRenderType.CUTOUT;
            case TRANSLUCENT, TRIPWIRE -> TerrainRenderType.TRANSLUCENT;
         };
      }

      return renderType;
   }

   private <E extends BlockEntity> void handleBlockEntity(CompileResult compileResult, E blockEntity) {
      BlockEntityRenderer<E, BlockEntityRenderState> blockEntityRenderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
      if (blockEntityRenderer != null) {
         compileResult.blockEntities.add(blockEntity);
         if (blockEntityRenderer.shouldRenderOffScreen()) {
            compileResult.globalBlockEntities.add(blockEntity);
         }
      }
   }
}
