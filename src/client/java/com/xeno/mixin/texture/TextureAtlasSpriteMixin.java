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
package com.xeno.mixin.texture;

import com.mojang.blaze3d.buffers.Std140Builder;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TextureAtlasSprite.class)
public class TextureAtlasSpriteMixin {
   @Shadow
   @Final
   private SpriteContents contents;
   @Shadow
   @Final
   private int padding;
   @Shadow
   @Final
   private int x;
   @Shadow
   @Final
   private int y;

   @Overwrite
   public void uploadSpriteUbo(ByteBuffer byteBuffer, int i, int maxMipLevel, int width, int height, int uboSize) {
      for (int n = 0; n <= maxMipLevel; n++) {
         Std140Builder.intoBuffer(MemoryUtil.memSlice(byteBuffer, i + n * uboSize, uboSize))
            .putMat4f(new Matrix4f().ortho2D(0.0F, width >> n, height >> n, 0.0F))
            .putMat4f(
               new Matrix4f()
                  .translate(this.x >> n, this.y >> n, 0.0F)
                  .scale(this.contents.width() + this.padding * 2 >> n, this.contents.height() + this.padding * 2 >> n, 1.0F)
            )
            .putFloat((float)this.padding / this.contents.width())
            .putFloat((float)this.padding / this.contents.height())
            .putInt(n);
      }
   }
}
