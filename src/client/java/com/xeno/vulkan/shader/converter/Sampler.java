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


public class Sampler implements GLSLParser.Node {
   final Sampler.Type type;
   final String id;
   int binding;

   public Sampler(Sampler.Type type, String id) {
      this.type = type;
      this.id = id;
   }

   public void setBinding(int binding) {
      this.binding = binding;
   }

   @Override
   public String getStringValue() {
      return "layout(binding = %d) uniform %s %s;\n".formatted(this.binding, this.type.name, this.id);
   }

   public enum Type {
      SAMPLER_2D("sampler2D"),
      SAMPLER_CUBE("samplerCube"),
      I_SAMPLER_BUFFER("isamplerBuffer");

      public final String name;

      Type(String name) {
         this.name = name;
      }
   }
}
