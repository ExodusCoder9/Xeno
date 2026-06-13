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
package com.xeno.vulkan.shader;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeResource;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.shaderc.ShadercIncludeResolveI;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultReleaseI;
import org.lwjgl.vulkan.VK12;

public class SPIRVUtils {
   private static final boolean DEBUG = true;
   private static final boolean OPTIMIZATIONS = false;
   private static long compiler;
   private static long options;
   private static final SPIRVUtils.ShaderIncluder SHADER_INCLUDER = new SPIRVUtils.ShaderIncluder();
   private static final SPIRVUtils.ShaderReleaser SHADER_RELEASER = new SPIRVUtils.ShaderReleaser();
   private static final long pUserData = 0L;
   private static ObjectArrayList<String> includePaths;

   private static void initCompiler() {
      compiler = Shaderc.shaderc_compiler_initialize();
      if (compiler == 0L) {
         throw new RuntimeException("Failed to create shader compiler");
      }

      options = Shaderc.shaderc_compile_options_initialize();
      if (options == 0L) {
         throw new RuntimeException("Failed to create compiler options");
      }

      Shaderc.shaderc_compile_options_set_generate_debug_info(options);
      Shaderc.shaderc_compile_options_set_target_env(options, 4202496, VK12.VK_API_VERSION_1_2);
      Shaderc.shaderc_compile_options_set_include_callbacks(options, SHADER_INCLUDER, SHADER_RELEASER, 0L);
      includePaths = new ObjectArrayList();
      addIncludePath("/assets/xeno/shaders/include/");
   }

   public static void addIncludePath(String path) {
      URL url = SPIRVUtils.class.getResource(path);
      if (url != null) {
         includePaths.add(url.toExternalForm());
      }
   }

   public static SPIRVUtils.SPIRV compileShader(String filename, String source, SPIRVUtils.ShaderKind shaderKind) {
      if (source == null) {
         throw new NullPointerException("Source for %s.%s is null".formatted(filename, shaderKind));
      } else {
         long result = Shaderc.shaderc_compile_into_spv(compiler, source, shaderKind.kind, filename, "main", options);
         if (result == 0L) {
            throw new RuntimeException("Failed to compile shader %s into SPRI-V".formatted(filename));
         } else if (Shaderc.shaderc_result_get_compilation_status(result) != 0) {
            String errorMessage = Shaderc.shaderc_result_get_error_message(result);
            throw new RuntimeException("Failed to compile shader %s into SPIR-V:\n\t%s".formatted(filename, errorMessage));
         } else {
            return new SPIRVUtils.SPIRV(result, Shaderc.shaderc_result_get_bytes(result));
         }
      }
   }

   static {
      initCompiler();
   }

   public static final class SPIRV implements NativeResource {
      private final long handle;
      private ByteBuffer bytecode;

      public SPIRV(long handle, ByteBuffer bytecode) {
         this.handle = handle;
         this.bytecode = bytecode;
      }

      public ByteBuffer bytecode() {
         return this.bytecode;
      }

      public void free() {
         this.bytecode = null;
      }
   }

   private static class ShaderIncluder implements ShadercIncludeResolveI {
      private static final int MAX_PATH_LENGTH = 4096;

      public long invoke(long user_data, long requested_source, int type, long requesting_source, long include_depth) {
         String requesting = MemoryUtil.memASCII(requesting_source);
         String requested = MemoryUtil.memASCII(requested_source);

         try {
            MemoryStack stack;
            label56: {
               stack = MemoryStack.stackPush();

               long var17;
               try {
                  ObjectListIterator var14 = SPIRVUtils.includePaths.iterator();

                  Path path;
                  do {
                     if (!var14.hasNext()) {
                        break label56;
                     }

                     String includePath = (String)var14.next();
                     path = Paths.get(new URI(String.format("%s%s", includePath, requested)));
                  } while (!Files.exists(path));

                  byte[] bytes = Files.readAllBytes(path);
                  var17 = ShadercIncludeResult.malloc(stack).source_name(stack.ASCII(requested)).content(stack.bytes(bytes)).user_data(user_data).address();
               } catch (Throwable var20) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable var19) {
                        var20.addSuppressed(var19);
                     }
                  }

                  throw var20;
               }

               if (stack != null) {
                  stack.close();
               }

               return var17;
            }

            if (stack != null) {
               stack.close();
            }
         } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
         }

         throw new RuntimeException(String.format("%s: Unable to find %s in include paths", requesting, requested));
      }
   }

   public enum ShaderKind {
      VERTEX_SHADER(0),
      GEOMETRY_SHADER(3),
      FRAGMENT_SHADER(1),
      COMPUTE_SHADER(2);

      private final int kind;

      ShaderKind(int kind) {
         this.kind = kind;
      }
   }

   private static class ShaderReleaser implements ShadercIncludeResultReleaseI {
      public void invoke(long user_data, long include_result) {
      }
   }
}
