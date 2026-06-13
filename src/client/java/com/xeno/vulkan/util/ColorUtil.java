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
package com.xeno.vulkan.util;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class ColorUtil {
   private static final float COLOR_INV = 0.003921569F;
   static ColorUtil.ColorConsumer colorConsumer = new ColorUtil.DefaultColorConsumer();

   public static void useGammaCorrection(boolean b) {
      colorConsumer = b ? new ColorUtil.GammaColorConsumer() : new ColorUtil.DefaultColorConsumer();
   }

   public static int floatToInt(float f) {
      return (int)(f * 255.0F) & 0xFF;
   }

   public static float unpackColor(int c, int s) {
      return (c >> s & 0xFF) * 0.003921569F;
   }

   public static int BGRAtoRGBA(int v) {
      byte r = (byte)(v >> 16);
      byte b = (byte)v;
      return r & 0xFF | b << 16 & 0xFF0000 | v & -16711936;
   }

   public static float gamma(float f) {
      return (float)Math.pow(f, 2.2);
   }

   public static void setRGBA_Buffer(MappedBuffer buffer, float r, float g, float b, float a) {
      colorConsumer.setRGBA_Buffer(buffer, r, g, b, a);
   }

   public static void setRGBA_Buffer(FloatBuffer buffer, float r, float g, float b, float a) {
      colorConsumer.setRGBA_Buffer(buffer, r, g, b, a);
   }

   public static class ARGB {
      public static int pack(float r, float g, float b, float a) {
         return ColorUtil.floatToInt(a) << 24 | ColorUtil.floatToInt(r) << 16 | ColorUtil.floatToInt(g) << 8 | ColorUtil.floatToInt(b);
      }

      public static float unpackR(int color) {
         return ColorUtil.unpackColor(color, 16);
      }

      public static float unpackG(int color) {
         return ColorUtil.unpackColor(color, 8);
      }

      public static float unpackB(int color) {
         return ColorUtil.unpackColor(color, 0);
      }

      public static float unpackA(int color) {
         return ColorUtil.unpackColor(color, 24);
      }

      public static int multiplyAlpha(int color, float m) {
         int newA = ColorUtil.floatToInt(unpackA(color) * m);
         return color & 16777215 | newA << 24;
      }

      public static int multiplyRGB(int color, float m) {
         int alpha = color >>> 24 & 0xFF;
         int red = (int)((color >>> 16 & 0xFF) * m);
         int green = (int)((color >>> 8 & 0xFF) * m);
         int blue = (int)((color & 0xFF) * m);
         return alpha << 24 | red << 16 | green << 8 | blue;
      }

      public static int toRGBA(int color) {
         return color & -16711936 | color >> 16 & 0xFF | color << 16 & 0xFF0000;
      }
   }

   interface ColorConsumer {
      void setRGBA_Buffer(MappedBuffer var1, float var2, float var3, float var4, float var5);

      void setRGBA_Buffer(FloatBuffer var1, float var2, float var3, float var4, float var5);

      void setRGBA_Buffer(ByteBuffer var1, float var2, float var3, float var4, float var5);

      default void putColor(MappedBuffer buffer, float r, float g, float b, float a) {
         buffer.putFloat(0, r);
         buffer.putFloat(4, g);
         buffer.putFloat(8, b);
         buffer.putFloat(12, a);
      }

      default void putColor(FloatBuffer buffer, float r, float g, float b, float a) {
         buffer.put(0, r);
         buffer.put(1, g);
         buffer.put(2, b);
         buffer.put(3, a);
      }

      default void putColor(ByteBuffer buffer, float r, float g, float b, float a) {
         buffer.putFloat(0, r);
         buffer.putFloat(4, g);
         buffer.putFloat(8, b);
         buffer.putFloat(12, a);
      }
   }

   public static class DefaultColorConsumer implements ColorUtil.ColorConsumer {
      @Override
      public void setRGBA_Buffer(MappedBuffer buffer, float r, float g, float b, float a) {
         this.putColor(buffer, r, g, b, a);
      }

      @Override
      public void setRGBA_Buffer(FloatBuffer buffer, float r, float g, float b, float a) {
         this.putColor(buffer, r, g, b, a);
      }

      @Override
      public void setRGBA_Buffer(ByteBuffer buffer, float r, float g, float b, float a) {
         this.putColor(buffer, r, g, b, a);
      }
   }

   public static class GammaColorConsumer implements ColorUtil.ColorConsumer {
      @Override
      public void setRGBA_Buffer(MappedBuffer buffer, float r, float g, float b, float a) {
         r = ColorUtil.gamma(r);
         g = ColorUtil.gamma(g);
         b = ColorUtil.gamma(b);
         this.putColor(buffer, r, g, b, a);
      }

      @Override
      public void setRGBA_Buffer(FloatBuffer buffer, float r, float g, float b, float a) {
         r = ColorUtil.gamma(r);
         g = ColorUtil.gamma(g);
         b = ColorUtil.gamma(b);
         this.putColor(buffer, r, g, b, a);
      }

      @Override
      public void setRGBA_Buffer(ByteBuffer buffer, float r, float g, float b, float a) {
         r = ColorUtil.gamma(r);
         g = ColorUtil.gamma(g);
         b = ColorUtil.gamma(b);
         this.putColor(buffer, r, g, b, a);
      }
   }

   public static class RGBA {
      public static int pack(float r, float g, float b, float a) {
         return ColorUtil.floatToInt(a) << 24 | ColorUtil.floatToInt(b) << 16 | ColorUtil.floatToInt(g) << 8 | ColorUtil.floatToInt(r);
      }

      public static float unpackR(int color) {
         return ColorUtil.unpackColor(color, 24);
      }

      public static float unpackG(int color) {
         return ColorUtil.unpackColor(color, 16);
      }

      public static float unpackB(int color) {
         return ColorUtil.unpackColor(color, 8);
      }

      public static int fromArgb32(int i) {
         return i & -16711936 | (i & 0xFF0000) >> 16 | (i & 0xFF) << 16;
      }
   }
}
