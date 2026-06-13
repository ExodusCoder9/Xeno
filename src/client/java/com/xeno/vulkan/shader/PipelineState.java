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
package com.xeno.vulkan.shader;

import java.util.Objects;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.framebuffer.RenderPass;

public class PipelineState {
   private static final int DEFAULT_DEPTH_OP = 515;
   public static PipelineState.BlendInfo blendInfo = defaultBlendInfo();
   public static final PipelineState DEFAULT = new PipelineState(
      getAssemblyRasterState(), getBlendState(), getDepthState(), getLogicOpState(), VRenderSystem.getColorMask(), null
   );
   public static PipelineState currentState = DEFAULT;
   final RenderPass renderPass;
   int assemblyRasterState;
   int blendState_i;
   int depthState_i;
   int colorMask_i;
   int logicOp_i;

   public static PipelineState getCurrentPipelineState(RenderPass renderPass) {
      int assemblyRasterState = getAssemblyRasterState();
      int blendState = getBlendState();
      int currentColorMask = VRenderSystem.getColorMask();
      int depthState = getDepthState();
      int logicOp = getLogicOpState();
      return currentState.checkEquals(assemblyRasterState, blendState, depthState, logicOp, currentColorMask, renderPass)
         ? currentState
         : (currentState = new PipelineState(assemblyRasterState, blendState, depthState, logicOp, currentColorMask, renderPass));
   }

   public static int getBlendState() {
      return PipelineState.BlendState.getState(blendInfo);
   }

   public static int getAssemblyRasterState() {
      return PipelineState.AssemblyRasterState.encode(VRenderSystem.cull, VRenderSystem.topology, VRenderSystem.polygonMode);
   }

   public static int getDepthState() {
      int depthState = 0;
      depthState |= VRenderSystem.depthTest ? 1 : 0;
      depthState |= VRenderSystem.depthMask ? 2 : 0;
      return depthState | PipelineState.DepthState.encodeDepthFun(VRenderSystem.depthFun);
   }

   public static int getLogicOpState() {
      int logicOpState = 0;
      logicOpState |= VRenderSystem.logicOp ? 1 : 0;
      return logicOpState | PipelineState.LogicOpState.encodeLogicOpFun(VRenderSystem.logicOpFun);
   }

   public PipelineState(int assemblyRasterState, int blendState, int depthState, int logicOp, int colorMask, RenderPass renderPass) {
      this.renderPass = renderPass;
      this.assemblyRasterState = assemblyRasterState;
      this.blendState_i = blendState;
      this.depthState_i = depthState;
      this.colorMask_i = colorMask;
      this.logicOp_i = logicOp;
   }

