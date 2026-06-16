package com.xeno.render.chunk;

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


import com.google.common.collect.Sets;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import com.xeno.Initializer;
import com.xeno.render.chunk.buffer.DrawBuffers;
import com.xeno.render.chunk.build.RenderRegionBuilder;
import com.xeno.render.chunk.build.task.ChunkTask;
import com.xeno.render.chunk.build.task.TaskDispatcher;
import com.xeno.render.chunk.graph.SectionGraph;
import com.xeno.render.chunk.util.StaticQueue;
import com.xeno.render.chunk.util.ResettableQueue;
import com.xeno.render.engine.VkGpuTexture;
import com.xeno.render.profiling.BuildTimeProfiler;
import com.xeno.render.profiling.Profiler;
import com.xeno.render.shader.PipelineManager;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.Drawer;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.memory.MemoryTypes;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.memory.buffer.IndexBuffer;
import com.xeno.vulkan.memory.buffer.IndirectBuffer;
import com.xeno.vulkan.shader.GraphicsPipeline;
import com.xeno.vulkan.texture.SamplerManager;
import com.xeno.vulkan.texture.VTextureSelector;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class WorldRenderer {
   private static WorldRenderer INSTANCE;
   public RenderRegionBuilder renderRegionCache;
   private final Minecraft minecraft;
   private ClientLevel level;
   private int renderDistance;
   private final RenderBuffers renderBuffers;
   private final EntityRenderDispatcher entityRenderDispatcher;
   private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
   private final GameRenderState levelRenderState;
   private final FeatureRenderDispatcher featureRenderDispatcher;
   private float partialTick;
   private final Vector3d cameraPos = new Vector3d();
   private int lastCameraSectionX;
   private int lastCameraSectionY;
   private int lastCameraSectionZ;
   private float lastCameraX;
   private float lastCameraY;
   private float lastCameraZ;
   private float lastCamRotX;
   private float lastCamRotY;
   private SectionGrid sectionGrid;
   private SectionGraph sectionGraph;
   private boolean graphNeedsUpdate;
   private final Set<BlockEntity> globalBlockEntities = Sets.newHashSet();
   private final TaskDispatcher taskDispatcher;
   private double xTransparentOld;
   private double yTransparentOld;
   private double zTransparentOld;
   IndirectBuffer[] indirectBuffers;
   private long terrainSampler;
   private final List<Runnable> onAllChangedCallbacks = new ObjectArrayList();
   private long currentFrameTimeMs;
   public static long worldLoadTime;

   public static WorldRenderer init(
      EntityRenderDispatcher entityRenderDispatcher,
      BlockEntityRenderDispatcher blockEntityRenderDispatcher,
      RenderBuffers renderBuffers,
      GameRenderState levelRenderState,
      FeatureRenderDispatcher featureRenderDispatcher
   ) {
      return INSTANCE != null
         ? INSTANCE
         : (INSTANCE = new WorldRenderer(entityRenderDispatcher, blockEntityRenderDispatcher, renderBuffers, levelRenderState, featureRenderDispatcher));
   }

   private WorldRenderer(
      EntityRenderDispatcher entityRenderDispatcher,
      BlockEntityRenderDispatcher blockEntityRenderDispatcher,
      RenderBuffers renderBuffers,
      GameRenderState levelRenderState,
      FeatureRenderDispatcher featureRenderDispatcher
   ) {
      this.minecraft = Minecraft.getInstance();
      this.renderBuffers = renderBuffers;
      this.entityRenderDispatcher = entityRenderDispatcher;
      this.blockEntityRenderDispatcher = blockEntityRenderDispatcher;
      this.levelRenderState = levelRenderState;
      this.featureRenderDispatcher = featureRenderDispatcher;
      this.renderRegionCache = new RenderRegionBuilder();
      this.taskDispatcher = new TaskDispatcher();
      ChunkTask.setTaskDispatcher(this.taskDispatcher);
      this.allocateIndirectBuffers();
      TerrainRenderType.updateMapping();
      Renderer.getInstance().addOnResizeCallback(() -> {
         if (this.indirectBuffers.length != Renderer.getFramesNum()) {
            this.allocateIndirectBuffers();
         }
      });
   }

   private void allocateIndirectBuffers() {
      if (this.indirectBuffers != null) {
         Arrays.stream(this.indirectBuffers).forEach(Buffer::scheduleFree);
      }

      this.indirectBuffers = new IndirectBuffer[Renderer.getFramesNum()];

      for (int i = 0; i < this.indirectBuffers.length; i++) {
         this.indirectBuffers[i] = new IndirectBuffer(1000000, MemoryTypes.HOST_MEM);
      }
   }

   private void benchCallback() {
      BuildTimeProfiler.runBench(this.graphNeedsUpdate || !this.taskDispatcher.isIdle());
   }

   public void setupRenderer(Camera camera, Frustum frustum, boolean isCapturedFrustum, boolean spectator) {
      Profiler profiler = Profiler.getMainProfiler();
      profiler.push("Setup_Renderer");
      ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();
      this.benchCallback();
      this.currentFrameTimeMs = System.currentTimeMillis();
      Vec3 camPos = camera.position();
      this.cameraPos.set(camPos.x(), camPos.y(), camPos.z());
      if (this.minecraft.options.getEffectiveRenderDistance() != this.renderDistance) {
         this.allChanged();
      }

      mcProfiler.push("camera");
      float cameraX = (float)this.cameraPos.x();
      float cameraY = (float)this.cameraPos.y();
      float cameraZ = (float)this.cameraPos.z();
      int sectionX = SectionPos.posToSectionCoord(cameraX);
      int sectionY = SectionPos.posToSectionCoord(cameraY);
      int sectionZ = SectionPos.posToSectionCoord(cameraZ);
      profiler.push("reposition");
      if (this.lastCameraSectionX != sectionX || this.lastCameraSectionY != sectionY || this.lastCameraSectionZ != sectionZ) {
         this.lastCameraSectionX = sectionX;
         this.lastCameraSectionY = sectionY;
         this.lastCameraSectionZ = sectionZ;
         this.sectionGrid.repositionCamera(cameraX, cameraZ);
      }

      profiler.pop();
      double entityDistanceScaling = (Double)this.minecraft.options.entityDistanceScaling().get();
      Entity.setViewScale(Mth.clamp(this.renderDistance / 8.0, 1.0, 2.5) * entityDistanceScaling);
      mcProfiler.popPush("cull");
      mcProfiler.popPush("update");
      boolean cameraMoved = false;
      float d_xRot = Math.abs(camera.xRot() - this.lastCamRotX);
      float d_yRot = Math.abs(camera.yRot() - this.lastCamRotY);
      cameraMoved |= d_xRot > 2.0F || d_yRot > 2.0F;
      cameraMoved |= cameraX != this.lastCameraX || cameraY != this.lastCameraY || cameraZ != this.lastCameraZ;
      this.graphNeedsUpdate |= cameraMoved;
      if (!isCapturedFrustum && this.graphNeedsUpdate()) {
         this.graphNeedsUpdate = false;
         this.lastCameraX = cameraX;
         this.lastCameraY = cameraY;
         this.lastCameraZ = cameraZ;
         this.lastCamRotX = camera.xRot();
         this.lastCamRotY = camera.yRot();
         this.sectionGraph.update(camera, frustum, spectator);
      }

      this.indirectBuffers[Renderer.getCurrentFrame()].reset();
      mcProfiler.pop();
      profiler.pop();
   }

   public void uploadSections() {
      ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();
      mcProfiler.push("upload");
      Profiler profiler = Profiler.getMainProfiler();
      profiler.push("Uploads");

      try {
         if (this.taskDispatcher.updateSections()) {
            this.graphNeedsUpdate = true;
         }
      } catch (Exception e) {
         Initializer.LOGGER.error(e.getMessage());
         this.allChanged();
      }

      profiler.pop();
      mcProfiler.pop();
   }

   public boolean isSectionCompiled(BlockPos blockPos) {
      RenderSection renderSection = this.sectionGrid.getSectionAtBlockPos(blockPos);
      return renderSection != null && renderSection.isCompiled();
   }

   public void allChanged() {
      if (this.level != null) {
         this.level.clearTintCaches();
         this.renderRegionCache.clear();
         this.taskDispatcher.createThreads(Initializer.CONFIG.builderThreads);
         this.graphNeedsUpdate = true;
         this.renderDistance = this.minecraft.options.getEffectiveRenderDistance();
         if (this.sectionGrid != null) {
            this.sectionGrid.freeAllBuffers();
         }

         this.taskDispatcher.clearBatchQueue();
         synchronized (this.globalBlockEntities) {
            this.globalBlockEntities.clear();
         }

         this.sectionGrid = new SectionGrid(this.level, this.renderDistance);
         this.sectionGraph = new SectionGraph(this.level, this.sectionGrid, this.taskDispatcher);
         this.onAllChangedCallbacks.forEach(Runnable::run);
         Entity entity = this.minecraft.getCameraEntity();
         if (entity != null) {
            this.sectionGrid.repositionCamera(entity.getX(), entity.getZ());
         }
      }
   }

   public void setLevel(@Nullable ClientLevel level) {
      this.lastCameraX = Float.MIN_VALUE;
      this.lastCameraY = Float.MIN_VALUE;
      this.lastCameraZ = Float.MIN_VALUE;
      this.lastCameraSectionX = Integer.MIN_VALUE;
      this.lastCameraSectionY = Integer.MIN_VALUE;
      this.lastCameraSectionZ = Integer.MIN_VALUE;
      this.level = level;
      ChunkStatusMap.createInstance(this.renderDistance);
      if (level != null) {
         worldLoadTime = System.currentTimeMillis();
         this.allChanged();
      } else {
         if (this.sectionGrid != null) {
            this.sectionGrid.freeAllBuffers();
            this.sectionGrid = null;
         }

         this.taskDispatcher.stopThreads();
         this.graphNeedsUpdate = true;
      }
   }

   public void addOnAllChangedCallback(Runnable runnable) {
      this.onAllChangedCallbacks.add(runnable);
   }

   public void clearOnAllChangedCallbacks() {
      this.onAllChangedCallbacks.clear();
   }

   public void renderSectionLayer(TerrainRenderType renderType, double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection) {
      Renderer renderer = Renderer.getInstance();
      renderer.getMainPass().rebindMainTarget();
      this.sortTranslucentSections(camX, camY, camZ);
      ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();
      Zone zone = mcProfiler.zone(() -> "render_" + renderType);
      boolean isTranslucent = renderType == TerrainRenderType.TRANSLUCENT;
      if (!isTranslucent) {
         GlStateManager._disableBlend();
      } else {
         GlStateManager._enableBlend();
         VRenderSystem.blendFuncSeparate(770, 771, 1, 771);
      }

      VRenderSystem.enableCull();
      VRenderSystem.depthFunc(515);
      GlStateManager._enableDepthTest();
      GlStateManager._depthMask(true);
      GlStateManager._colorMask(15);
      GlStateManager._disablePolygonOffset();
      VRenderSystem.setPolygonModeGL(6914);
      VRenderSystem.applyMVP(modelView, projection);
      VRenderSystem.setPrimitiveTopologyGL(4);
      GraphicsPipeline pipeline = PipelineManager.getTerrainShader(renderType);
      renderer.bindGraphicsPipeline(pipeline);
      // Cache singleton once to avoid repeated static-field reads
      Minecraft mc = this.minecraft;
      TextureManager textureManager = mc.getTextureManager();
      AbstractTexture atlasTexture = textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS);
      GpuTextureView texView = atlasTexture.getTextureView();
      boolean useAnisotropy = mc.options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC;
      int maxAnisotropy = mc.options.maxAnisotropyValue();
      VkGpuTexture texture = (VkGpuTexture)texView.texture();
      if (this.terrainSampler == 0L) {
         this.terrainSampler = SamplerManager.getSampler(true, false, texture.getVulkanImage().mipLevels - 1, useAnisotropy, maxAnisotropy);
      }

      texture.getVulkanImage().setSampler(this.terrainSampler);
      VRenderSystem.setShaderTexture(0, texView);
      VRenderSystem.setShaderTexture(2, mc.gameRenderer.lightmap());
      VTextureSelector.bindShaderTextures(pipeline);
      int atlasTexWidth = texView.getWidth(0);
      int atlasTexHeight = texView.getHeight(0);
      VRenderSystem.setTextureSize(atlasTexWidth, atlasTexHeight);
      long currentTimeMs = this.currentFrameTimeMs;
      VRenderSystem.setCurrentTime((int)currentTimeMs);
      float fadeTime = ((Double)mc.options.chunkSectionFadeInTime().get()).floatValue();
      int fadeTimeMs = (int)(fadeTime * 1000.0F);
      float fadeTimeInv = fadeTime > 0.0F ? 1.0F / (fadeTime * 1000.0F) : 1.0F;
      Drawer drawer = Renderer.getDrawer();
      IndexBuffer indexBuffer = drawer.getQuadsIndexBuffer().getIndexBuffer();
      drawer.bindIndexBuffer(Renderer.getCommandBuffer(), indexBuffer, indexBuffer.indexType.value);
      int currentFrame = Renderer.getCurrentFrame();
      Set<TerrainRenderType> allowedRenderTypes = Initializer.CONFIG.uniqueOpaqueLayer
         ? TerrainRenderType.COMPACT_RENDER_TYPES
         : TerrainRenderType.SEMI_COMPACT_RENDER_TYPES;
      if (allowedRenderTypes.contains(renderType)) {
         renderType.setCutoutUniform();

         StaticQueue<ChunkArea> areaQueue = this.sectionGraph.getChunkAreaQueue().queue();
         int areaCount = areaQueue.size();
         for (int i = 0; i < areaCount; i++) {
            ChunkArea chunkArea = areaQueue.get(i);
            StaticQueue<RenderSection> queue = chunkArea.sectionQueue;
            DrawBuffers drawBuffers = chunkArea.drawBuffers;
            if (drawBuffers.getAreaBuffer(renderType) != null && queue.size() > 0) {
                drawBuffers.bindBuffers(Renderer.getCommandBuffer(), pipeline, renderType, camX, camY, camZ, currentTimeMs, fadeTimeMs, fadeTimeInv);
                renderer.uploadAndBindUBOs(pipeline);
                if (Initializer.CONFIG.gpuDrivenRenderer) {
                   drawBuffers.buildDrawBatchesGpu(this.cameraPos, queue, renderType, currentFrame);
                } else {
                   drawBuffers.buildDrawBatchesIndirect(this.cameraPos, this.indirectBuffers[currentFrame], queue, renderType);
                }
            }
         }
      }

      if (renderType == TerrainRenderType.CUTOUT || renderType == TerrainRenderType.TRIPWIRE) {
         this.indirectBuffers[currentFrame].submitUploads();
      }

      zone.close();
   }

   private void sortTranslucentSections(double camX, double camY, double camZ) {
      ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();
      mcProfiler.push("translucent_sort");
      double d0 = camX - this.xTransparentOld;
      double d1 = camY - this.yTransparentOld;
      double d2 = camZ - this.zTransparentOld;
      if (d0 * d0 + d1 * d1 + d2 * d2 > 2.0) {
         this.xTransparentOld = camX;
         this.yTransparentOld = camY;
         this.zTransparentOld = camZ;
         ResettableQueue<RenderSection> sectionQueue = this.sectionGraph.getSectionQueue();
         int queueSize = sectionQueue.size();
         for (int i = 0, j = 0; i < queueSize && j < 200; i++) {
            RenderSection section = sectionQueue.get(i);
            if (section.resortTransparency(this.taskDispatcher, this.cameraPos)) {
               j++;
            }
         }
      }

      mcProfiler.pop();
   }

   public void renderBlockEntities(
      PoseStack poseStack,
      LevelRenderState levelRenderState,
      SubmitNodeStorage submitNodeStorage,
      Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress
   ) {
      Profiler profiler = Profiler.getMainProfiler();
      profiler.pop();
      profiler.push("Block-entities");
      Vec3 vec3 = levelRenderState.cameraRenderState.pos;
      double camX = vec3.x();
      double camY = vec3.y();
      double camZ = vec3.z();

      ResettableQueue<RenderSection> blockEntitiesSections = this.sectionGraph.getBlockEntitiesSections();
      int blockSectionsCount = blockEntitiesSections.size();
      for (int i = 0; i < blockSectionsCount; i++) {
         RenderSection renderSection = blockEntitiesSections.get(i);
         List<BlockEntity> list = renderSection.getCompiledSection().getBlockEntities();
         int listSize = list.size();
         if (listSize > 0) {
            for (int k = 0; k < listSize; k++) {
               BlockEntity blockEntity = list.get(k);
               BlockPos blockPos = blockEntity.getBlockPos();
               SortedSet<BlockDestructionProgress> sortedSet = (SortedSet<BlockDestructionProgress>)destructionProgress.get(blockPos.asLong());
               CrumblingOverlay crumblingOverlay;
               if (sortedSet != null && !sortedSet.isEmpty()) {
                  poseStack.pushPose();
                  poseStack.translate(blockPos.getX() - camX, blockPos.getY() - camY, blockPos.getZ() - camZ);
                  crumblingOverlay = new CrumblingOverlay(sortedSet.last().getProgress(), poseStack.last());
                  poseStack.popPose();
               } else {
                  crumblingOverlay = null;
               }

               BlockEntityRenderState blockEntityRenderState = this.blockEntityRenderDispatcher
                  .tryExtractRenderState(blockEntity, this.partialTick, crumblingOverlay);
               if (blockEntityRenderState != null) {
                  levelRenderState.blockEntityRenderStates.add(blockEntityRenderState);
               }
            }
         }
      }

      Iterator<BlockEntity> iterator = this.level.getGloballyRenderedBlockEntities().iterator();

      while (iterator.hasNext()) {
         BlockEntity blockEntity2 = iterator.next();
         if (blockEntity2.isRemoved()) {
            iterator.remove();
         } else {
            BlockEntityRenderState blockEntityRenderState2 = this.blockEntityRenderDispatcher.tryExtractRenderState(blockEntity2, this.partialTick, null);
            if (blockEntityRenderState2 != null) {
               levelRenderState.blockEntityRenderStates.add(blockEntityRenderState2);
            }
         }
      }

      for (BlockEntityRenderState blockEntityRenderState : levelRenderState.blockEntityRenderStates) {
         BlockPos blockPos = blockEntityRenderState.blockPos;
         poseStack.pushPose();
         poseStack.translate(blockPos.getX() - camX, blockPos.getY() - camY, blockPos.getZ() - camZ);
         BlockEntityRenderDispatcher blockEntityRenderDispatcher = this.minecraft.getBlockEntityRenderDispatcher();
         blockEntityRenderDispatcher.submit(blockEntityRenderState, poseStack, submitNodeStorage, levelRenderState.cameraRenderState);
         poseStack.popPose();
      }
   }

   public void resetSampler() {
      this.terrainSampler = 0L;
   }

   public void setPartialTick(float partialTick) {
      this.partialTick = partialTick;
   }

   public void scheduleGraphUpdate() {
      this.graphNeedsUpdate = true;
   }

   public boolean graphNeedsUpdate() {
      return this.graphNeedsUpdate;
   }

   public int getVisibleSectionsCount() {
      return this.sectionGraph.getSectionQueue().size();
   }

   public void setSectionDirty(int x, int y, int z, boolean flag) {
      this.sectionGrid.setDirty(x, y, z, flag);
      this.renderRegionCache.remove(x, z);
   }

   public SectionGrid getSectionGrid() {
      return this.sectionGrid;
   }

   public ChunkAreaManager getChunkAreaManager() {
      return this.sectionGrid == null ? null : this.sectionGrid.chunkAreaManager;
   }

   public TaskDispatcher getTaskDispatcher() {
      return this.taskDispatcher;
   }

   public short getLastFrame() {
      return this.sectionGraph.getLastFrame();
   }

   public int getRenderDistance() {
      return this.renderDistance;
   }

   public String getChunkStatistics() {
      return this.sectionGraph == null ? null : this.sectionGraph.getStatistics();
   }

   public void cleanUp() {
      if (this.indirectBuffers != null) {
         Arrays.stream(this.indirectBuffers).forEach(Buffer::scheduleFree);
      }
   }

   public static WorldRenderer getInstance() {
      return INSTANCE;
   }

   public static ClientLevel getLevel() {
      return INSTANCE.level;
   }

   public static Vector3d getCameraPos() {
      return INSTANCE.cameraPos;
   }
}
