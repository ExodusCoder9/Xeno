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


import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.client.renderer.v1.render.ExtraLightCoordsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.xeno.interfaces.color.BlockColorsExtended;
import com.xeno.render.chunk.build.color.BlockColorRegistry;
import com.xeno.render.chunk.build.frapi.mesh.EncodingFormat;
import com.xeno.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import com.xeno.render.chunk.build.light.LightPipeline;
import com.xeno.render.chunk.build.light.data.QuadLightData;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractBlockRenderContext extends AbstractRenderContext {
   protected final BlockColorRegistry blockColorRegistry;
   private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
      {
         this.data = new int[EncodingFormat.TOTAL_STRIDE];
         this.clear();
      }

      @Override
      public void emitDirectly() {
         AbstractBlockRenderContext.this.renderQuad(this);
      }
   };
   protected BlockState blockState;
   protected BlockPos blockPos;
   protected MutableBlockPos tempPos = new MutableBlockPos();
   protected ChunkSectionLayer defaultLayer;
   protected BlockAndTintGetter renderRegion;
   private int tintCacheIndex = -1;
   private int tintCacheValue;
   private boolean tintSourcesInitialized;
   private final List<@Nullable BlockTintSource> tintSources = new ObjectArrayList();
   private final IntList computedTintValues = new IntArrayList();
   protected final Object2ByteLinkedOpenHashMap<AbstractBlockRenderContext.ShapePairKey> occlusionCache = new Object2ByteLinkedOpenHashMap<AbstractBlockRenderContext.ShapePairKey>(
      8192, 0.25F
   ) {
   };
   protected final QuadLightData quadLightData = new QuadLightData();
   protected LightPipeline smoothLightPipeline;
   protected LightPipeline flatLightPipeline;
   protected boolean useAO;
   protected boolean defaultAO;
   protected RandomSource random;
   protected boolean enableCulling = true;
   protected int cullCompletionFlags;
   protected int cullResultFlags;

   protected AbstractBlockRenderContext() {
      this.occlusionCache.defaultReturnValue((byte)127);
      BlockColors blockColors = Minecraft.getInstance().getBlockColors();
      this.blockColorRegistry = BlockColorsExtended.from(blockColors).getColorResolverMap();
      this.useAO = Minecraft.getInstance().gameRenderer.getGameRenderState().optionsRenderState.ambientOcclusion;
   }

   protected void setupLightPipelines(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
      this.flatLightPipeline = flatLightPipeline;
      this.smoothLightPipeline = smoothLightPipeline;
   }

   public void prepareForWorld(BlockAndTintGetter blockView, boolean enableCulling) {
      this.renderRegion = blockView;
      this.enableCulling = enableCulling;
   }

   public void prepareForBlock(BlockState blockState, BlockPos blockPos, boolean modelAo) {
      this.blockPos = blockPos;
      this.blockState = blockState;
      this.defaultAO = this.useAO && modelAo && blockState.getLightEmission() == 0;
      this.cullCompletionFlags = 0;
      this.cullResultFlags = 0;
   }

   public boolean isFaceCulled(@Nullable Direction face) {
      return !this.shouldRenderFace(face);
   }

   public boolean shouldRenderFace(Direction face) {
      if (face != null && this.enableCulling) {
         int mask = 1 << face.get3DDataValue();
         if ((this.cullCompletionFlags & mask) == 0) {
            this.cullCompletionFlags |= mask;
            if (this.faceNotOccluded(this.blockState, face)) {
               this.cullResultFlags |= mask;
               return true;
            } else {
               return false;
            }
         } else {
            return (this.cullResultFlags & mask) != 0;
         }
      } else {
         return true;
      }
   }

   public boolean faceNotOccluded(BlockState blockState, Direction face) {
      BlockGetter blockGetter = this.renderRegion;
      BlockPos adjPos = this.tempPos.setWithOffset(this.blockPos, face);
      BlockState adjBlockState = blockGetter.getBlockState(adjPos);
      if (blockState.skipRendering(adjBlockState, face)) {
         return false;
      }

      if (adjBlockState.canOcclude()) {
         VoxelShape shape = blockState.getFaceOcclusionShape(face);
         if (shape.isEmpty()) {
            return true;
         }

         VoxelShape adjShape = adjBlockState.getFaceOcclusionShape(face.getOpposite());
         if (adjShape.isEmpty()) {
            return true;
         }

         if (shape == Shapes.block() && adjShape == Shapes.block()) {
            return false;
         }

         AbstractBlockRenderContext.ShapePairKey blockStatePairKey = new AbstractBlockRenderContext.ShapePairKey(shape, adjShape);
         byte b = this.occlusionCache.getAndMoveToFirst(blockStatePairKey);
         if (b != 127) {
            return b != 0;
         }

         boolean bl = Shapes.joinIsNotEmpty(shape, adjShape, BooleanOp.ONLY_FIRST);
         if (this.occlusionCache.size() == 2048) {
            this.occlusionCache.removeLastByte();
         }

         this.occlusionCache.putAndMoveToFirst(blockStatePairKey, (byte)(bl ? 1 : 0));
         return bl;
      } else {
         return true;
      }
   }

   public QuadEmitter getEmitter() {
      this.editorQuad.clear();
      return this.editorQuad;
   }

   @Override
   protected void bufferQuad(MutableQuadViewImpl quadView) {
      this.renderQuad(quadView);
   }

   private void renderQuad(MutableQuadViewImpl quad) {
      if (!this.isFaceCulled(quad.cullFace())) {
         this.endRenderQuad(quad);
      }
   }

   protected void endRenderQuad(MutableQuadViewImpl quad) {
   }

   protected void tintQuad(MutableQuadView quad) {
      int tintIndex = quad.tintIndex();
      if (tintIndex != -1) {
         int color = 0xFF000000 | this.getTintColor(this.renderRegion, this.blockState, this.blockPos, tintIndex);
         quad.multiplyColor(color);
      }
   }

   private void configureTintCache(BlockState blockState) {
      List<BlockTintSource> tintSources = this.blockColorRegistry.getTintSources(blockState.getBlock());
      int tintSourceCount = tintSources.size();
      if (tintSourceCount > 0) {
         this.tintSources.addAll(tintSources);

         for (int i = 0; i < tintSourceCount; i++) {
            this.computedTintValues.add(-1);
         }
      }
   }

   private int computeTintColor(BlockAndTintGetter level, BlockState state, BlockPos pos, int tintIndex) {
      if (!this.tintSourcesInitialized) {
         this.configureTintCache(state);
         this.tintSourcesInitialized = true;
      }

      if (tintIndex >= this.tintSources.size()) {
         return -1;
      } else {
         BlockTintSource tintSource = this.tintSources.set(tintIndex, null);
         if (tintSource != null) {
            int computedTintValue = tintSource.colorInWorld(state, level, pos);
            this.computedTintValues.set(tintIndex, computedTintValue);
            return computedTintValue;
         } else {
            return this.computedTintValues.getInt(tintIndex);
         }
      }
   }

   private int getTintColor(BlockAndTintGetter level, BlockState state, BlockPos pos, int tintIndex) {
      if (this.tintCacheIndex == tintIndex) {
         return this.tintCacheValue;
      }

      int tintColor = this.computeTintColor(level, state, pos, tintIndex);
      this.tintCacheIndex = tintIndex;
      this.tintCacheValue = tintColor;
      return tintColor;
   }

   protected void resetTintCache() {
      this.tintCacheIndex = -1;
      if (this.tintSourcesInitialized) {
         this.tintSources.clear();
         this.computedTintValues.clear();
         this.tintSourcesInitialized = false;
      }
   }

   protected void shadeQuad(MutableQuadViewImpl quad, LightPipeline lightPipeline, boolean emissive, boolean vanillaShade) {
      QuadLightData data = this.quadLightData;
      lightPipeline.calculate(quad, this.blockPos, data, quad.cullFace(), quad.lightFace(), quad.diffuseShade());
      if (emissive) {
         for (int i = 0; i < 4; i++) {
            quad.color(i, ARGB.scaleRGB(quad.color(i), data.br[i]));
            quad.lightmap(i, 15728880);
         }
      } else {
         for (int i = 0; i < 4; i++) {
            quad.color(i, ARGB.scaleRGB(quad.color(i), data.br[i]));
            quad.lightmap(i, ExtraLightCoordsUtil.smoothMax(quad.lightmap(i), data.lm[i]));
         }
      }
   }

   public ChunkSectionLayer effectiveRenderLayer(@Nullable ChunkSectionLayer quadRenderLayer) {
      return quadRenderLayer == null ? this.defaultLayer : quadRenderLayer;
   }

   public void emitVanillaBlockQuads(BlockStateModel model, Predicate<Direction> cullTest) {
      MutableQuadViewImpl quad = this.editorQuad;

      for (int i = 0; i <= 6; i++) {
         Direction cullFace = ModelHelper.faceFromIndex(i);
         if (!cullTest.test(cullFace)) {
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(this.random, parts);
            int partCount = parts.size();

            for (int j = 0; j < partCount; j++) {
               parts.get(j).emitQuads(quad, cullTest);
            }
         }
      }
   }

   record ShapePairKey(VoxelShape first, VoxelShape second) {
      ShapePairKey {
      }

      @Override
      public boolean equals(Object object) {
         return object instanceof AbstractBlockRenderContext.ShapePairKey shapePairKey
            && this.first == shapePairKey.first
            && this.second == shapePairKey.second;
      }

      @Override
      public int hashCode() {
         return System.identityHashCode(this.first) * 31 + System.identityHashCode(this.second);
      }
   }
}
