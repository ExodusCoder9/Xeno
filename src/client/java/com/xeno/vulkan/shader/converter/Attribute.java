package com.xeno.vulkan.shader.converter;

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


public class Attribute implements GLSLParser.Node {
   String ioType;
   String type;
   String id;
   int location;

   public Attribute(String ioType, String type, String id) {
      switch (ioType) {
         case "in":
         case "out":
            this.ioType = ioType;
            this.type = type;
            this.id = id;
            return;
         default:
            throw new IllegalArgumentException();
      }
   }

   public void setLocation(int location) {
      this.location = location;
   }

   @Override
   public String getStringValue() {
      return "layout(location = %d) %s %s %s;\n".formatted(this.location, this.ioType, this.type, this.id);
   }
}
