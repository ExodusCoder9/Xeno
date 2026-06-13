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
package com.xeno.mixin.compatibility.gl;

import com.xeno.gl.VkGlFramebuffer;
import com.xeno.gl.VkGlRenderbuffer;
import com.xeno.gl.VkGlTexture;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.NativeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GL30.class)
public class GL30M {
   @Overwrite(remap = false)
   public static void glGenerateMipmap(@NativeType("GLenum") int target) {
      VkGlTexture.generateMipmap(target);
   }

   @NativeType("void")
   @Overwrite(remap = false)
   public static int glGenFramebuffers() {
      return VkGlFramebuffer.genFramebufferId();
   }

   @Overwrite(remap = false)
   public static void glBindFramebuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int framebuffer) {
      VkGlFramebuffer.bindFramebuffer(target, framebuffer);
   }

   @Overwrite(remap = false)
   public static void glFramebufferTexture2D(
      @NativeType("GLenum") int target,
      @NativeType("GLenum") int attachment,
      @NativeType("GLenum") int textarget,
      @NativeType("GLuint") int texture,
      @NativeType("GLint") int level
   ) {
      VkGlFramebuffer.framebufferTexture2D(target, attachment, textarget, texture, level);
   }

   @Overwrite(remap = false)
   public static void glFramebufferRenderbuffer(
      @NativeType("GLenum") int target,
      @NativeType("GLenum") int attachment,
      @NativeType("GLenum") int renderbuffertarget,
      @NativeType("GLuint") int renderbuffer
   ) {
   }

   @Overwrite(remap = false)
   public static void glDeleteFramebuffers(@NativeType("GLuint const *") int framebuffer) {
      VkGlFramebuffer.deleteFramebuffer(framebuffer);
   }

   @Overwrite(remap = false)
   @NativeType("GLenum")
   public static int glCheckFramebufferStatus(@NativeType("GLenum") int target) {
      return VkGlFramebuffer.glCheckFramebufferStatus(target);
   }

   @Overwrite(remap = false)
   public static void glBlitFramebuffer(
      @NativeType("GLint") int srcX0,
      @NativeType("GLint") int srcY0,
      @NativeType("GLint") int srcX1,
      @NativeType("GLint") int srcY1,
      @NativeType("GLint") int dstX0,
      @NativeType("GLint") int dstY0,
      @NativeType("GLint") int dstX1,
      @NativeType("GLint") int dstY1,
      @NativeType("GLbitfield") int mask,
      @NativeType("GLenum") int filter
   ) {
      VkGlFramebuffer.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
   }

   @NativeType("void")
   @Overwrite(remap = false)
   public static int glGenRenderbuffers() {
      return VkGlRenderbuffer.genId();
   }

   @Overwrite(remap = false)
   public static void glBindRenderbuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int framebuffer) {
      VkGlRenderbuffer.bindRenderbuffer(target, framebuffer);
   }

   @Overwrite(remap = false)
   public static void glRenderbufferStorage(
      @NativeType("GLenum") int target, @NativeType("GLenum") int internalformat, @NativeType("GLsizei") int width, @NativeType("GLsizei") int height
   ) {
      VkGlRenderbuffer.renderbufferStorage(target, internalformat, width, height);
   }

   @Overwrite(remap = false)
   public static void glDeleteRenderbuffers(@NativeType("GLuint const *") int renderbuffer) {
      VkGlRenderbuffer.deleteRenderbuffer(renderbuffer);
   }
}
