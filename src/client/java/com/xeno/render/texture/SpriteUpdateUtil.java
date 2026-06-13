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
package com.xeno.render.texture;

import java.util.HashSet;
import java.util.Set;
import com.xeno.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

public abstract class SpriteUpdateUtil {
   private static final Set<VulkanImage> transitionedLayouts = new HashSet<>();

   public static void addTransitionedLayout(VulkanImage image) {
      transitionedLayouts.add(image);
   }

   public static void transitionLayouts() {
      if (!transitionedLayouts.isEmpty()) {
         VkCommandBuffer commandBuffer = ImageUploadHelper.INSTANCE.getOrStartCommandBuffer().handle;
         transitionedLayouts.forEach(image -> {
            MemoryStack stack = MemoryStack.stackPush();

            try {
               image.readOnlyLayout(stack, commandBuffer);
            } catch (Throwable t$) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable x2) {
                     t$.addSuppressed(x2);
                  }
               }

               throw t$;
            }

            if (stack != null) {
               stack.close();
            }
         });
         transitionedLayouts.clear();
      }
   }
}
