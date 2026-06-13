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
package com.xeno.render.shader;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.shaders.ShaderType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import com.xeno.vulkan.shader.SPIRVUtils;
import org.apache.commons.io.IOUtils;

public abstract class ShaderLoadUtil {
   public static final String RESOURCES_PATH = SPIRVUtils.class.getResource("/assets/xeno").toExternalForm();
   public static final String SHADERS_PATH = "%s/shaders/".formatted(RESOURCES_PATH);
   public static final Set<String> REMAPPED_SHADERS = Sets.newHashSet(
      new String[]{"core/screenquad.vsh", "core/rendertype_item_entity_translucent_cull.vsh", "core/animate_sprite.vsh", "core/animate_sprite_blit.fsh"}
   );

   public static String resolveShaderPath(String path) {
      return resolveShaderPath(SHADERS_PATH, path);
   }

   public static String resolveShaderPath(String shaderPath, String path) {
      return "%s%s".formatted(shaderPath, path);
   }

   public static String loadShader(String path, String shaderName) {
      return loadShader(path, shaderName, null);
   }

   public static String loadShader(String path, String shaderName, List<String> defines) {
      String source = getShaderSource(path, shaderName);
      if (defines != null && !defines.isEmpty()) {
         source = injectDefines(source, defines);
      }

      return source;
   }

   public static String getConfigFilePath(String path, String rendertype) {
      String basePath = "%s/shaders/%s".formatted(RESOURCES_PATH, path);
      String configPath = "%s/%s/%s.json".formatted(basePath, rendertype, rendertype);

      Path filePath;
      try {
         filePath = FileSystems.getDefault().getPath(configPath);
         if (!Files.exists(filePath)) {
            configPath = "%s/%s.json".formatted(basePath, rendertype);
            filePath = FileSystems.getDefault().getPath(configPath);
         }

         if (!Files.exists(filePath)) {
            return null;
         }
      } catch (Throwable e) {
         throw new RuntimeException(e);
      }

      return filePath.toString();
   }

   public static JsonObject getJsonConfig(String path, String rendertype) {
      if (rendertype.contains(String.valueOf(':'))) {
         return null;
      }

      String basePath = path;
      String configPath = "%s/%s/%s.json".formatted(basePath, rendertype, rendertype);

      try {
         InputStream stream = getInputStream(configPath);
         if (stream == null) {
            configPath = "%s/%s.json".formatted(basePath, rendertype);
            stream = getInputStream(configPath);
         }

         if (stream == null) {
            return null;
         }

         JsonElement jsonElement = JsonParser.parseReader(new BufferedReader(new InputStreamReader(stream)));
         stream.close();
         return (JsonObject)jsonElement;
      } catch (Throwable e) {
         throw new RuntimeException(e);
      }
   }

   public static String getShaderSource(Identifier resourceLocation, ShaderType type) {
      String shaderExtension = switch (type) {
         case VERTEX -> ".vsh";
         case FRAGMENT -> ".fsh";
      };
      String path = resourceLocation.getPath();
      String[] splitPath = splitPath(path);
      String shaderName = "%s%s".formatted(splitPath[1], shaderExtension);
      String shaderFile = "%s/shaders/%s/%s".formatted(RESOURCES_PATH, path, shaderName);

      try {
         InputStream stream = getInputStream(shaderFile);
         if (stream == null) {
            shaderFile = "%s/shaders/%s%s".formatted(RESOURCES_PATH, path, shaderExtension);
            stream = getInputStream(shaderFile);
         }

         if (stream == null) {
            return null;
         }

         String source = IOUtils.toString(new BufferedReader(new InputStreamReader(stream)));
         stream.close();
         return source;
      } catch (Throwable e) {
         throw new RuntimeException(e);
      }
   }

   public static String getShaderSource(String path, ShaderType type) {
      String shaderExtension = switch (type) {
         case VERTEX -> ".vsh";
         case FRAGMENT -> ".fsh";
      };
      String[] splitPath = splitPath(path);
      String shaderName = "%s%s".formatted(splitPath[1], shaderExtension);
      String shaderFile = "%s/shaders/%s/%s".formatted(RESOURCES_PATH, path, shaderName);

      try {
         InputStream stream = getInputStream(shaderFile);
         String source = IOUtils.toString(new BufferedReader(new InputStreamReader(stream)));
         stream.close();
         return source;
      } catch (Throwable e) {
         throw new RuntimeException(e);
      }
   }

   public static String getShaderSource(String path, String shaderName) {
      String shaderFile = "%s/%s".formatted(path, shaderName);

      try {
         InputStream stream = getInputStream(shaderFile);
         if (stream == null) {
            return null;
         }

         String source = IOUtils.toString(new BufferedReader(new InputStreamReader(stream)));
         stream.close();
         return source;
      } catch (Throwable e) {
         throw new RuntimeException(e);
      }
   }

   public static String getShaderSource(String path, String configName, String shaderName, SPIRVUtils.ShaderKind type) {
      String shaderExtension = switch (type) {
         case VERTEX_SHADER -> ".vsh";
         case FRAGMENT_SHADER -> ".fsh";
         case COMPUTE_SHADER -> ".comp";
         default -> throw new UnsupportedOperationException("shader type %s unsupported");
      };
      String basePath = path;
      String shaderPath = "/%s/%s".formatted(configName, configName);
      String shaderFile = "%s%s%s".formatted(basePath, shaderPath, shaderExtension);

      try {
         InputStream stream = getInputStream(shaderFile);
         if (stream == null) {
            shaderPath = "/%s".formatted(shaderName);
            shaderFile = "%s%s%s".formatted(basePath, shaderPath, shaderExtension);
            stream = getInputStream(shaderFile);
         }

         if (stream == null) {
            shaderPath = "/%s/%s".formatted(configName, shaderName);
            shaderFile = "%s%s%s".formatted(basePath, shaderPath, shaderExtension);
            stream = getInputStream(shaderFile);
         }

         if (stream == null) {
            shaderPath = "/%s/%s".formatted(shaderName, shaderName);
            shaderFile = "%s%s%s".formatted(basePath, shaderPath, shaderExtension);
            stream = getInputStream(shaderFile);
         }

         if (stream == null) {
            return null;
         }

         String source = IOUtils.toString(new BufferedReader(new InputStreamReader(stream)));
         stream.close();
         return source;
      } catch (Throwable e) {
         throw new RuntimeException(e);
      }
   }

   public static String getFileName(String path) {
      int idx = path.lastIndexOf(47);
      return idx > -1 ? path.substring(idx + 1) : path;
   }

   public static String removeNameSpace(String path) {
      int idx = path.indexOf(58);
      return idx > -1 ? path.substring(idx + 1) : path;
   }

   public static String[] splitPath(String path) {
      int idx = path.lastIndexOf(47);
      return new String[]{path.substring(0, idx), path.substring(idx + 1)};
   }

   public static InputStream getInputStream(String path) {
      try {
         Path path1 = Paths.get(new URI(path));
         return !Files.exists(path1) ? null : Files.newInputStream(path1);
      } catch (URISyntaxException | IOException e) {
         throw new RuntimeException(e);
      }
   }

   public static String injectDefines(String shaderSrc, List<String> defines) {
      StringBuilder stringBuilder = new StringBuilder();

      for (String define : defines) {
         stringBuilder.append("#define ").append(define).append('\n');
      }

      int i = shaderSrc.indexOf(10);
      String out = shaderSrc.substring(0, i + 1) + "\n" + stringBuilder + "\n" + shaderSrc.substring(i + 1);
      return out;
   }
}
