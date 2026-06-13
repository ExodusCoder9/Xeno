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
package com.xeno.render.engine;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import com.xeno.gl.VkGlTexture;
import com.xeno.interfaces.shader.ExtendedRenderPipeline;
import com.xeno.render.shader.ShaderLoadUtil;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.shader.GraphicsPipeline;
import com.xeno.vulkan.shader.Pipeline;
import com.xeno.vulkan.shader.SPIRVUtils;
import com.xeno.vulkan.shader.converter.GLSLParser;
import com.xeno.vulkan.shader.converter.Lexer;
import com.xeno.vulkan.shader.descriptor.ImageDescriptor;
import com.xeno.vulkan.shader.descriptor.UBO;
import com.xeno.vulkan.texture.VulkanImage;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class VkGpuDevice implements GpuDeviceBackend {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final VkCommandEncoder encoder;
   private final VkDebugLabel debugLabels;
   private final int maxSupportedTextureSize;
   private final int uniformOffsetAlignment;
   private final ShaderSource defaultShaderSource;
   private final Map<RenderPipeline, GlRenderPipeline> pipelineCache = new IdentityHashMap<>();
   private final Map<VkGpuDevice.ShaderCompilationKey, GlShaderModule> shaderCache = new HashMap<>();
   private final Set<String> enabledExtensions = new HashSet<>();
   private final Map<VkGpuDevice.ShaderCompilationKey, String> shaderSrcCache = new HashMap<>();

   public VkGpuDevice(long windowHandle, ShaderSource defaultShaderSource, GpuDebugOptions debugOptions) {
      VRenderSystem.initRenderer();
      this.debugLabels = VkDebugLabel.create(debugOptions.useLabels(), this.enabledExtensions);
      this.maxSupportedTextureSize = VRenderSystem.maxSupportedTextureSize();
      this.uniformOffsetAlignment = (int)DeviceManager.deviceProperties.limits().minUniformBufferOffsetAlignment();
      this.defaultShaderSource = defaultShaderSource;
      this.encoder = new VkCommandEncoder(this);
   }

   public VkDebugLabel debugLabels() {
      return this.debugLabels;
   }

   @Override
   public CommandEncoderBackend createCommandEncoder() {
      return this.encoder;
   }

   @Override
   public GpuSampler createSampler(
      AddressMode addressMode, AddressMode addressMode2, FilterMode filterMode, FilterMode filterMode2, int maxAnisotropy, OptionalDouble maxLod
   ) {
      return new VkSampler(addressMode, addressMode2, filterMode, filterMode2, maxAnisotropy, maxLod);
   }

   @Override
   public GpuTexture createTexture(
      @Nullable Supplier<String> supplier, int usage, TextureFormat textureFormat, int width, int height, int layers, int mipLevels
   ) {
      return this.createTexture(this.debugLabels.exists() && supplier != null ? supplier.get() : null, usage, textureFormat, width, height, layers, mipLevels);
   }

   @Override
   public GpuTexture createTexture(@Nullable String string, int usage, TextureFormat textureFormat, int width, int height, int layers, int mipLevels) {
      if (mipLevels < 1) {
         throw new IllegalArgumentException("mipLevels must be at least 1");
      }

      int id = VkGlTexture.genTextureId();
      if (string == null) {
         string = String.valueOf(id);
      }

      int format = VkGpuTexture.vkFormat(textureFormat);
      int viewType = VkGpuTexture.vkImageViewType(usage);
      boolean depthFormat = VulkanImage.isDepthFormat(format);
      int attachmentUsage = depthFormat ? 32 : 16;
      VulkanImage texture = VulkanImage.builder(width, height)
         .setName(string)
         .setFormat(format)
         .setArrayLayers(layers)
         .setMipLevels(mipLevels)
         .addUsage(attachmentUsage)
         .setViewType(viewType)
         .createVulkanImage();
      VkGlTexture vGlTexture = VkGlTexture.getTexture(id);
      vGlTexture.setVulkanImage(texture);
      VkGlTexture.bindTexture(id);
      VkGpuTexture glTexture = new VkGpuTexture(usage, string, textureFormat, width, height, layers, mipLevels, id, vGlTexture);
      this.debugLabels.applyLabel(glTexture);
      return glTexture;
   }

   public VkGpuTexture gpuTextureFromVulkanImage(VulkanImage image) {
      int id = VkGlTexture.genTextureId();
      VkGlTexture glTexture = VkGlTexture.getTexture(id);
      glTexture.setVulkanImage(image);
      TextureFormat textureFormat = VkGpuTexture.textureFormat(image.format);
      VkGpuTexture gpuTexture = new VkGpuTexture(15, image.name, textureFormat, image.width, image.height, 1, image.mipLevels, id, glTexture);
      this.debugLabels.applyLabel(gpuTexture);
      return gpuTexture;
   }

   @Override
   public GpuTextureView createTextureView(GpuTexture gpuTexture) {
      return this.createTextureView(gpuTexture, 0, gpuTexture.getMipLevels());
   }

   @Override
   public GpuTextureView createTextureView(GpuTexture gpuTexture, int startLevel, int levels) {
      if (gpuTexture.isClosed()) {
         throw new IllegalArgumentException("Can't create texture view with closed texture");
      }

      if (startLevel >= 0 && startLevel + levels <= gpuTexture.getMipLevels()) {
         if (gpuTexture.getClass() != VkGpuTexture.class) {
            gpuTexture = VkGpuTexture.fromGlTexture((GlTexture)gpuTexture);
         }

         return new VkTextureView((VkGpuTexture)gpuTexture, startLevel, levels);
      } else {
         throw new IllegalArgumentException(
            levels + " mip levels starting from " + startLevel + " would be out of range for texture with only " + gpuTexture.getMipLevels() + " mip levels"
         );
      }
   }

   @Override
   public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, @GpuBuffer.Usage int usage, long size) {
      if (size <= 0L) {
         throw new IllegalArgumentException("Buffer size must be greater than zero");
      } else {
         return new VkGpuBuffer(this.debugLabels, supplier, usage, size);
      }
   }

   @Override
   public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int usage, ByteBuffer byteBuffer) {
      if (!byteBuffer.hasRemaining()) {
         throw new IllegalArgumentException("Buffer source must not be empty");
      }

      VkGpuBuffer glBuffer = new VkGpuBuffer(this.debugLabels, supplier, usage, byteBuffer.remaining());
      this.encoder.writeToBuffer(glBuffer.slice(), byteBuffer);
      return glBuffer;
   }

   @Override
   public String getImplementationInformation() {
      return "Vulkan " + Vulkan.getDevice().vkVersion + ", " + Vulkan.getDevice().vendorIdString;
   }

   @Override
   public List<String> getLastDebugMessages() {
      return Collections.emptyList();
   }

   @Override
   public boolean isDebuggingEnabled() {
      return false;
   }

   @Override
   public String getRenderer() {
      return DeviceManager.device.deviceName;
   }

   @Override
   public String getVendor() {
      return Vulkan.getDevice().vendorIdString;
   }

   @Override
   public String getBackendName() {
      return "Vulkan";
   }

   @Override
   public String getVersion() {
      return Vulkan.getDevice().vkVersion;
   }

   private static int getMaxSupportedTextureSize() {
      int i = GlStateManager._getInteger(3379);

      for (int j = Math.max(32768, i); j >= 1024; j >>= 1) {
         GlStateManager._texImage2D(32868, 0, 6408, j, j, 0, 6408, 5121, null);
         int k = GlStateManager._getTexLevelParameter(32868, 0, 4096);
         if (k != 0) {
            return j;
         }
      }

      int jx = Math.max(i, 1024);
      LOGGER.info("Failed to determine maximum texture size by probing, trying GL_MAX_TEXTURE_SIZE = {}", jx);
      return jx;
   }

   @Override
   public int getMaxTextureSize() {
      return this.maxSupportedTextureSize;
   }

   @Override
   public int getUniformOffsetAlignment() {
      return this.uniformOffsetAlignment;
   }

   @Override
   public void clearPipelineCache() {
      for (GlRenderPipeline glRenderPipeline : this.pipelineCache.values()) {
         if (glRenderPipeline.program() != GlProgram.INVALID_PROGRAM) {
            glRenderPipeline.program().close();
         }
      }

      this.pipelineCache.clear();

      for (GlShaderModule glShaderModule : this.shaderCache.values()) {
         if (glShaderModule != GlShaderModule.INVALID_SHADER) {
            glShaderModule.close();
         }
      }

      this.shaderCache.clear();
   }

   @Override
   public List<String> getEnabledExtensions() {
      return new ArrayList<>(this.enabledExtensions);
   }

   @Override
   public int getMaxSupportedAnisotropy() {
      return 16;
   }

   @Override
   public void close() {
      this.clearPipelineCache();
   }

   @Override
   public void setVsync(boolean enabled) {
   }

   @Override
   public void presentFrame() {
      Renderer.getInstance().endFrame();
   }

   @Override
   public boolean isZZeroToOne() {
      return true;
   }

   protected GlShaderModule getOrCompileShader(
      Identifier resourceLocation, ShaderType shaderType, ShaderDefines shaderDefines, BiFunction<Identifier, ShaderType, String> biFunction
   ) {
      VkGpuDevice.ShaderCompilationKey shaderCompilationKey = new VkGpuDevice.ShaderCompilationKey(resourceLocation, shaderType, shaderDefines);
      return this.shaderCache.computeIfAbsent(shaderCompilationKey, shaderCompilationKey2 -> this.compileShader(shaderCompilationKey, biFunction));
   }

   protected String getCachedShaderSrc(Identifier resourceLocation, ShaderType shaderType, ShaderDefines shaderDefines, ShaderSource shaderSourceGetter) {
      VkGpuDevice.ShaderCompilationKey shaderCompilationKey = new VkGpuDevice.ShaderCompilationKey(resourceLocation, shaderType, shaderDefines);
      return this.shaderSrcCache.computeIfAbsent(shaderCompilationKey, compilationKey -> {
         String shaderExtension = switch (shaderType) {
            case VERTEX -> ".vsh";
            case FRAGMENT -> ".fsh";
         };
         String shaderName = resourceLocation.getPath() + shaderExtension;
         if (ShaderLoadUtil.REMAPPED_SHADERS.contains(shaderName)) {
            String src = ShaderLoadUtil.getShaderSource(resourceLocation, shaderType);
            if (src == null) {
               throw new RuntimeException("shader: (%s) not found.".formatted(src));
            } else {
               return src;
            }
         } else {
            return shaderSourceGetter.get(compilationKey.id, compilationKey.type);
         }
      });
   }

   @Override
   public CompiledRenderPipeline precompilePipeline(RenderPipeline renderPipeline, @Nullable ShaderSource shaderSourceGetter) {
      shaderSourceGetter = shaderSourceGetter == null ? this.defaultShaderSource : shaderSourceGetter;

      try {
         this.compilePipeline(renderPipeline, shaderSourceGetter);
      } catch (Exception e) {
         throw new RuntimeException("Caught exception compiling pipeline: %s".formatted(renderPipeline.toString()), e);
      }

      return new VkGpuDevice.VkRenderPipeline(renderPipeline);
   }

   public void compilePipeline(RenderPipeline renderPipeline) {
      this.compilePipeline(renderPipeline, this.defaultShaderSource);
   }

   private GlShaderModule compileShader(VkGpuDevice.ShaderCompilationKey shaderCompilationKey, BiFunction<Identifier, ShaderType, String> biFunction) {
      String string = biFunction.apply(shaderCompilationKey.id, shaderCompilationKey.type);
      if (string == null) {
         LOGGER.error("Couldn't find source for {} shader ({})", shaderCompilationKey.type, shaderCompilationKey.id);
         return GlShaderModule.INVALID_SHADER;
      } else {
         String string2 = GlslPreprocessor.injectDefines(string, shaderCompilationKey.defines);
         int i = GlStateManager.glCreateShader(GlConst.toGl(shaderCompilationKey.type));
         GlStateManager.glShaderSource(i, string2);
         GlStateManager.glCompileShader(i);
         if (GlStateManager.glGetShaderi(i, 35713) == 0) {
            String string3 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
            LOGGER.error("Couldn't compile {} shader ({}): {}", new Object[]{shaderCompilationKey.type.getName(), shaderCompilationKey.id, string3});
            return GlShaderModule.INVALID_SHADER;
         } else {
            GlShaderModule glShaderModule = new GlShaderModule(i, shaderCompilationKey.id, shaderCompilationKey.type);
            this.debugLabels.applyLabel(glShaderModule);
            return glShaderModule;
         }
      }
   }

   private void compilePipeline(RenderPipeline renderPipeline, ShaderSource shaderSrcGetter) {
      String locationPath = renderPipeline.getLocation().getPath();
      String configName;
      if (locationPath.contains("core")) {
         configName = locationPath.split("/")[1];
      } else {
         configName = locationPath;
      }

      Pipeline.Builder builder = new Pipeline.Builder(renderPipeline.getVertexFormat(), configName);
      ExtendedRenderPipeline extPipeline = ExtendedRenderPipeline.of(renderPipeline);
      Identifier vertexShaderLocation = renderPipeline.getVertexShader();
      Identifier fragmentShaderLocation = renderPipeline.getFragmentShader();
      ShaderDefines shaderDefines = renderPipeline.getShaderDefines();
      String vshSrc = this.getCachedShaderSrc(vertexShaderLocation, ShaderType.VERTEX, shaderDefines, shaderSrcGetter);
      String fshSrc = this.getCachedShaderSrc(fragmentShaderLocation, ShaderType.FRAGMENT, shaderDefines, shaderSrcGetter);
      vshSrc = GlslPreprocessor.injectDefines(vshSrc, shaderDefines);
      fshSrc = GlslPreprocessor.injectDefines(fshSrc, shaderDefines);
      Lexer lexer = new Lexer(vshSrc);
      GLSLParser parser = new GLSLParser();
      parser.setVertexFormat(renderPipeline.getVertexFormat());

      try {
         parser.parse(lexer, GLSLParser.Stage.VERTEX);
         lexer = new Lexer(fshSrc);
         parser.parse(lexer, GLSLParser.Stage.FRAGMENT);
      } catch (Exception e) {
         throw new RuntimeException("Caught exception while parsing: %s".formatted(renderPipeline.toString()), e);
      }

      UBO[] ubos = parser.createUBOs();
      List<ImageDescriptor> samplers = parser.getSamplerList();
      String vshProcessed = parser.getOutput(GLSLParser.Stage.VERTEX);
      String fshProcessed = parser.getOutput(GLSLParser.Stage.FRAGMENT);
      builder.setUniforms(List.of(ubos), samplers);
      builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vshProcessed);
      builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fshProcessed);

      GraphicsPipeline pipeline;
      try {
         pipeline = builder.createGraphicsPipeline();
      } catch (Exception e) {
         e.printStackTrace();
         throw new RuntimeException("Exception while compiling pipeline %s".formatted(renderPipeline));
      }

      EGlProgram eGlProgram = new EGlProgram(1, configName);
      eGlProgram.setupUniforms(pipeline, renderPipeline.getUniforms(), renderPipeline.getSamplers());
      extPipeline.setProgram(eGlProgram);
      extPipeline.setPipeline(pipeline);
   }

   @Environment(EnvType.CLIENT)
   record ShaderCompilationKey(Identifier id, ShaderType type, ShaderDefines defines) {
      @Override
      public String toString() {
         String string = this.id + " (" + this.type + ")";
         return !this.defines.isEmpty() ? string + " with " + this.defines : string;
      }
   }

   private static class VkRenderPipeline implements CompiledRenderPipeline {
      final RenderPipeline renderPipeline;

      public VkRenderPipeline(RenderPipeline renderPipeline) {
         this.renderPipeline = renderPipeline;
      }

      @Override
      public boolean isValid() {
         return true;
      }
   }
}
