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
package com.xeno.mixin.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import com.xeno.interfaces.VertexFormatMixed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexFormat.class)
public class VertexFormatMixin implements VertexFormatMixed {
   private int[] offsets;
   private ObjectArrayList<VertexFormatElement> fastList;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void injectList(List<VertexFormatElement> list, List<String> list2, IntList intList, int i, CallbackInfo ci) {
      ObjectArrayList<VertexFormatElement> fList = new ObjectArrayList();
      fList.addAll(list);
      this.fastList = fList;
      this.offsets = intList.toIntArray();
   }

   @Override
   public int getOffset(int i) {
      return this.offsets[i];
   }

   public VertexFormatElement getElement(int i) {
      return (VertexFormatElement)this.fastList.get(i);
   }

   @Override
   public List<VertexFormatElement> getFastList() {
      return this.fastList;
   }
}
