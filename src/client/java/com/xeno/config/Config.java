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


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import com.xeno.config.video.VideoModeSet;

public class Config {
   public VideoModeSet.VideoMode videoMode = VideoModeSet.getDummy().getVideoMode();
   public int windowMode = 0;
   public int advCulling = 2;
   public boolean uniqueOpaqueLayer = true;
   public boolean entityCulling = true;
   public int device = -1;
   public boolean useWayland = false;
   public int ambientOcclusion = 1;
   public int frameQueueSize = 2;
   public int builderThreads = 0;
   public boolean backFaceCulling = true;
   public boolean greedyMeshing = false;
    public boolean gpuDrivenRenderer = false;
    public boolean entityBatching = false;
    public boolean textureAnimations = true;
   private static Path CONFIG_PATH;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().excludeFieldsWithModifiers(new int[]{2}).create();

   public Config() {
   }

   public void write() {
      if (!Files.exists(CONFIG_PATH.getParent())) {
         try {
            Files.createDirectories(CONFIG_PATH);
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

      try {
         Files.write(CONFIG_PATH, Collections.singleton(GSON.toJson(this)));
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static Config load(Path path) {
      CONFIG_PATH = path;
      Config config;
      if (Files.exists(path)) {
         try (FileReader fileReader = new FileReader(path.toFile())) {
            config = (Config)GSON.fromJson(fileReader, Config.class);
         } catch (IOException exception) {
            throw new RuntimeException(exception.getMessage());
         }
      } else {
         config = new Config();
         config.write();
      }

      config.gpuDrivenRenderer = false;

      return config;
   }
}
