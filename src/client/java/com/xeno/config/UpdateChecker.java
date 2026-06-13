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


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.util.version.VersionParser;
import net.minecraft.SharedConstants;
import com.xeno.Initializer;

public abstract class UpdateChecker {
   private static boolean updateAvailable = false;

   public UpdateChecker() {
   }

   public static void checkForUpdates() {
      CompletableFuture.supplyAsync(() -> {
         try {
            String req = "https://api.modrinth.com/v2/project/vulkanmod/version?include_changelog=false";
            String mcVersion = SharedConstants.getCurrentVersion().name();
            req = req + "&game_versions=%s".formatted(mcVersion);
            URL url = new URL(req);
            HttpURLConnection http = (HttpURLConnection)url.openConnection();
            InputStream inputStream = http.getInputStream();
            JsonObject data = JsonParser.parseString("{ versions: " + new String(inputStream.readAllBytes()) + "}").getAsJsonObject();
            JsonArray versions = data.getAsJsonArray("versions");
            http.disconnect();
            String version = String.valueOf(versions.get(0).getAsJsonObject().get("version_number")).replace("\"", "");
            SemanticVersion currentVersion = VersionParser.parseSemantic(Initializer.getVersion());
            if (currentVersion.getPrereleaseKey().isPresent()) {
               Initializer.LOGGER.info("Pre-release version, skipping update check.");
               return null;
            }

            updateAvailable = currentVersion.compareTo(Version.parse(version)) < 0;
            if (updateAvailable) {
               Initializer.LOGGER.info("Update available!");
            }
         } catch (IOException e) {
            Initializer.LOGGER.info("Error occurred, skipping update check.");
         } catch (VersionParsingException e) {
            Initializer.LOGGER.info("Unable to parse version, skipping update check.");
         }

         return null;
      });
   }

   public static boolean isUpdateAvailable() {
      return updateAvailable;
   }
}
