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
package com.xeno.vulkan.device;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.xeno.Initializer;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.queue.ComputeQueue;
import com.xeno.vulkan.queue.GraphicsQueue;
import com.xeno.vulkan.queue.PresentQueue;
import com.xeno.vulkan.queue.Queue;
import com.xeno.vulkan.queue.TransferQueue;
import com.xeno.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo.Buffer;

public abstract class DeviceManager {
   public static List<Device> availableDevices;
   public static List<Device> suitableDevices;
   public static VkPhysicalDevice physicalDevice;
   public static VkDevice vkDevice;
   public static Device device;
   public static VkPhysicalDeviceProperties deviceProperties;
   public static VkPhysicalDeviceMemoryProperties memoryProperties;
   public static DeviceManager.SurfaceProperties surfaceProperties;
   static GraphicsQueue graphicsQueue;
   static PresentQueue presentQueue;
   static TransferQueue transferQueue;
   static ComputeQueue computeQueue;

   public static void init(VkInstance instance) {
      try {
         getSuitableDevices(instance);
         pickPhysicalDevice();
         createLogicalDevice();
      } catch (Exception e) {
         Initializer.LOGGER.info(getAvailableDevicesInfo());
         throw new RuntimeException(e);
      }
   }

   static List<Device> getAvailableDevices(VkInstance instance) {
      MemoryStack stack = MemoryStack.stackPush();

      List var10;
      label50: {
         Object var11;
         try {
            List<Device> devices = new ObjectArrayList();
            IntBuffer deviceCount = stack.ints(0);
            VK10.vkEnumeratePhysicalDevices(instance, deviceCount, null);
            if (deviceCount.get(0) == 0) {
               var10 = List.of();
               break label50;
            }

            PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));
            VK10.vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

            for (int i = 0; i < ppPhysicalDevices.capacity(); i++) {
               VkPhysicalDevice currentDevice = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);
               Device device = new Device(currentDevice);
               devices.add(device);
            }

