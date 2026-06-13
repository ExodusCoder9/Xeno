package com.xeno.render.model.quad;

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


import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import com.xeno.render.chunk.cull.QuadFacing;

public class ModelQuad implements ModelQuadView {
   public static final int VERTEX_SIZE = 8;
   private final int[] data = new int[32];
   Direction direction;
   TextureAtlasSprite sprite;
   private int flags;

   public ModelQuad() {
   }

   public static int vertexOffset(int vertexIndex) {
      return vertexIndex * 8;
   }

   @Override
   public int getFlags() {
      return this.flags;
   }

   @Override
   public float getX(int idx) {
      return Float.intBitsToFloat(this.data[vertexOffset(idx)]);
   }

   @Override
   public float getY(int idx) {
      return Float.intBitsToFloat(this.data[vertexOffset(idx) + 1]);
   }

   @Override
   public float getZ(int idx) {
      return Float.intBitsToFloat(this.data[vertexOffset(idx) + 2]);
   }

   @Override
   public int getColor(int idx) {
      return this.data[vertexOffset(idx) + 3];
   }

   @Override
   public float getU(int idx) {
      return Float.intBitsToFloat(this.data[vertexOffset(idx) + 4]);
   }

   @Override
   public float getV(int idx) {
      return Float.intBitsToFloat(this.data[vertexOffset(idx) + 5]);
   }

   @Override
   public int getColorIndex() {
      return -1;
   }

   @Override
   public Direction getFacingDirection() {
      return this.direction;
   }

   @Override
   public Direction lightFace() {
      return this.direction;
   }

   @Override
   public QuadFacing getQuadFacing() {
      return QuadFacing.UNDEFINED;
   }

   @Override
   public int getNormal() {
      return 0;
   }

   public float setX(int idx, float f) {
      return this.data[vertexOffset(idx)] = Float.floatToRawIntBits(f);
   }

   public float setY(int idx, float f) {
      return this.data[vertexOffset(idx) + 1] = Float.floatToRawIntBits(f);
   }

   public float setZ(int idx, float f) {
      return this.data[vertexOffset(idx) + 2] = Float.floatToRawIntBits(f);
   }

   public float setU(int idx, float f) {
      return this.data[vertexOffset(idx) + 4] = Float.floatToRawIntBits(f);
   }

   public float setV(int idx, float f) {
      return this.data[vertexOffset(idx) + 5] = Float.floatToRawIntBits(f);
   }

   public void setFlags(int f) {
      this.flags = f;
   }

   public void setSprite(TextureAtlasSprite sprite) {
      this.sprite = sprite;
   }
}
