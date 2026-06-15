package com.xeno.render.chunk.build.light.smooth;

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


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import com.xeno.render.chunk.build.light.LightPipeline;
import com.xeno.render.chunk.build.light.data.LightDataAccess;
import com.xeno.render.chunk.build.light.data.QuadLightData;
import com.xeno.render.chunk.util.SimpleDirection;
import com.xeno.render.model.quad.ModelQuadView;

public class NewSmoothLightPipeline implements LightPipeline {
   private final LightDataAccess lightCache;
   private final SubBlockAoFace[] cachedFaceData = new SubBlockAoFace[12];
   private final SubBlockAoFace self = new SubBlockAoFace();
   private long cachedPos = Long.MIN_VALUE;
   private final float[] weights = new float[4];

   public NewSmoothLightPipeline(LightDataAccess cache) {
      this.lightCache = cache;

      for (int i = 0; i < this.cachedFaceData.length; i++) {
         this.cachedFaceData[i] = new SubBlockAoFace();
      }
   }

   @Override
   public void calculate(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFaceO, boolean shade) {
      this.updateCachedData(pos.asLong());
      int flags = quad.getFlags();
      SimpleDirection lightFace = SimpleDirection.BY_ORDINAL[lightFaceO.ordinal()];
      AoNeighborInfo neighborInfo = AoNeighborInfo.get(lightFace);
      if ((flags & 4) != 0 || (flags & 2) != 0 && LightDataAccess.unpackFC(this.lightCache.get(pos))) {
         if ((flags & 1) == 0) {
            this.applyAlignedFullFace(neighborInfo, pos, lightFace, out);
         } else {
            this.applyAlignedPartialFace(neighborInfo, quad, pos, lightFace, out);
         }
      } else if ((flags & 2) != 0) {
         this.applyParallelFace(neighborInfo, quad, pos, lightFace, out);
      } else {
         this.applyNonParallelFace(neighborInfo, quad, pos, lightFace, out);
      }

      this.applySidedBrightness(out, lightFaceO, shade);
   }

   private void applyAlignedFullFace(AoNeighborInfo neighborInfo, BlockPos pos, SimpleDirection dir, QuadLightData out) {
      SubBlockAoFace faceData = this.getCachedFaceData(pos, dir, true);
      neighborInfo.copyLightValues(faceData.lm, faceData.ao, out.lm, out.br);
   }

