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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;
import org.lwjgl.vulkan.VkExtensionProperties.Buffer;

public class Device {
   final VkPhysicalDevice physicalDevice;
   final VkPhysicalDeviceProperties properties;
   final Buffer extensionProperties;
   public final VkPhysicalDeviceFeatures2 availableFeatures;
   public final VkPhysicalDeviceVulkan11Features availableFeatures11;
   private final int vendorId;
   public final String vendorIdString;
   public final String deviceName;
   public final String driverVersion;
   public final String vkVersion;
   private boolean drawIndirectSupported;

   public Device(VkPhysicalDevice device) {
      this.physicalDevice = device;
      this.properties = VkPhysicalDeviceProperties.malloc();
      VK10.vkGetPhysicalDeviceProperties(this.physicalDevice, this.properties);
      this.extensionProperties = getAvailableExtension(device);
      this.vendorId = this.properties.vendorID();
      this.vendorIdString = decodeVendor(this.properties.vendorID());
      this.deviceName = this.properties.deviceNameString();
      this.driverVersion = decodeDvrVersion(this.properties.driverVersion(), this.properties.vendorID());
      this.vkVersion = decDefVersion(this.properties.apiVersion());
      this.availableFeatures = VkPhysicalDeviceFeatures2.calloc();
      this.availableFeatures.sType$Default();
      this.availableFeatures11 = VkPhysicalDeviceVulkan11Features.malloc();
      this.availableFeatures11.sType$Default();
      this.availableFeatures.pNext(this.availableFeatures11);
      VK11.vkGetPhysicalDeviceFeatures2(this.physicalDevice, this.availableFeatures);
      if (this.availableFeatures.features().multiDrawIndirect() && this.availableFeatures11.shaderDrawParameters()) {
         this.drawIndirectSupported = true;
      }
   }

   public boolean hasExtension(String s) {
      return this.extensionProperties.stream().anyMatch(pr -> pr.extensionNameString().equals(s));
   }

   public Set<String> getUnsupportedExtensions(Set<String> requiredExtensions) {
      int[] extensionCount = new int[1];
      VK10.vkEnumerateDeviceExtensionProperties(this.physicalDevice, (String)null, extensionCount, null);
      Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount[0]);
      VK10.vkEnumerateDeviceExtensionProperties(this.physicalDevice, (String)null, extensionCount, availableExtensions);
      Set<String> extensions = availableExtensions.stream().<String>map(VkExtensionProperties::extensionNameString).collect(Collectors.toSet());
      availableExtensions.free();
      Set<String> unsupportedExtensions = new HashSet<>(requiredExtensions);
      unsupportedExtensions.removeAll(extensions);
      return unsupportedExtensions;
   }

   public boolean isDrawIndirectSupported() {
      return this.drawIndirectSupported;
   }

   public boolean isAMD() {
      return this.vendorId == 4130 || this.vendorId == 4098;
   }

   public boolean isNvidia() {
      return this.vendorId == 4318;
   }

   public boolean isIntel() {
      return this.vendorId == 32902;
   }

   private static String decodeVendor(int i) {
      return switch (i) {
         case 4098, 4130 -> "AMD";
         case 4112 -> "Imagination Technologies";
         case 4203 -> "Apple";
         case 4318 -> "Nvidia";
         case 5045 -> "ARM";
         case 5348 -> "Broadcom";
         case 6880 -> "Google";
         case 20803 -> "Qualcomm";
         case 32902 -> "Intel";
         case 65541 -> "Mesa";
         default -> "undef";
      };
   }

   static String decDefVersion(int v) {
      return VK10.VK_VERSION_MAJOR(v) + "." + VK10.VK_VERSION_MINOR(v) + "." + VK10.VK_VERSION_PATCH(v);
   }

   private static String decodeDvrVersion(int v, int i) {
      return switch (i) {
         case 4098, 4130 -> decDefVersion(v);
         case 4318 -> decodeNvidia(v);
         case 32902 -> decIntelVersion(v);
         default -> decDefVersion(v);
      };
   }

   private static String decIntelVersion(int v) {
      return GLFW.glfwGetPlatform() == 393217 ? (v >>> 14) + "." + (v & 16383) : decDefVersion(v);
   }

   private static String decodeNvidia(int v) {
      return (v >>> 22 & 1023) + "." + (v >>> 14 & 0xFF) + "." + (v >>> 6 & 0xFF) + "." + (v & 0xFF);
   }

   public static Buffer getAvailableExtension(VkPhysicalDevice device) {
      int[] extensionCount = new int[1];
      VK10.vkEnumerateDeviceExtensionProperties(device, (String)null, extensionCount, null);
      Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount[0]);
      VK10.vkEnumerateDeviceExtensionProperties(device, (String)null, extensionCount, availableExtensions);
      return availableExtensions;
   }
}