            var11 = devices;
         } catch (Throwable var9) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (stack != null) {
            stack.close();
         }

         return (List<Device>)var11;
      }

      if (stack != null) {
         stack.close();
      }

      return var10;
   }

   static void getSuitableDevices(VkInstance instance) {
      availableDevices = getAvailableDevices(instance);
      List<Device> devices = new ObjectArrayList();

      for (Device device : availableDevices) {
         if (isDeviceSuitable(device)) {
            devices.add(device);
         }
      }

      suitableDevices = devices;
   }

   public static void pickPhysicalDevice() {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         int deviceIdx = Initializer.CONFIG.device;
         if (deviceIdx >= 0 && deviceIdx < suitableDevices.size()) {
            device = suitableDevices.get(deviceIdx);
         } else {
            device = autoPickDevice();
            Initializer.CONFIG.device = -1;
         }

         physicalDevice = device.physicalDevice;
         deviceProperties = device.properties;
         memoryProperties = VkPhysicalDeviceMemoryProperties.malloc();
         VK10.vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
         surfaceProperties = querySurfaceProperties(physicalDevice, stack);
      } catch (Throwable var4) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var3) {
               var4.addSuppressed(var3);
            }
         }

         throw var4;
      }

      if (stack != null) {
         stack.close();
      }
   }

   static Device autoPickDevice() {
      ArrayList<Device> integratedGPUs = new ArrayList<>();
      ArrayList<Device> otherDevices = new ArrayList<>();
      boolean flag = false;
      Device currentDevice = null;

      for (Device device : suitableDevices) {
         currentDevice = device;
         int deviceType = device.properties.deviceType();
         if (deviceType == 2) {
            flag = true;
            break;
         }

         if (deviceType == 1) {
            integratedGPUs.add(device);
         } else {
            otherDevices.add(device);
         }
      }

      if (!flag) {
         if (!integratedGPUs.isEmpty()) {
            currentDevice = integratedGPUs.get(0);
         } else {
            if (otherDevices.isEmpty()) {
               throw new IllegalStateException("Failed to find a suitable GPU");
            }

            currentDevice = otherDevices.get(0);
         }
      }

      return currentDevice;
   }

   public static void createLogicalDevice() {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         Queue.QueueFamilyIndices indices = Queue.findQueueFamilies(physicalDevice);
         int[] uniqueQueueFamilies = indices.unique();
         Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.length, stack);

         for (int i = 0; i < uniqueQueueFamilies.length; i++) {
            VkDeviceQueueCreateInfo queueCreateInfo = (VkDeviceQueueCreateInfo)queueCreateInfos.get(i);
            queueCreateInfo.sType(2);
            queueCreateInfo.queueFamilyIndex(uniqueQueueFamilies[i]);
            queueCreateInfo.pQueuePriorities(stack.floats(1.0F));
         }

         VkPhysicalDeviceVulkan11Features deviceVulkan11Features = VkPhysicalDeviceVulkan11Features.calloc(stack);
         deviceVulkan11Features.sType$Default();
         deviceVulkan11Features.shaderDrawParameters(device.isDrawIndirectSupported());
         VkPhysicalDeviceFeatures2 deviceFeatures = VkPhysicalDeviceFeatures2.calloc(stack);
         deviceFeatures.sType$Default();
         deviceFeatures.features().samplerAnisotropy(device.availableFeatures.features().samplerAnisotropy());
         deviceFeatures.features().logicOp(device.availableFeatures.features().logicOp());
         deviceFeatures.features().multiDrawIndirect(device.isDrawIndirectSupported());
         if (device.availableFeatures.features().wideLines()) {
            deviceFeatures.features().wideLines(true);
            VRenderSystem.canSetLineWidth = true;
         }

         VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
         createInfo.sType$Default();
         createInfo.sType(3);
         createInfo.pQueueCreateInfos(queueCreateInfos);
         createInfo.pEnabledFeatures(deviceFeatures.features());
         createInfo.pNext(deviceVulkan11Features);
         VkPhysicalDeviceDynamicRenderingFeaturesKHR dynamicRenderingFeaturesKHR = VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack);
         dynamicRenderingFeaturesKHR.sType$Default();
         dynamicRenderingFeaturesKHR.dynamicRendering(true);
         deviceVulkan11Features.pNext(dynamicRenderingFeaturesKHR.address());
         HashSet<String> enabledExtensions = new HashSet<>(Vulkan.REQUIRED_EXTENSION);
         if (device.hasExtension("VK_KHR_portability_subset")) {
            enabledExtensions.add("VK_KHR_portability_subset");
         }

         createInfo.ppEnabledExtensionNames(VUtil.asPointerBuffer(enabledExtensions));
         PointerBuffer pDevice = stack.pointers(0L);
         int res = VK10.vkCreateDevice(physicalDevice, createInfo, null, pDevice);
         Vulkan.checkResult(res, "Failed to create logical device");
         vkDevice = new VkDevice(pDevice.get(0), physicalDevice, createInfo, VK12.VK_API_VERSION_1_2);
         graphicsQueue = new GraphicsQueue(stack, indices.graphicsFamily);
         transferQueue = new TransferQueue(stack, indices.transferFamily);
         presentQueue = new PresentQueue(stack, indices.presentFamily);
         computeQueue = new ComputeQueue(stack, indices.computeFamily);
      } catch (Throwable var11) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }
         }

         throw var11;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private static PointerBuffer getRequiredExtensions() {
      return GLFWVulkan.glfwGetRequiredInstanceExtensions();
   }

   private static boolean isDeviceSuitable(Device device) {
      MemoryStack stack = MemoryStack.stackPush();

      boolean var8;
      try {
         Queue.QueueFamilyIndices indices = Queue.findQueueFamilies(device.physicalDevice);
         org.lwjgl.vulkan.VkExtensionProperties.Buffer availableExtensions = device.extensionProperties;
         boolean extensionsSupported = availableExtensions.stream()
            .map(VkExtensionProperties::extensionNameString)
            .collect(Collectors.toSet())
            .containsAll(Vulkan.REQUIRED_EXTENSION);
         boolean swapChainAdequate = false;
         if (extensionsSupported) {
            DeviceManager.SurfaceProperties surfaceProperties = querySurfaceProperties(device.physicalDevice, stack);
            swapChainAdequate = surfaceProperties.formats.hasRemaining() && surfaceProperties.presentModes.hasRemaining();
         }

         VkPhysicalDeviceFeatures supportedFeatures = device.availableFeatures.features();
         boolean anisotropicFilterSupported = supportedFeatures.samplerAnisotropy();
         var8 = indices.isSuitable() && extensionsSupported && swapChainAdequate;
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

      return var8;
   }

   public static org.lwjgl.vulkan.VkExtensionProperties.Buffer getAvailableExtension(MemoryStack stack, VkPhysicalDevice device) {
      IntBuffer extensionCount = stack.ints(0);
      VK10.vkEnumerateDeviceExtensionProperties(device, (String)null, extensionCount, null);
      org.lwjgl.vulkan.VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);
      VK10.vkEnumerateDeviceExtensionProperties(device, (String)null, extensionCount, availableExtensions);
      return availableExtensions;
   }

   public static int findDepthFormat(boolean use24BitsDepthFormat) {
      int[] formats = use24BitsDepthFormat ? new int[]{129, 125, 126, 130} : new int[]{126, 130};
      return findSupportedFormat(0, 512, formats);
   }

   private static int findSupportedFormat(int tiling, int features, int... formatCandidates) {
      MemoryStack stack = MemoryStack.stackPush();

      int var12;
      label64: {
         label65: {
            try {
               VkFormatProperties props = VkFormatProperties.calloc(stack);

               for (int format : formatCandidates) {
                  VK10.vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props);
                  if (tiling == 1 && (props.linearTilingFeatures() & features) == features) {
                     var12 = format;
                     break label64;
                  }

                  if (tiling == 0 && (props.optimalTilingFeatures() & features) == features) {
                     var12 = format;
                     break label65;
                  }
               }
            } catch (Throwable var11) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (stack != null) {
               stack.close();
            }

            throw new RuntimeException("Failed to find supported format");
         }

         if (stack != null) {
            stack.close();
         }

         return var12;
      }

      if (stack != null) {
         stack.close();
      }

      return var12;
   }

   public static String getAvailableDevicesInfo() {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("\n");
      if (availableDevices == null) {
         stringBuilder.append("\tDevice Manager not initialized");
         return stringBuilder.toString();
      }

      if (availableDevices.isEmpty()) {
         stringBuilder.append("\tNo available device found");
      }

      for (Device device : availableDevices) {
         stringBuilder.append("\tDevice: %s\n".formatted(device.deviceName));
         stringBuilder.append("\t\tVulkan Version: %s\n".formatted(device.vkVersion));
         stringBuilder.append("\t\t");
         Set<String> unsupportedExtensions = device.getUnsupportedExtensions(Vulkan.REQUIRED_EXTENSION);
         if (unsupportedExtensions.isEmpty()) {
            stringBuilder.append("All required extensions are supported\n");
         } else {
            stringBuilder.append("Unsupported extension: %s\n".formatted(unsupportedExtensions));
         }
      }

      return stringBuilder.toString();
   }

   public static void destroy() {
      graphicsQueue.cleanUp();
      transferQueue.cleanUp();
      computeQueue.cleanUp();
      VK10.vkDestroyDevice(vkDevice, null);
   }

   public static GraphicsQueue getGraphicsQueue() {
      return graphicsQueue;
   }

   public static PresentQueue getPresentQueue() {
      return presentQueue;
   }

   public static TransferQueue getTransferQueue() {
      return transferQueue;
   }

   public static ComputeQueue getComputeQueue() {
      return computeQueue;
   }

   public static DeviceManager.SurfaceProperties querySurfaceProperties(VkPhysicalDevice device, MemoryStack stack) {
      long surface = Vulkan.getSurface();
      DeviceManager.SurfaceProperties details = new DeviceManager.SurfaceProperties();
      details.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
      KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, details.capabilities);
      IntBuffer count = stack.ints(0);
      KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, null);
      if (count.get(0) != 0) {
         details.formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
         KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, details.formats);
      }

      KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, null);
      if (count.get(0) != 0) {
         details.presentModes = stack.mallocInt(count.get(0));
         KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, details.presentModes);
      }

      return details;
   }

   public static class SurfaceProperties {
      public VkSurfaceCapabilitiesKHR capabilities;
      public org.lwjgl.vulkan.VkSurfaceFormatKHR.Buffer formats;
      public IntBuffer presentModes;
   }
}
