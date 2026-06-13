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
package com.xeno.mixin.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.jtracy.Plot;
import java.nio.ByteBuffer;
import com.xeno.gl.VkGlBuffer;
import com.xeno.gl.VkGlFramebuffer;
import com.xeno.gl.VkGlShader;
import com.xeno.gl.VkGlTexture;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GlStateManager.class)
public class GlStateManagerM {
   @Shadow
   @Final
   private static Plot PLOT_BUFFERS;
   @Shadow
   private static int numBuffers;

   @Overwrite(remap = false)
   public static void _bindTexture(int i) {
      VkGlTexture.bindTexture(i);
   }

   @Overwrite(remap = false)
   public static void _disableBlend() {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.disableBlend();
   }

   @Overwrite(remap = false)
   public static void _enableBlend() {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.enableBlend();
   }

   @Overwrite(remap = false)
   public static void _blendFuncSeparate(int i, int j, int k, int l) {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.blendFuncSeparate(i, j, k, l);
   }

   @Overwrite(remap = false)
   public static void _disableScissorTest() {
      Renderer.resetScissor();
   }

   @Overwrite(remap = false)
   public static void _enableScissorTest() {
   }

   @Overwrite(remap = false)
   public static void _enableCull() {
      VRenderSystem.enableCull();
   }

   @Overwrite(remap = false)
   public static void _disableCull() {
      VRenderSystem.disableCull();
   }

