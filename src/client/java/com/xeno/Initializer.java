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
package com.xeno;

import com.xeno.config.Config;
import com.xeno.config.Platform;
import com.xeno.config.UpdateChecker;
import com.xeno.render.chunk.build.frapi.XenoRenderer;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Initializer {
   public static final Logger LOGGER = LogManager.getLogger("xeno");
   private static String VERSION;
   public static Config CONFIG;

   public static void init() {
      VERSION = ((ModContainer)FabricLoader.getInstance().getModContainer("xeno").get()).getMetadata().getVersion().getFriendlyString();
      LOGGER.info("== Xeno ==");
      Path configPath = FabricLoader.getInstance().getConfigDir().resolve("xeno_settings.json");
      CONFIG = loadConfig(configPath);
      Platform.init();
      Renderer.register(XenoRenderer.INSTANCE);
      UpdateChecker.checkForUpdates();
   }

   private static Config loadConfig(Path path) {
      return Config.load(path);
   }

   public static String getVersion() {
      return VERSION;
   }
}