   private void applyAlignedPartialFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, SimpleDirection dir, QuadLightData out) {
      float[] weights = this.weights;

      // Vertex 0
      float cx0 = clamp(quad.getX(0));
      float cy0 = clamp(quad.getY(0));
      float cz0 = clamp(quad.getZ(0));
      neighborInfo.calculateCornerWeights(cx0, cy0, cz0, weights);
      this.applyAlignedPartialFaceVertex(pos, dir, weights, 0, out, true);

      // Vertex 1
      float cx1 = clamp(quad.getX(1));
      float cy1 = clamp(quad.getY(1));
      float cz1 = clamp(quad.getZ(1));
      neighborInfo.calculateCornerWeights(cx1, cy1, cz1, weights);
      this.applyAlignedPartialFaceVertex(pos, dir, weights, 1, out, true);

      // Vertex 2
      float cx2 = clamp(quad.getX(2));
      float cy2 = clamp(quad.getY(2));
      float cz2 = clamp(quad.getZ(2));
      neighborInfo.calculateCornerWeights(cx2, cy2, cz2, weights);
      this.applyAlignedPartialFaceVertex(pos, dir, weights, 2, out, true);

      // Vertex 3
      float cx3 = clamp(quad.getX(3));
      float cy3 = clamp(quad.getY(3));
      float cz3 = clamp(quad.getZ(3));
      neighborInfo.calculateCornerWeights(cx3, cy3, cz3, weights);
      this.applyAlignedPartialFaceVertex(pos, dir, weights, 3, out, true);
   }

   private void applyParallelFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, SimpleDirection dir, QuadLightData out) {
      this.self.calculateSelfOcclusion(this.lightCache, pos, dir);
      float[] weights = this.weights;

      // Vertex 0
      float cx0 = clamp(quad.getX(0));
      float cy0 = clamp(quad.getY(0));
      float cz0 = clamp(quad.getZ(0));
      neighborInfo.calculateCornerWeights(cx0, cy0, cz0, weights);
      float depth0 = neighborInfo.getDepth(cx0, cy0, cz0);
      if (Mth.equal(depth0, 1.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 0, out, false);
      } else {
         this.applyInsetPartialFaceVertexSO(pos, dir, depth0, 1.0F - depth0, weights, 0, out);
      }

      // Vertex 1
      float cx1 = clamp(quad.getX(1));
      float cy1 = clamp(quad.getY(1));
      float cz1 = clamp(quad.getZ(1));
      neighborInfo.calculateCornerWeights(cx1, cy1, cz1, weights);
      float depth1 = neighborInfo.getDepth(cx1, cy1, cz1);
      if (Mth.equal(depth1, 1.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 1, out, false);
      } else {
         this.applyInsetPartialFaceVertexSO(pos, dir, depth1, 1.0F - depth1, weights, 1, out);
      }

      // Vertex 2
      float cx2 = clamp(quad.getX(2));
      float cy2 = clamp(quad.getY(2));
      float cz2 = clamp(quad.getZ(2));
      neighborInfo.calculateCornerWeights(cx2, cy2, cz2, weights);
      float depth2 = neighborInfo.getDepth(cx2, cy2, cz2);
      if (Mth.equal(depth2, 1.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 2, out, false);
      } else {
         this.applyInsetPartialFaceVertexSO(pos, dir, depth2, 1.0F - depth2, weights, 2, out);
      }

      // Vertex 3
      float cx3 = clamp(quad.getX(3));
      float cy3 = clamp(quad.getY(3));
      float cz3 = clamp(quad.getZ(3));
      neighborInfo.calculateCornerWeights(cx3, cy3, cz3, weights);
      float depth3 = neighborInfo.getDepth(cx3, cy3, cz3);
      if (Mth.equal(depth3, 1.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 3, out, false);
      } else {
         this.applyInsetPartialFaceVertexSO(pos, dir, depth3, 1.0F - depth3, weights, 3, out);
      }
   }

   private void applyNonParallelFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, SimpleDirection dir, QuadLightData out) {
      float[] weights = this.weights;

      // Vertex 0
      float cx0 = clamp(quad.getX(0));
      float cy0 = clamp(quad.getY(0));
      float cz0 = clamp(quad.getZ(0));
      neighborInfo.calculateCornerWeights(cx0, cy0, cz0, weights);
      float depth0 = neighborInfo.getDepth(cx0, cy0, cz0);
      if (Mth.equal(depth0, 0.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 0, out, true);
      } else if (Mth.equal(depth0, 1.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 0, out, false);
      } else {
         this.applyInsetPartialFaceVertex(pos, dir, depth0, 1.0F - depth0, weights, 0, out);
      }

      // Vertex 1
      float cx1 = clamp(quad.getX(1));
      float cy1 = clamp(quad.getY(1));
      float cz1 = clamp(quad.getZ(1));
      neighborInfo.calculateCornerWeights(cx1, cy1, cz1, weights);
      float depth1 = neighborInfo.getDepth(cx1, cy1, cz1);
      if (Mth.equal(depth1, 0.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 1, out, true);
      } else if (Mth.equal(depth1, 1.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 1, out, false);
      } else {
         this.applyInsetPartialFaceVertex(pos, dir, depth1, 1.0F - depth1, weights, 1, out);
      }

      // Vertex 2
      float cx2 = clamp(quad.getX(2));
      float cy2 = clamp(quad.getY(2));
      float cz2 = clamp(quad.getZ(2));
      neighborInfo.calculateCornerWeights(cx2, cy2, cz2, weights);
      float depth2 = neighborInfo.getDepth(cx2, cy2, cz2);
      if (Mth.equal(depth2, 0.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 2, out, true);
      } else if (Mth.equal(depth2, 1.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 2, out, false);
      } else {
         this.applyInsetPartialFaceVertex(pos, dir, depth2, 1.0F - depth2, weights, 2, out);
      }

      // Vertex 3
      float cx3 = clamp(quad.getX(3));
      float cy3 = clamp(quad.getY(3));
      float cz3 = clamp(quad.getZ(3));
      neighborInfo.calculateCornerWeights(cx3, cy3, cz3, weights);
      float depth3 = neighborInfo.getDepth(cx3, cy3, cz3);
      if (Mth.equal(depth3, 0.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 3, out, true);
      } else if (Mth.equal(depth3, 1.0F)) {
         this.applyAlignedPartialFaceVertex(pos, dir, weights, 3, out, false);
      } else {
         this.applyInsetPartialFaceVertex(pos, dir, depth3, 1.0F - depth3, weights, 3, out);
      }
   }

   private void applyAlignedPartialFaceVertex(BlockPos pos, SimpleDirection dir, float[] w, int i, QuadLightData out, boolean offset) {
      SubBlockAoFace faceData = this.getCachedFaceData(pos, dir, offset);
      if (!faceData.hasUnpackedLightData()) {
         faceData.unpackLightData();
      }

      float sl = faceData.getBlendedSkyLight(w);
      float bl = faceData.getBlendedBlockLight(w);
      float ao = faceData.getBlendedShade(w);
      out.br[i] = ao;
      out.lm[i] = packLightMap(sl, bl);
   }

   private void applyInsetPartialFaceVertex(BlockPos pos, SimpleDirection dir, float n1d, float n2d, float[] w, int i, QuadLightData out) {
      SubBlockAoFace n1 = this.getCachedFaceData(pos, dir, false);
      if (!n1.hasUnpackedLightData()) {
         n1.unpackLightData();
      }

      SubBlockAoFace n2 = this.getCachedFaceData(pos, dir, true);
      if (!n2.hasUnpackedLightData()) {
         n2.unpackLightData();
      }

      float ao = n1.getBlendedShade(w) * n1d + n2.getBlendedShade(w) * n2d;
      float sl = n1.getBlendedSkyLight(w) * n1d + n2.getBlendedSkyLight(w) * n2d;
      float bl = n1.getBlendedBlockLight(w) * n1d + n2.getBlendedBlockLight(w) * n2d;
      out.br[i] = ao;
      out.lm[i] = packLightMap(sl, bl);
   }

   private void applyInsetPartialFaceVertexSO(BlockPos pos, SimpleDirection dir, float n1d, float n2d, float[] w, int i, QuadLightData out) {
      SubBlockAoFace n1 = this.getCachedFaceData(pos, dir, false);
      if (!n1.hasUnpackedLightData()) {
         n1.unpackLightData();
      }

      SubBlockAoFace n2 = this.getCachedFaceData(pos, dir, true);
      if (!n2.hasUnpackedLightData()) {
         n2.unpackLightData();
      }

      float ao = n1.getBlendedShade(w) * n1d + n2.getBlendedShade(w) * n2d;
      float sl = n1.getBlendedSkyLight(w) * n1d + n2.getBlendedSkyLight(w) * n2d;
      float bl = n1.getBlendedBlockLight(w) * n1d + n2.getBlendedBlockLight(w) * n2d;
      ao = Math.min(ao, this.self.getBlendedShade(w));
      out.br[i] = ao;
      out.lm[i] = packLightMap(sl, bl);
   }

   private void applySidedBrightness(QuadLightData out, Direction face, boolean shade) {
      float brightness = this.lightCache.getRegion().cardinalLighting().byFace(face);
      float[] br = out.br;
      br[0] *= brightness;
      br[1] *= brightness;
      br[2] *= brightness;
      br[3] *= brightness;
   }

   private SubBlockAoFace getCachedFaceData(BlockPos pos, SimpleDirection face, boolean offset) {
      SubBlockAoFace data = this.cachedFaceData[offset ? face.ordinal() : face.ordinal() + 6];
      if (!data.hasLightData()) {
         data.initLightData(this.lightCache, pos, face, offset);
      }

      return data;
   }

   private void updateCachedData(long key) {
      if (this.cachedPos != key) {
         SubBlockAoFace[] faceData = this.cachedFaceData;
         faceData[0].reset();  faceData[1].reset();  faceData[2].reset();
         faceData[3].reset();  faceData[4].reset();  faceData[5].reset();
         faceData[6].reset();  faceData[7].reset();  faceData[8].reset();
         faceData[9].reset();  faceData[10].reset(); faceData[11].reset();
         this.cachedPos = key;
      }
   }

   private static float clamp(float v) {
      if (v < 0.0F) {
         return 0.0F;
      } else {
         return v > 1.0F ? 1.0F : v;
      }
   }

   private static int packLightMap(float sl, float bl) {
      return ((int)sl & 0xFF) << 16 | (int)bl & 0xFF;
   }
}
