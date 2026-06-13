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

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import com.xeno.gl.VkGlTexture;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GL11.class)
public class GL11M {
   @Overwrite(remap = false)
   public static void glScissor(@NativeType("GLint") int x, @NativeType("GLint") int y, @NativeType("GLsizei") int width, @NativeType("GLsizei") int height) {
      Renderer.setScissor(x, y, width, height);
   }

   @Overwrite(remap = false)
   public static void glViewport(@NativeType("GLint") int x, @NativeType("GLint") int y, @NativeType("GLsizei") int w, @NativeType("GLsizei") int h) {
      Renderer.setViewport(x, y, w, h);
   }

   @Overwrite(remap = false)
   public static void glBindTexture(@NativeType("GLenum") int target, @NativeType("GLuint") int texture) {
      VkGlTexture.bindTexture(texture);
   }

   @Overwrite(remap = false)
   public static void glLineWidth(@NativeType("GLfloat") float width) {
      VRenderSystem.setLineWidth(width);
   }

   @NativeType("void")
   @Overwrite(remap = false)
   public static int glGenTextures() {
      return VkGlTexture.genTextureId();
   }

   @NativeType("GLboolean")
   @Overwrite(remap = false)
   public static boolean glIsEnabled(@NativeType("GLenum") int cap) {
      return true;
   }

   @Overwrite(remap = false)
   public static void glClear(@NativeType("GLbitfield") int mask) {
      VRenderSystem.clear(mask);
   }

   @NativeType("GLenum")
   @Overwrite(remap = false)
   public static int glGetError() {
      return 0;
   }

   @Overwrite(remap = false)
   public static void glClearColor(
      @NativeType("GLfloat") float red, @NativeType("GLfloat") float green, @NativeType("GLfloat") float blue, @NativeType("GLfloat") float alpha
   ) {
      VRenderSystem.setClearColor(red, green, blue, alpha);
   }

   @Overwrite(remap = false)
   public static void glDepthFunc(@NativeType("GLenum") int func) {
      VRenderSystem.depthFunc(func);
   }

   @Overwrite(remap = false)
   public static void glClearDepth(@NativeType("GLdouble") double depth) {
      VRenderSystem.clearDepth(depth);
   }

   @Overwrite(remap = false)
   public static void glDepthMask(@NativeType("GLboolean") boolean flag) {
      VRenderSystem.depthMask(flag);
   }

   @NativeType("void")
   @Overwrite(remap = false)
   public static int glGetInteger(@NativeType("GLenum") int pname) {
      return 0;
   }

   @Overwrite(remap = false)
   public static void glTexImage2D(
      int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels
   ) {
      VkGlTexture.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
   }

   @Overwrite(remap = false)
   public static void glTexImage2D(
      @NativeType("GLenum") int target,
      @NativeType("GLint") int level,
      @NativeType("GLint") int internalformat,
      @NativeType("GLsizei") int width,
      @NativeType("GLsizei") int height,
      @NativeType("GLint") int border,
      @NativeType("GLenum") int format,
      @NativeType("GLenum") int type,
      @NativeType("void const *") long pixels
   ) {
      VkGlTexture.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
   }

   @Overwrite(remap = false)
   public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, long pixels) {
      VkGlTexture.texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
   }

   @Overwrite(remap = false)
   public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, @Nullable ByteBuffer pixels) {
      VkGlTexture.texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
   }

   @Overwrite(remap = false)
   public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, @Nullable IntBuffer pixels) {
      VkGlTexture.texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, MemoryUtil.memByteBuffer(pixels));
   }

   @Overwrite(remap = false)
   public static void glTexParameteri(@NativeType("GLenum") int target, @NativeType("GLenum") int pname, @NativeType("GLint") int param) {
      VkGlTexture.texParameteri(target, pname, param);
   }

   @Overwrite(remap = false)
   public static void glTexParameterf(@NativeType("GLenum") int target, @NativeType("GLenum") int pname, @NativeType("GLfloat") float param) {
   }

   @Overwrite(remap = false)
   public static int glGetTexParameteri(@NativeType("GLenum") int target, @NativeType("GLenum") int pname) {
      return VkGlTexture.getTexParameteri(target, pname);
   }

   @Overwrite(remap = false)
   public static int glGetTexLevelParameteri(@NativeType("GLenum") int target, @NativeType("GLint") int level, @NativeType("GLenum") int pname) {
      return VkGlTexture.getTexLevelParameter(target, level, pname);
   }

   @Overwrite(remap = false)
   public static void glPixelStorei(@NativeType("GLenum") int pname, @NativeType("GLint") int param) {
      VkGlTexture.pixelStoreI(pname, param);
   }

   @Overwrite(remap = false)
   public static void glEnable(@NativeType("GLenum") int target) {
   }

   @Overwrite(remap = false)
   public static void glDisable(@NativeType("GLenum") int target) {
   }

   @Overwrite(remap = false)
   public static void glFinish() {
   }

   @Overwrite(remap = false)
   public static void glHint(@NativeType("GLenum") int target, @NativeType("GLenum") int hint) {
   }

   @Overwrite(remap = false)
   public static void glDeleteTextures(@NativeType("GLuint const *") int texture) {
      VkGlTexture.glDeleteTextures(texture);
   }

   @Overwrite(remap = false)
   public static void glDeleteTextures(@NativeType("GLuint const *") IntBuffer textures) {
      VkGlTexture.glDeleteTextures(textures);
   }

   @Overwrite(remap = false)
   public static void glGetTexImage(
      @NativeType("GLenum") int tex,
      @NativeType("GLint") int level,
      @NativeType("GLenum") int format,
      @NativeType("GLenum") int type,
      @NativeType("void *") long pixels
   ) {
      VkGlTexture.getTexImage(tex, level, format, type, pixels);
   }

   @Overwrite(remap = false)
   public static void glGetTexImage(
      @NativeType("GLenum") int tex,
      @NativeType("GLint") int level,
      @NativeType("GLenum") int format,
      @NativeType("GLenum") int type,
      @NativeType("void *") ByteBuffer pixels
   ) {
      VkGlTexture.getTexImage(tex, level, format, type, MemoryUtil.memAddress(pixels));
   }

   @Overwrite(remap = false)
   public static void glGetTexImage(
      @NativeType("GLenum") int tex,
      @NativeType("GLint") int level,
      @NativeType("GLenum") int format,
      @NativeType("GLenum") int type,
      @NativeType("void *") IntBuffer pixels
   ) {
      VkGlTexture.getTexImage(tex, level, format, type, MemoryUtil.memAddress(pixels));
   }

   @Overwrite(remap = false)
   public static void glCopyTexSubImage2D(
      @NativeType("GLenum") int target,
      @NativeType("GLint") int level,
      @NativeType("GLint") int xoffset,
      @NativeType("GLint") int yoffset,
      @NativeType("GLint") int x,
      @NativeType("GLint") int y,
      @NativeType("GLsizei") int width,
      @NativeType("GLsizei") int height
   ) {
   }

   @Overwrite(remap = false)
   public static void glBlendFunc(@NativeType("GLenum") int sfactor, @NativeType("GLenum") int dfactor) {
   }

   @Overwrite(remap = false)
   public static void glPolygonOffset(@NativeType("GLfloat") float factor, @NativeType("GLfloat") float units) {
      VRenderSystem.polygonOffset(factor, units);
   }
}
