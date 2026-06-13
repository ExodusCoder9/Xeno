package com.xeno.vulkan.queue;

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


import java.nio.IntBuffer;
import java.util.stream.IntStream;
import com.xeno.Initializer;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkQueueFamilyProperties.Buffer;

public abstract class Queue {
   private static VkDevice device;
   private static Queue.QueueFamilyIndices queueFamilyIndices;
   private final VkQueue vkQueue;
   protected CommandPool commandPool;

   public synchronized CommandPool.CommandBuffer beginCommands() {
      MemoryStack stack = MemoryStack.stackPush();

      CommandPool.CommandBuffer var3;
      try {
         CommandPool.CommandBuffer commandBuffer = this.commandPool.getCommandBuffer(stack);
         commandBuffer.begin(stack);
         var3 = commandBuffer;
      } catch (Throwable var5) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }
         }

         throw var5;
      }

      if (stack != null) {
         stack.close();
      }

      return var3;
   }

   Queue(MemoryStack stack, int familyIndex) {
      this(stack, familyIndex, true);
   }

   Queue(MemoryStack stack, int familyIndex, boolean initCommandPool) {
      PointerBuffer pQueue = stack.mallocPointer(1);
      VK10.vkGetDeviceQueue(DeviceManager.vkDevice, familyIndex, 0, pQueue);
      this.vkQueue = new VkQueue(pQueue.get(0), DeviceManager.vkDevice);
      if (initCommandPool) {
         this.commandPool = new CommandPool(familyIndex);
      }
   }

   public long submitCommands(CommandPool.CommandBuffer commandBuffer) {
      return this.submitCommands(commandBuffer, false);
   }

   public synchronized long submitCommands(CommandPool.CommandBuffer commandBuffer, boolean useSemaphore) {
      MemoryStack stack = MemoryStack.stackPush();

      long var4;
      try {
         var4 = commandBuffer.submitCommands(stack, this.vkQueue, useSemaphore);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }

      return var4;
   }

   public VkQueue vkQueue() {
      return this.vkQueue;
   }

   public void cleanUp() {
      if (this.commandPool != null) {
         this.commandPool.cleanUp();
      }
   }

   public void waitIdle() {
      VK10.vkQueueWaitIdle(this.vkQueue);
   }

   public CommandPool getCommandPool() {
      return this.commandPool;
   }

   public static Queue.QueueFamilyIndices getQueueFamilies() {
      if (device == null) {
         device = Vulkan.getVkDevice();
      }

      if (queueFamilyIndices == null) {
         queueFamilyIndices = findQueueFamilies(device.getPhysicalDevice());
      }

      return queueFamilyIndices;
   }

   public static Queue.QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {
      Queue.QueueFamilyIndices indices = new Queue.QueueFamilyIndices();
      MemoryStack stack = MemoryStack.stackPush();

      Queue.QueueFamilyIndices var13;
      try {
         IntBuffer queueFamilyCount = stack.ints(0);
         VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);
         Buffer queueFamilies = VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);
         VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);
         IntBuffer presentSupport = stack.ints(0);

         for (int i = 0; i < queueFamilies.capacity(); i++) {
            int queueFlags = ((VkQueueFamilyProperties)queueFamilies.get(i)).queueFlags();
            if ((queueFlags & 1) != 0) {
               indices.graphicsFamily = i;
               KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);
               if (presentSupport.get(0) == 1) {
                  indices.presentFamily = i;
               }
            } else if ((queueFlags & 1) == 0 && (queueFlags & 2) != 0) {
               indices.computeFamily = i;
            } else if ((queueFlags & 3) == 0 && (queueFlags & 4) != 0) {
               indices.transferFamily = i;
            }

            if (indices.presentFamily == -1) {
               KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);
               if (presentSupport.get(0) == 1) {
                  indices.presentFamily = i;
               }
            }

            if (indices.isComplete()) {
               break;
            }
         }

         if (indices.presentFamily == -1) {
            indices.presentFamily = indices.computeFamily;
            Initializer.LOGGER.warn("Using compute queue as present fallback");
         }

         if (indices.transferFamily == -1) {
            int transferIndex = -1;

            for (int i = 0; i < queueFamilies.capacity(); i++) {
               int queueFlags = ((VkQueueFamilyProperties)queueFamilies.get(i)).queueFlags();
               if ((queueFlags & 4) != 0) {
                  if (transferIndex == -1) {
                     transferIndex = i;
                  }

                  if ((queueFlags & 1) == 0) {
                     indices.transferFamily = i;
                     if (i != indices.computeFamily) {
                        break;
                     }

                     transferIndex = i;
                  }
               }
            }

            if (transferIndex == -1) {
               throw new RuntimeException("Failed to find queue family with transfer support");
            }

            indices.transferFamily = transferIndex;
         }

         if (indices.computeFamily == -1) {
            for (int i = 0; i < queueFamilies.capacity(); i++) {
               int queueFlags = ((VkQueueFamilyProperties)queueFamilies.get(i)).queueFlags();
               if ((queueFlags & 2) != 0) {
                  indices.computeFamily = i;
                  break;
               }
            }
         }

         if (indices.graphicsFamily == -1) {
            throw new RuntimeException("Unable to find queue family with graphics support.");
         }

         if (indices.presentFamily == -1) {
            throw new RuntimeException("Unable to find queue family with present support.");
         }

         if (indices.computeFamily == -1) {
            throw new RuntimeException("Unable to find queue family with compute support.");
         }

         var13 = indices;
      } catch (Throwable var10) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (stack != null) {
         stack.close();
      }

      return var13;
   }

   public enum Family {
      Graphics,
      Transfer,
      Compute;

      Family() {
      }
   }

   public static class QueueFamilyIndices {
      public int graphicsFamily = -1;
      public int presentFamily = -1;
      public int transferFamily = -1;
      public int computeFamily = -1;

      public QueueFamilyIndices() {
      }

      public boolean isComplete() {
         return this.graphicsFamily != -1 && this.presentFamily != -1 && this.transferFamily != -1 && this.computeFamily != -1;
      }

      public boolean isSuitable() {
         return this.graphicsFamily != -1 && this.presentFamily != -1;
      }

      public int[] unique() {
         return IntStream.of(this.graphicsFamily, this.presentFamily, this.transferFamily, this.computeFamily).distinct().toArray();
      }

      public int[] array() {
         return new int[]{this.graphicsFamily, this.presentFamily};
      }
   }
}