   private boolean checkEquals(int assemblyRasterState, int blendState, int depthState, int logicOp, int colorMask, RenderPass renderPass) {
      return blendState == this.blendState_i
         && depthState == this.depthState_i
         && renderPass == this.renderPass
         && logicOp == this.logicOp_i
         && assemblyRasterState == this.assemblyRasterState
         && colorMask == this.colorMask_i;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         PipelineState that = (PipelineState)o;
         return this.blendState_i == that.blendState_i
            && this.depthState_i == that.depthState_i
            && this.renderPass == that.renderPass
            && this.logicOp_i == that.logicOp_i
            && this.assemblyRasterState == that.assemblyRasterState
            && this.colorMask_i == that.colorMask_i;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.blendState_i, this.depthState_i, this.logicOp_i, this.assemblyRasterState, this.colorMask_i, this.renderPass);
   }

   public static PipelineState.BlendInfo defaultBlendInfo() {
      return new PipelineState.BlendInfo(true, 6, 7, 1, 0, 0);
   }

   public abstract static class AssemblyRasterState {
      public static final int POLYGON_MODE_MASK = 7;
      public static final int TOPOLOGY_OFFSET = 3;
      public static final int TOPOLOGY_BITS = 4;
      public static final int TOPOLOGY_MASK = 31;
      public static final int CULL_MODE_OFFSET = 7;
      public static final int CULL_MODE_BITS = 2;
      public static final int CULL_MODE_MASK = 3;

      public static int encode(boolean cull, int topology, int polygonMode) {
         int state = polygonMode | topology << 3;
         return state | (cull ? 2 : 0) << 7;
      }

      public static int decodeTopology(int state) {
         return state >>> 3 & 31;
      }

      public static int decodePolygonMode(int state) {
         return state & 7;
      }

      public static int decodeCullMode(int state) {
         return state >>> 7 & 3;
      }
   }

   public static class BlendInfo {
      public boolean enabled;
      public int srcRgbFactor;
      public int dstRgbFactor;
      public int srcAlphaFactor;
      public int dstAlphaFactor;
      public int blendOp;

      public BlendInfo(boolean enabled, int srcRgbFactor, int dstRgbFactor, int srcAlphaFactor, int dstAlphaFactor, int blendOp) {
         this.enabled = enabled;
         this.srcRgbFactor = srcRgbFactor;
         this.dstRgbFactor = dstRgbFactor;
         this.srcAlphaFactor = srcAlphaFactor;
         this.dstAlphaFactor = dstAlphaFactor;
         this.blendOp = blendOp;
      }

      public void setBlendFunction(int sourceFactor, int destFactor) {
         this.srcRgbFactor = glToVulkanBlendFactor(sourceFactor);
         this.srcAlphaFactor = glToVulkanBlendFactor(sourceFactor);
         this.dstRgbFactor = glToVulkanBlendFactor(destFactor);
         this.dstAlphaFactor = glToVulkanBlendFactor(destFactor);
      }

      public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
         this.srcRgbFactor = glToVulkanBlendFactor(srcRgb);
         this.srcAlphaFactor = glToVulkanBlendFactor(srcAlpha);
         this.dstRgbFactor = glToVulkanBlendFactor(dstRgb);
         this.dstAlphaFactor = glToVulkanBlendFactor(dstAlpha);
      }

      public void setBlendOp(int i) {
         this.blendOp = glToVulkanBlendOp(i);
      }

      public int createBlendState() {
         return PipelineState.BlendState.getState(this);
      }

      private static int glToVulkanBlendOp(int value) {
         return switch (value) {
            case 32774 -> 0;
            case 32775 -> 3;
            case 32776 -> 4;
            default -> throw new RuntimeException("unknown blend factor: " + value);
            case 32778 -> 1;
            case 32779 -> 2;
         };
      }

      private static int glToVulkanBlendFactor(int value) {
         return switch (value) {
            case 0 -> 0;
            case 1 -> 1;
            case 768 -> 2;
            case 769 -> 3;
            case 770 -> 6;
            case 771 -> 7;
            case 772 -> 8;
            case 773 -> 9;
            case 774 -> 4;
            case 775 -> 5;
            case 776 -> 14;
            default -> throw new RuntimeException("unknown blend factor: " + value);
         };
      }
   }

   public static class BlendState {
      public static final int SRC_RGB_OFFSET = 0;
      public static final int DST_RGB_OFFSET = 5;
      public static final int SRC_A_OFFSET = 10;
      public static final int DST_A_OFFSET = 15;
      public static final int FUN_OFFSET = 20;
      public static final int ENABLE_BIT = 16777216;
      public static final int OP_MASK = 15;
      public static final int FACTOR_MASK = 31;

      public static int getState(PipelineState.BlendInfo blendInfo) {
         int s = 0;
         s |= blendInfo.enabled ? 16777216 : 0;
         s |= encode(blendInfo.srcRgbFactor, 0, 31);
         s |= encode(blendInfo.dstRgbFactor, 5, 31);
         s |= encode(blendInfo.srcAlphaFactor, 10, 31);
         s |= encode(blendInfo.dstAlphaFactor, 15, 31);
         return s | encode(blendInfo.blendOp, 20, 15);
      }

      public static boolean enable(int i) {
         return (i & 16777216) != 0;
      }

      public static int encode(int i, int offset, int mask) {
         return (i & mask) << offset;
      }

      public static int decode(int i, int offset, int bits) {
         return i >>> offset & bits;
      }

      public static int getSrcRgbFactor(int s) {
         return decode(s, 0, 31);
      }

      public static int getDstRgbFactor(int s) {
         return decode(s, 5, 31);
      }

      public static int getSrcAlphaFactor(int s) {
         return decode(s, 10, 31);
      }

      public static int getDstAlphaFactor(int s) {
         return decode(s, 15, 31);
      }

      public static int blendOp(int state) {
         return decode(state, 20, 15);
      }
   }

   public abstract static class ColorMask {
      public static int getColorMask(boolean r, boolean g, boolean b, boolean a) {
         return (r ? 1 : 0) | (g ? 2 : 0) | (b ? 4 : 0) | (a ? 8 : 0);
      }
   }

   public abstract static class DepthState {
      public static final int DEPTH_TEST_BIT = 1;
      public static final int DEPTH_MASK_BIT = 2;
      public static final int DEPTH_FUN_OFFSET = 2;
      public static final int DEPTH_FUN_BITS = 4;

      public static boolean depthTest(int i) {
         return (i & 1) != 0;
      }

      public static boolean depthMask(int i) {
         return (i & 2) != 0;
      }

      public static int encodeDepthFun(int glFun) {
         int fun = glToVulkan(glFun);
         return fun << 2;
      }

      public static int decodeDepthFun(int state) {
         return state >>> 2;
      }

      private static int glToVulkan(int value) {
         return switch (value) {
            case 512 -> 0;
            case 513 -> 1;
            case 514 -> 2;
            case 515 -> 3;
            case 516 -> 4;
            case 517 -> 5;
            case 518 -> 6;
            case 519 -> 7;
            default -> throw new RuntimeException("unknown blend factor: %d".formatted(value));
         };
      }
   }

   public abstract static class LogicOpState {
      public static final int ENABLE_BIT = 1;
      public static final int FUN_OFFSET = 1;
      public static final int FUN_BITS = 5;

      public static boolean enable(int i) {
         return (i & 1) != 0;
      }

      public static int encodeLogicOpFun(int glFun) {
         int fun = glToVulkan(glFun);
         return fun << 1;
      }

      public static int decodeFun(int state) {
         return state >>> 1;
      }

      public static int glToVulkan(int f) {
         return switch (f) {
            case 5377 -> 1;
            case 5378 -> 2;
            case 5379 -> 3;
            case 5380 -> 4;
            case 5381 -> 5;
            case 5382 -> 6;
            case 5383 -> 7;
            case 5384 -> 8;
            case 5385 -> 9;
            case 5386 -> 10;
            case 5387 -> 11;
            case 5388 -> 12;
            case 5389 -> 13;
            case 5390 -> 14;
            case 5391 -> 15;
            default -> 1;
         };
      }
   }
}
