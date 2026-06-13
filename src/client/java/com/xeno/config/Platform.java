package com.xeno.config;

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


import net.minecraft.util.Util;
import net.minecraft.util.Util.OS;
import com.xeno.Initializer;
import org.apache.commons.lang3.SystemUtils;
import org.lwjgl.glfw.GLFW;

public abstract class Platform {
   public static final OS OS = Util.getPlatform();
   private static int activePlat;
   private static String activeDE;

   public Platform() {
   }

   public static void init() {
      activePlat = getSupportedPlat();
      activeDE = determineDE();
      GLFW.glfwInitHint(327683, activePlat);
      Initializer.LOGGER.info("Selecting Platform: {}", getStringFromPlat());
      Initializer.LOGGER.info("GLFW: {}", GLFW.glfwGetVersionString());
      GLFW.glfwInit();
   }

   private static int determineLinuxDisplayServer() {
      String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
      if (xdgSessionType == null) {
         return 393216;
      }

      return switch (xdgSessionType) {
         case "wayland" -> Initializer.CONFIG.useWayland ? 393219 : 393220;
         case "x11" -> 393220;
         default -> 393216;
      };
   }

   private static int getSupportedPlat() {
      if (SystemUtils.IS_OS_WINDOWS) {
         return 393217;
      } else if (SystemUtils.IS_OS_MAC_OSX) {
         return 393218;
      } else {
         return SystemUtils.IS_OS_LINUX ? determineLinuxDisplayServer() : 393216;
      }
   }

   private static String getStringFromPlat() {
      return switch (activePlat) {
         case 393216 -> "ANDROID";
         case 393217 -> "WIN32";
         case 393218 -> "MACOS";
         case 393219 -> "WAYLAND";
         case 393220 -> "X11";
         default -> throw new IllegalStateException("Unexpected value: " + activePlat);
      };
   }

   private static String determineDE() {
      String xdgSessionDesktop = System.getenv("XDG_SESSION_DESKTOP");
      String xdgCurrentDesktop = System.getenv("XDG_CURRENT_DESKTOP");
      if (xdgSessionDesktop != null) {
         return xdgSessionDesktop.toLowerCase();
      } else {
         return xdgCurrentDesktop != null ? xdgCurrentDesktop.toLowerCase() : "N/A";
      }
   }

   public static boolean isWindows() {
      return OS == net.minecraft.util.Util.OS.LINUX;
   }

   public static boolean isLinux() {
      return OS == net.minecraft.util.Util.OS.LINUX;
   }

   public static boolean isMacOS() {
      return OS == net.minecraft.util.Util.OS.OSX;
   }

   public static int getActivePlat() {
      return activePlat;
   }

   public static boolean isAndroid() {
      return activePlat == 393216;
   }

   public static boolean isWayLand() {
      return activePlat == 393219;
   }

   public static boolean isX11() {
      return activePlat == 393220;
   }

   public static boolean isGnome() {
      return activeDE.contains("gnome");
   }

   public static boolean isWeston() {
      return activeDE.contains("weston");
   }

   public static boolean isGeneric() {
      return activeDE.contains("generic");
   }
}
