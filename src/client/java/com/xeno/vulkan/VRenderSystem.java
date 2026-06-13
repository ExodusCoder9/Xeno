package com.xeno.vulkan;

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


import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import com.xeno.render.engine.VkGpuBuffer;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.shader.PipelineState;
import com.xeno.vulkan.util.ColorUtil;
import com.xeno.vulkan.util.MappedBuffer;
import com.xeno.vulkan.util.MemoryAccess;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

public abstract class VRenderSystem {
   private static final float DEFAULT_DEPTH_VALUE = 1.0F;
   private static long window;
   public static boolean depthTest = true;
   public static boolean depthMask = true;
   public static int depthFun = 515;
   public static int topology = 3;
   public static int polygonMode = 0;
   public static boolean canSetLineWidth = false;
   public static int colorMask = PipelineState.ColorMask.getColorMask(true, true, true, true);
   public static boolean cull = true;
   public static boolean logicOp = false;
   public static int logicOpFun = 0;
   private static final GpuTextureView[] shaderTextures = new GpuTextureView[12];
   public static float clearDepthValue = 1.0F;
   public static FloatBuffer clearColor = MemoryUtil.memCallocFloat(4);
   public static MappedBuffer modelViewMatrix = new MappedBuffer(64);
   public static MappedBuffer projectionMatrix = new MappedBuffer(64);
   public static MappedBuffer TextureMatrix = new MappedBuffer(64);
   public static MappedBuffer MVP = new MappedBuffer(64);
   public static MappedBuffer modelOffset = new MappedBuffer(12);
   public static MappedBuffer lightDirection0 = new MappedBuffer(12);
   public static MappedBuffer lightDirection1 = new MappedBuffer(12);
   public static MappedBuffer shaderColor = new MappedBuffer(16);
   public static MappedBuffer shaderFogColor = new MappedBuffer(16);
   public static FogData fogData;
   public static MappedBuffer screenSize = new MappedBuffer(8);
   public static MappedBuffer textureSize = new MappedBuffer(8);
   public static MappedBuffer texelSize = new MappedBuffer(8);
   public static float alphaCutout = 0.0F;
   private static boolean depthBiasEnabled = false;
   private static float depthBiasConstant = 0.0F;
   private static float depthBiasSlope = 0.0F;
   public static Matrix4f projection;
   public static Matrix4f modelView;
   private static int currentTime;

   public VRenderSystem() {
   }

