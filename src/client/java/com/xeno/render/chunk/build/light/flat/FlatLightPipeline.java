package com.xeno.render.chunk.build.light.flat;

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


import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import com.xeno.render.chunk.build.light.LightPipeline;
import com.xeno.render.chunk.build.light.data.LightDataAccess;
import com.xeno.render.chunk.build.light.data.QuadLightData;
import com.xeno.render.chunk.util.SimpleDirection;
import com.xeno.render.model.quad.ModelQuadView;

public class FlatLightPipeline implements LightPipeline {
   private final LightDataAccess lightCache;

   public FlatLightPipeline(LightDataAccess lightCache) {
      this.lightCache = lightCache;
   }

   @Override
   public void calculate(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFace, boolean shade) {
      int lightmap;
      if (cullFace != null) {
         lightmap = this.getLightmap(pos, cullFace);
      } else {
         int flags = quad.getFlags();
         if ((flags & 4) == 0 && ((flags & 2) == 0 || !LightDataAccess.unpackFC(this.lightCache.get(pos)))) {
            lightmap = LightDataAccess.getEmissiveLightmap(this.lightCache.get(pos));
         } else {
            lightmap = this.getLightmap(pos, lightFace);
         }
      }

      CardinalLighting cardinalLighting = this.lightCache.getRegion().cardinalLighting();
      out.lm[0] = lightmap;
      out.lm[1] = lightmap;
      out.lm[2] = lightmap;
      out.lm[3] = lightmap;
      float brightness = shade ? cardinalLighting.byFace(lightFace) : cardinalLighting.up();
      out.br[0] = brightness;
      out.br[1] = brightness;
      out.br[2] = brightness;
      out.br[3] = brightness;
   }

   private int getLightmap(BlockPos pos, Direction face) {
      int word = this.lightCache.get(pos);
      if (LightDataAccess.unpackEM(word)) {
         return 15728880;
      }

      int adjWord = this.lightCache.get(pos, SimpleDirection.BY_ORDINAL[face.ordinal()]);
      return LightDataAccess.getLightmap(adjWord);
   }
}