   @Redirect(method = "_viewport", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glViewport(IIII)V"), remap = false)
   private static void _viewport(int x, int y, int width, int height) {
      Renderer.setViewport(x, y, width, height);
   }

   @Overwrite(remap = false)
   public static void _scissorBox(int x, int y, int width, int height) {
      Renderer.setScissor(x, y, width, height);
   }

   @Overwrite(remap = false)
   public static int _getError() {
      return 0;
   }

   @Overwrite(remap = false)
   public static void _texImage2D(
      int target, int level, int internalFormat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels
   ) {
      RenderSystem.assertOnRenderThread();
      VkGlTexture.texImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
   }

   @Overwrite(remap = false)
   public static void _texSubImage2D(int target, int level, int offsetX, int offsetY, int width, int height, int format, int type, long pixels) {
      RenderSystem.assertOnRenderThread();
      VkGlTexture.texSubImage2D(target, level, offsetX, offsetY, width, height, format, type, pixels);
   }

   @Overwrite(remap = false)
   public static void _activeTexture(int i) {
      VkGlTexture.activeTexture(i);
   }

   @Overwrite(remap = false)
   public static void _texParameter(int i, int j, int k) {
      VkGlTexture.texParameteri(i, j, k);
   }

   @Overwrite(remap = false)
   public static int _getTexLevelParameter(int i, int j, int k) {
      return VkGlTexture.getTexLevelParameter(i, j, k);
   }

   @Overwrite(remap = false)
   public static void _pixelStore(int pname, int param) {
      RenderSystem.assertOnRenderThread();
      VkGlTexture.pixelStoreI(pname, param);
   }

   @Overwrite(remap = false)
   public static int _genTexture() {
      RenderSystem.assertOnRenderThread();
      return VkGlTexture.genTextureId();
   }

   @Overwrite(remap = false)
   public static void _deleteTexture(int i) {
      RenderSystem.assertOnRenderThread();
      VkGlTexture.glDeleteTextures(i);
   }

   @Overwrite(remap = false)
   public static void _colorMask(int writeMask) {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.colorMask(writeMask);
   }

   @Overwrite(remap = false)
   public static void _depthFunc(int i) {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.depthFunc(i);
   }

   @Overwrite(remap = false)
   public static void _polygonMode(int face, int mode) {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.setPolygonModeGL(mode);
   }

   @Overwrite(remap = false)
   public static void _enablePolygonOffset() {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.enablePolygonOffset();
   }

   @Overwrite(remap = false)
   public static void _disablePolygonOffset() {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.disablePolygonOffset();
   }

   @Overwrite(remap = false)
   public static void _polygonOffset(float f, float g) {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.polygonOffset(f, g);
   }

   @Overwrite(remap = false)
   public static void _enableColorLogicOp() {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.enableColorLogicOp();
   }

   @Overwrite(remap = false)
   public static void _disableColorLogicOp() {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.disableColorLogicOp();
   }

   @Overwrite(remap = false)
   public static void _logicOp(int i) {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.logicOp(i);
   }

   @Overwrite(remap = false)
   public static void _clear(int mask) {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.clear(mask);
   }

   @Overwrite(remap = false)
   public static void _disableDepthTest() {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.disableDepthTest();
   }

   @Overwrite(remap = false)
   public static void _enableDepthTest() {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.enableDepthTest();
   }

   @Overwrite(remap = false)
   public static void _depthMask(boolean bl) {
      RenderSystem.assertOnRenderThread();
      VRenderSystem.depthMask(bl);
   }

   @Overwrite(remap = false)
   public static int glGenFramebuffers() {
      RenderSystem.assertOnRenderThread();
      return VkGlFramebuffer.genFramebufferId();
   }

   @Overwrite(remap = false)
   public static void _glBindFramebuffer(int i, int j) {
      RenderSystem.assertOnRenderThread();
      VkGlFramebuffer.bindFramebuffer(i, j);
   }

   @Overwrite(remap = false)
   public static void _glFramebufferTexture2D(int i, int j, int k, int l, int m) {
      RenderSystem.assertOnRenderThread();
      VkGlFramebuffer.framebufferTexture2D(i, j, k, l, m);
   }

   @Overwrite(remap = false)
   public static int _glGenBuffers() {
      RenderSystem.assertOnRenderThread();
      numBuffers++;
      PLOT_BUFFERS.setValue(numBuffers);
      return VkGlBuffer.glGenBuffers();
   }

   @Overwrite(remap = false)
   public static void _glBindBuffer(int i, int j) {
      RenderSystem.assertOnRenderThread();
      VkGlBuffer.glBindBuffer(i, j);
   }

   @Overwrite(remap = false)
   public static void _glBufferData(int i, ByteBuffer byteBuffer, int j) {
      RenderSystem.assertOnRenderThread();
      VkGlBuffer.glBufferData(i, byteBuffer, j);
   }

   @Overwrite(remap = false)
   public static void _glBufferData(int i, long l, int j) {
      RenderSystem.assertOnRenderThread();
      VkGlBuffer.glBufferData(i, l, j);
   }

   @Overwrite(remap = false)
   public static void _glUnmapBuffer(int i) {
      RenderSystem.assertOnRenderThread();
      VkGlBuffer.glUnmapBuffer(i);
   }

   @Overwrite(remap = false)
   public static void _glDeleteBuffers(int i) {
      RenderSystem.assertOnRenderThread();
      VkGlBuffer.glDeleteBuffers(i);
   }

   @Overwrite(remap = false)
   public static void glDeleteShader(int i) {
      RenderSystem.assertOnRenderThread();
      VkGlShader.glDeleteShader(i);
   }

   @Overwrite(remap = false)
   public static int glCreateShader(int i) {
      RenderSystem.assertOnRenderThread();
      return VkGlShader.glCreateShader(i);
   }

   @Overwrite(remap = false)
   public static void glShaderSource(int i, String string) {
      RenderSystem.assertOnRenderThread();
      VkGlShader.glShaderSource(i, string);
   }

   @Overwrite(remap = false)
   public static void glCompileShader(int i) {
      RenderSystem.assertOnRenderThread();
      VkGlShader.glCompileShader(i);
   }

   @Overwrite(remap = false)
   public static int glGetShaderi(int i, int j) {
      RenderSystem.assertOnRenderThread();
      return VkGlShader.glGetShaderi(i, j);
   }

   @Overwrite(remap = false)
   public static void _glUseProgram(int i) {
   }

   @Overwrite(remap = false)
   public static int glCreateProgram() {
      return 0;
   }

   @Overwrite(remap = false)
   public static void glDeleteProgram(int i) {
   }
}