   public static void initRenderer() {
      Vulkan.initVulkan(window);
      setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   public static void setShaderTexture(int i, @Nullable GpuTextureView gpuTextureView) {
      RenderSystem.assertOnRenderThread();
      if (i >= 0 && i < shaderTextures.length) {
         shaderTextures[i] = gpuTextureView;
      }
   }

   @Nullable
   public static GpuTextureView getShaderTexture(int i) {
      RenderSystem.assertOnRenderThread();
      return i >= 0 && i < shaderTextures.length ? shaderTextures[i] : null;
   }

   public static MappedBuffer getScreenSize() {
      updateScreenSize();
      return screenSize;
   }

   public static void updateScreenSize() {
      Window window = Minecraft.getInstance().getWindow();
      screenSize.putFloat(0, window.getWidth());
      screenSize.putFloat(4, window.getHeight());
   }

   public static MappedBuffer getTextureSize() {
      return textureSize;
   }

   public static MappedBuffer getTexelSize() {
      return texelSize;
   }

   public static void setTextureSize(int width, int height) {
      textureSize.putInt(0, width);
      textureSize.putInt(4, height);
      texelSize.putFloat(0, 1.0F / width);
      texelSize.putFloat(4, 1.0F / height);
   }

   public static void setWindow(long window) {
      VRenderSystem.window = window;
   }

   public static ByteBuffer getModelOffset() {
      return modelOffset.buffer;
   }

   public static int maxSupportedTextureSize() {
      return DeviceManager.deviceProperties.limits().maxImageDimension2D();
   }

   public static void applyMVP(Matrix4f MV, Matrix4f P) {
      applyModelViewMatrix(MV);
      applyProjectionMatrix(P);
      calculateMVP();
   }

   public static void applyModelViewMatrix(Matrix4f mat) {
      mat.get(modelViewMatrix.buffer.asFloatBuffer());
   }

   public static void applyProjectionMatrix(Matrix4f mat) {
      mat.get(projectionMatrix.buffer.asFloatBuffer());
   }

   public static void applyProjectionMatrix(GpuBufferSlice bufferSlice) {
      long ptr = ((VkGpuBuffer)bufferSlice.buffer()).getBuffer().getDataPtr();
      ByteBuffer byteBuffer = MemoryUtil.memByteBuffer(ptr + bufferSlice.offset(), (int)bufferSlice.length());
      Matrix4f matrix4f = new Matrix4f().set(byteBuffer);
      matrix4f.get(projectionMatrix.buffer.asFloatBuffer());
   }

   public static void calculateMVP() {
      Matrix4f MV = new Matrix4f(modelViewMatrix.buffer.asFloatBuffer());
      Matrix4f P = new Matrix4f(projectionMatrix.buffer.asFloatBuffer());
      P.mul(MV).get(MVP.buffer);
   }

   public static void setTextureMatrix(Matrix4f mat) {
      mat.get(TextureMatrix.buffer.asFloatBuffer());
   }

   public static MappedBuffer getTextureMatrix() {
      return TextureMatrix;
   }

   public static MappedBuffer getModelViewMatrix() {
      return modelViewMatrix;
   }

   public static MappedBuffer getProjectionMatrix() {
      return projectionMatrix;
   }

   public static MappedBuffer getMVP() {
      return MVP;
   }

   public static void setModelOffset(float x, float y, float z) {
      long ptr = modelOffset.ptr;
      MemoryAccess.memPutFloat(ptr, x);
      MemoryAccess.memPutFloat(ptr + 4L, y);
      MemoryAccess.memPutFloat(ptr + 8L, z);
   }

   public static void setShaderColor(float f1, float f2, float f3, float f4) {
      ColorUtil.setRGBA_Buffer(shaderColor, f1, f2, f3, f4);
   }

   public static void setShaderFogColor(float f1, float f2, float f3, float f4) {
      ColorUtil.setRGBA_Buffer(shaderFogColor, f1, f2, f3, f4);
   }

   public static MappedBuffer getShaderColor() {
      return shaderColor;
   }

   public static MappedBuffer getShaderFogColor() {
      return shaderFogColor;
   }

   public static FogData getFogData() {
      return fogData;
   }

   public static void setCurrentTime(int currentTime) {
      VRenderSystem.currentTime = currentTime;
   }

   public static int getCurrentTime() {
      return currentTime;
   }

   public static void setClearColor(float f1, float f2, float f3, float f4) {
      ColorUtil.setRGBA_Buffer(clearColor, f1, f2, f3, f4);
   }

   public static void clear(int mask) {
      Renderer.clearAttachments(mask);
   }

   public static void clearDepth(double depth) {
      clearDepthValue = (float)depth;
   }

   public static void disableDepthTest() {
      depthTest = false;
   }

   public static void depthMask(boolean b) {
      depthMask = b;
   }

   public static void setPrimitiveTopologyGL(int mode) {
      topology = switch (mode) {
         case 0 -> 0;
         case 1, 3 -> 1;
         default -> throw new RuntimeException(String.format("Unknown GL primitive topology: %s", mode));
         case 4, 5, 6 -> 3;
      };
   }

   public static void setPolygonModeGL(int mode) {
      polygonMode = switch (mode) {
         case 6912 -> 2;
         case 6913 -> 1;
         case 6914 -> 0;
         default -> throw new RuntimeException(String.format("Unknown GL polygon mode: %s", mode));
      };
   }

   public static void setLineWidth(float width) {
      if (canSetLineWidth) {
         Renderer.setLineWidth(width);
      }
   }

   public static void colorMask(boolean b, boolean b1, boolean b2, boolean b3) {
      colorMask = PipelineState.ColorMask.getColorMask(b, b1, b2, b3);
   }

   public static void colorMask(int writeMask) {
      colorMask = PipelineState.ColorMask.getColorMask((writeMask & 1) != 0, (writeMask & 2) != 0, (writeMask & 4) != 0, (writeMask & 8) != 0);
   }

   public static int getColorMask() {
      return colorMask;
   }

   public static void enableDepthTest() {
      depthTest = true;
   }

   public static void enableCull() {
      cull = true;
   }

   public static void disableCull() {
      cull = false;
   }

   public static void depthFunc(int depthFun) {
      VRenderSystem.depthFun = depthFun;
   }

   public static void enableBlend() {
      PipelineState.blendInfo.enabled = true;
   }

   public static void disableBlend() {
      PipelineState.blendInfo.enabled = false;
   }

   public static void blendFunc(int srcFactor, int dstFactor) {
      PipelineState.blendInfo.setBlendFunction(srcFactor, dstFactor);
   }

   public static void blendFuncSeparate(int srcFactorRGB, int dstFactorRGB, int srcFactorAlpha, int dstFactorAlpha) {
      PipelineState.blendInfo.setBlendFuncSeparate(srcFactorRGB, dstFactorRGB, srcFactorAlpha, dstFactorAlpha);
   }

   public static void blendOp(int op) {
      PipelineState.blendInfo.setBlendOp(op);
   }

   public static void enableColorLogicOp() {
      logicOp = true;
   }

   public static void disableColorLogicOp() {
      logicOp = false;
   }

   public static void logicOp(int glLogicOp) {
      logicOpFun = glLogicOp;
   }

   public static void polygonOffset(float slope, float biasConstant) {
      if (depthBiasConstant != biasConstant || depthBiasSlope != slope) {
         depthBiasConstant = biasConstant;
         depthBiasSlope = slope;
         Renderer.setDepthBias(depthBiasConstant, depthBiasSlope);
      }
   }

   public static void enablePolygonOffset() {
      if (!depthBiasEnabled) {
         Renderer.setDepthBias(depthBiasConstant, depthBiasSlope);
         depthBiasEnabled = true;
      }
   }

   public static void disablePolygonOffset() {
      if (depthBiasEnabled) {
         Renderer.setDepthBias(0.0F, 0.0F);
         depthBiasEnabled = false;
      }
   }
}
