package com.xeno.render.vertex;

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


import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormatElement.Type;

public class CustomVertexFormat {
   public static final VertexFormatElement ELEMENT_POSITION_INT16 = new VertexFormatElement(0, 0, Type.SHORT, false, 4);
   public static final VertexFormatElement ELEMENT_COLOR_UINT = new VertexFormatElement(1, 0, Type.UINT, true, 1);
   public static final VertexFormatElement ELEMENT_UV0_UINT16 = new VertexFormatElement(2, 0, Type.USHORT, false, 2);
   private static float POSITION_OFFSET = 4.0F;
   public static final VertexFormat COMPRESSED_TERRAIN = VertexFormat.builder()
      .add("Position", ELEMENT_POSITION_INT16)
      .add("UV0", ELEMENT_UV0_UINT16)
      .add("Color", ELEMENT_COLOR_UINT)
      .build();
   public static final VertexFormat TERRAIN = VertexFormat.builder()
      .add("Position", VertexFormatElement.POSITION)
      .add("Color", VertexFormatElement.COLOR)
      .add("UV0", VertexFormatElement.UV0)
      .add("UV2", VertexFormatElement.UV2)
      .add("Normal", VertexFormatElement.NORMAL)
      .padding(1)
      .build();
   public static final VertexFormat NONE = VertexFormat.builder().build();

   public CustomVertexFormat() {
   }

   public static void setPositionOffset(float positionOffset) {
      POSITION_OFFSET = positionOffset;
   }

   public static float getPositionOffset() {
      return POSITION_OFFSET;
   }
}
