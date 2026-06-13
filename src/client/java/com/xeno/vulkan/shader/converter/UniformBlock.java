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
package com.xeno.vulkan.shader.converter;

import java.util.ArrayList;
import java.util.List;

public class UniformBlock implements GLSLParser.Node {
   int binding;
   String name;
   String alias;
   List<UniformBlock.Field> fields = new ArrayList<>();

   public UniformBlock(String name) {
      this.name = name;
   }

   public void addField(UniformBlock.Field field) {
      this.fields.add(field);
   }

   public void setBinding(int binding) {
      this.binding = binding;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   @Override
   public String getStringValue() {
      StringBuilder sb = new StringBuilder();
      sb.append("layout(binding = %d) uniform %s {\n".formatted(this.binding, this.name));
      int i = 0;

      for (int fieldsSize = this.fields.size(); i < fieldsSize; i++) {
         UniformBlock.Field field = this.fields.get(i);
         sb.append("\t%s %s;".formatted(field.type, field.name));
         if (i < fieldsSize - 1) {
            sb.append("\n");
         }
      }

      sb.append("\n}");
      if (this.alias != null) {
         sb.append(" %s ".formatted(this.alias));
      }

      sb.append(";\n");
      return sb.toString();
   }

   public static class Field {
      final String type;
      final String name;

      public Field(String type, String name) {
         this.type = type;
         this.name = name;
      }
   }
}
