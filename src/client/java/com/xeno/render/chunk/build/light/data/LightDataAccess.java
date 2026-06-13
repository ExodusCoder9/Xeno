package com.xeno.render.chunk.build.light.data;

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


import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.BrightnessGetter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.xeno.Initializer;
import com.xeno.interfaces.VoxelShapeExtended;
import com.xeno.render.chunk.util.SimpleDirection;

public abstract class LightDataAccess {
   private static final int BL_OFFSET = 0;
   private static final int SL_OFFSET = 4;
   private static final int AO_OFFSET = 8;
   private static final int CO_OFFSET = 20;
   private static final int EM_OFFSET = 28;
   private static final int OP_OFFSET = 29;
   private static final int FO_OFFSET = 30;
   private static final int FC_OFFSET = 31;
   private static final float AO_INV = 4.8828125E-4F;
   private final MutableBlockPos pos = new MutableBlockPos();
   protected BlockAndTintGetter region;
   final boolean subBlockLighting = Initializer.CONFIG.ambientOcclusion == 2;

   protected LightDataAccess() {
   }

   public int get(int x, int y, int z, SimpleDirection d1, SimpleDirection d2) {
      return this.get(x + d1.getStepX() + d2.getStepX(), y + d1.getStepY() + d2.getStepY(), z + d1.getStepZ() + d2.getStepZ());
   }

   public int get(int x, int y, int z, SimpleDirection dir) {
      return this.get(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
   }

   public int get(BlockPos pos, SimpleDirection dir) {
      return this.get(pos.getX(), pos.getY(), pos.getZ(), dir);
   }

   public int get(BlockPos pos) {
      return this.get(pos.getX(), pos.getY(), pos.getZ());
   }

   public abstract int get(int var1, int var2, int var3);

   protected int compute(int x, int y, int z) {
      BlockPos pos = this.pos.set(x, y, z);
      BlockState state = this.region.getBlockState(pos);
      boolean em = state.emissiveRendering(this.region, pos);
      boolean op;
      if (this.subBlockLighting) {
         op = state.canOcclude();
      } else {
         op = state.isViewBlocking(this.region, pos) && state.getLightDampening() != 0;
      }

      boolean fo = state.isSolidRender();
      boolean fc = state.isCollisionShapeFullBlock(this.region, pos);
      int lu = state.getLightEmission();
      int bl;
      int sl;
      if (fo && lu == 0) {
         bl = 0;
         sl = 0;
      } else if (em) {
         bl = this.region.getBrightness(LightLayer.BLOCK, pos);
         sl = this.region.getBrightness(LightLayer.SKY, pos);
      } else {
         int light = LevelRenderer.getLightCoords(BrightnessGetter.DEFAULT, this.region, state, pos);
         bl = LightCoordsUtil.block(light);
         sl = LightCoordsUtil.sky(light);
      }

      float ao;
      if (lu == 0) {
         ao = state.getShadeBrightness(this.region, pos);
      } else {
         ao = 1.0F;
      }

      boolean useAo = ao < 1.0F;
      bl = Math.max(bl, lu);
      int crs = (fo || fc) && lu == 0 && useAo ? 255 : 0;
      if (!fo && op) {
         VoxelShape shape = state.getShape(this.region, pos);
         crs = ((VoxelShapeExtended)shape).getCornerOcclusion();
      }

      return packFC(fc) | packFO(fo) | packOP(op) | packEM(em) | packCO(crs) | packAO(ao) | packSL(sl) | packBL(bl);
   }

   public static int packBL(int blockLight) {
      return (blockLight & 15) << 0;
   }

   public static int unpackBL(int word) {
      return word >>> 0 & 15;
   }

   public static int packSL(int skyLight) {
      return (skyLight & 15) << 4;
   }

   public static int unpackSL(int word) {
      return word >>> 4 & 15;
   }

   public static int packAO(float ao) {
      int aoi = (int)(ao * 2048.0F);
      return (aoi & 4095) << 8;
   }

   public static float unpackAO(int word) {
      int aoi = word >>> 8 & 4095;
      return aoi * 4.8828125E-4F;
   }

   public static int packCO(int co) {
      return (co & 0xFF) << 20;
   }

   public static int unpackCO(int word) {
      return word >>> 20 & 0xFF;
   }

   public static int packEM(boolean emissive) {
      return (emissive ? 1 : 0) << 28;
   }

   public static boolean unpackEM(int word) {
      return (word >>> 28 & 1) != 0;
   }

   public static int packOP(boolean opaque) {
      return (opaque ? 1 : 0) << 29;
   }

   public static boolean unpackOP(int word) {
      return (word >>> 29 & 1) != 0;
   }

   public static int packFO(boolean opaque) {
      return (opaque ? 1 : 0) << 30;
   }

   public static boolean unpackFO(int word) {
      return (word >>> 30 & 1) != 0;
   }

   public static int packFC(boolean fullCube) {
      return (fullCube ? 1 : 0) << 31;
   }

   public static boolean unpackFC(int word) {
      return (word >>> 31 & 1) != 0;
   }

   public static int getLightmap(int word) {
      return LightCoordsUtil.pack(unpackBL(word), unpackSL(word));
   }

   public static int getEmissiveLightmap(int word) {
      return unpackEM(word) ? 15728880 : getLightmap(word);
   }

   public BlockAndTintGetter getRegion() {
      return this.region;
   }
}
