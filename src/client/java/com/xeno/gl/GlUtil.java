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
package com.xeno.gl;

import java.nio.ByteBuffer;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.shader.SPIRVUtils;
import com.xeno.vulkan.util.MemoryAccess;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryUtil;

public abstract class GlUtil {
   public static SPIRVUtils.ShaderKind extToShaderKind(String ext) {
      return switch (ext) {
         case ".vsh" -> SPIRVUtils.ShaderKind.VERTEX_SHADER;
         case ".fsh" -> SPIRVUtils.ShaderKind.FRAGMENT_SHADER;
         default -> throw new RuntimeException("unknown shader type: " + ext);
      };
   }

   public static ByteBuffer RGBtoRGBA_buffer(ByteBuffer in) {
      Validate.isTrue(in.remaining() % 3 == 0, "Unexpected buffer stride", new Object[0]);
      int outSize = in.remaining() * 4 / 3;
      ByteBuffer out = MemoryUtil.memAlloc(outSize);
      int j = 0;

      for (int i = 0; i < outSize; j += 3) {
         out.put(i, in.get(j));
         out.put(i + 1, in.get(j + 1));
         out.put(i + 2, in.get(j + 2));
         out.put(i + 3, (byte)-1);
         i += 4;
      }

      return out;
   }

   public static ByteBuffer BGRAtoRGBA_buffer(ByteBuffer in) {
      Validate.isTrue(in.remaining() % 4 == 0, "Unexpected buffer stride", new Object[0]);
      int outSize = in.remaining();
      ByteBuffer out = MemoryUtil.memAlloc(outSize);
      long ptr = MemoryUtil.memAddress0(out);
      long srcPtr = MemoryUtil.memAddress0(in);

      for (int i = 0; i < outSize; i += 4) {
         int color = MemoryAccess.memGetInt(srcPtr + i);
         color = color << 24 & 0xFF000000 | color >> 8 & 16777215;
         MemoryAccess.memPutInt(ptr + i, color);
      }

      return out;
   }

   public static int vulkanFormat(int glFormat, int type) {
      return switch (glFormat) {
         case 6402, 33190, 33191, 36012 -> Vulkan.getDefaultDepthFormat();
         case 6403 -> {
            switch (type) {
               case 5121:
                  yield 9;
               default:
                  throw new IllegalStateException("Unexpected type: " + type);
            }
         }
         case 6408, 32856 -> {
            switch (type) {
               case 5120:
                  yield 37;
               case 5121:
                  yield 37;
               case 32821:
               case 33639:
                  yield 37;
               default:
                  throw new IllegalStateException("Unexpected type: " + type);
            }
         }
         case 32993 -> {
            switch (type) {
               case 5120:
                  yield 44;
               case 5121:
                  yield 44;
               case 32821:
               case 33639:
                  yield 44;
               default:
                  throw new IllegalStateException("Unexpected type: " + type);
            }
         }
         case 33639 -> {
            switch (type) {
               case 5120:
                  yield 37;
               case 5121:
                  yield 41;
               default:
                  throw new IllegalStateException("Unexpected type: " + type);
            }
         }
         default -> throw new IllegalStateException("Unexpected format: " + glFormat);
      };
   }

   public static int vulkanFormat(int glInternalFormat) {
      return switch (glInternalFormat) {
         case 6402, 33190, 36012 -> Vulkan.getDefaultDepthFormat();
         case 33639 -> 41;
         default -> throw new IllegalStateException("Unexpected value: " + glInternalFormat);
      };
   }

   public static int getGlFormat(int vFormat) {
      return switch (vFormat) {
         case 9 -> 6403;
         case 16 -> 33319;
         case 37 -> 6408;
         case 44 -> 32993;
         default -> throw new IllegalStateException("Unexpected value: " + vFormat);
      };
   }
}
