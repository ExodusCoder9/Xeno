package com.xeno.render.chunk.buffer;

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


import java.nio.ByteBuffer;
import java.util.EnumMap;
import net.minecraft.util.Mth;
import com.xeno.Initializer;
import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.build.UploadBuffer;
import com.xeno.render.chunk.build.task.CompiledSection;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.chunk.util.StaticQueue;
import com.xeno.render.shader.PipelineManager;
import com.xeno.render.vertex.CustomVertexFormat;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.memory.MemoryTypes;
import com.xeno.vulkan.memory.buffer.IndirectBuffer;
import com.xeno.vulkan.memory.buffer.UniformBuffer;
import com.xeno.vulkan.shader.Pipeline;
import com.xeno.vulkan.shader.descriptor.UBO;
import com.xeno.vulkan.util.MemoryAccess;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

public class DrawBuffers {
   public static final int INDEX_SIZE = 2;
   public static final int UNDEFINED_FACING_IDX = QuadFacing.UNDEFINED.ordinal();
   public static final float POS_OFFSET = CustomVertexFormat.getPositionOffset();
   private static final int CMD_STRIDE = 32;
   private static final long cmdBufferPtr = MemoryUtil.nmemAlignedAlloc(32L, 512L * QuadFacing.COUNT * 32L);
   private final int index;
   public final int vertexSize = PipelineManager.getTerrainVertexFormat().getVertexSize();
   private final Vector3i origin;
   private final int minHeight;
   private boolean allocated = false;
   AreaBuffer indexBuffer;
   private final EnumMap<TerrainRenderType, AreaBuffer> vertexBuffers = new EnumMap<>(TerrainRenderType.class);
   private final UniformBuffer sectionDataBuffer = new UniformBuffer(4096, MemoryTypes.HOST_MEM);
   final long drawParamsPtr;
   final int[] sectionIndices = new int[512];
   final int[] masks = new int[512];
   final long[] buildTimes = new long[512];
   final int[] activeSlots = new int[512];
   int activeSlotCount = 0;
   long latestBuildTime = 0L;
   long lastFadeUpdate = -1L;

   public DrawBuffers(int index, Vector3i origin, int minHeight) {
      this.index = index;
      this.origin = origin;
      this.minHeight = minHeight;
      this.drawParamsPtr = DrawParametersBuffer.allocateBuffer();
   }

   public void upload(RenderSection section, UploadBuffer buffer, TerrainRenderType renderType) {
      ByteBuffer[] vertexBuffers = buffer.getVertexBuffers();
      if (buffer.indexOnly) {
         long paramsPtr = DrawParametersBuffer.getParamsPtr(this.drawParamsPtr, section.inAreaIndex, renderType.ordinal(), QuadFacing.UNDEFINED.ordinal());
         int firstIndex = DrawParametersBuffer.getFirstIndex(paramsPtr);
         int indexCount = DrawParametersBuffer.getIndexCount(paramsPtr);
         int oldOffset = indexCount > 0 ? firstIndex : -1;
         AreaBuffer.Segment segment = this.indexBuffer.upload(buffer.getIndexBuffer(), oldOffset, paramsPtr);
         firstIndex = segment.offset / 2;
         DrawParametersBuffer.setFirstIndex(paramsPtr, firstIndex);
         buffer.release();
      } else {
         int oldOffset = -1;
         int size = 0;

         for (int i = 0; i < QuadFacing.COUNT; i++) {
            long paramPtr = DrawParametersBuffer.getParamsPtr(this.drawParamsPtr, section.inAreaIndex, renderType.ordinal(), i);
            int vertexOffset = DrawParametersBuffer.getVertexOffset(paramPtr);
            if (oldOffset == -1) {
               oldOffset = vertexOffset;
            }

            ByteBuffer vertexBuffer = vertexBuffers[i];
            if (vertexBuffer != null) {
               size += vertexBuffer.remaining();
            }
         }

         AreaBuffer areaBuffer = null;
         AreaBuffer.Segment segment = null;
         boolean doUpload = false;
         if (size > 0) {
            areaBuffer = this.getAreaBufferOrAlloc(renderType);
            areaBuffer.freeSegment(oldOffset);
            segment = areaBuffer.allocateSegment(size);
            doUpload = true;
         }

         int baseInstance = section.inAreaIndex;
         int offset = 0;

         for (int i = 0; i < QuadFacing.COUNT; i++) {
            long paramPtr = DrawParametersBuffer.getParamsPtr(this.drawParamsPtr, section.inAreaIndex, renderType.ordinal(), i);
            int vertexOffset = -1;
            int firstIndex = 0;
            int indexCount = 0;
            ByteBuffer vertexBuffer = vertexBuffers[i];
            int vertexCount = 0;
            if (vertexBuffer != null && doUpload) {
               areaBuffer.upload(segment, vertexBuffer, offset);
               vertexOffset = (segment.offset + offset) / this.vertexSize;
               offset += vertexBuffer.remaining();
               vertexCount = vertexBuffer.limit() / this.vertexSize;
               indexCount = vertexCount * 6 / 4;
            }

            if (i == QuadFacing.UNDEFINED.ordinal() && !buffer.autoIndices) {
               if (this.indexBuffer == null) {
                  this.indexBuffer = new AreaBuffer(AreaBuffer.Usage.INDEX, 60000, 2);
               }

               oldOffset = DrawParametersBuffer.getIndexCount(paramPtr) > 0 ? DrawParametersBuffer.getFirstIndex(paramPtr) : -1;
               AreaBuffer.Segment ibSegment = this.indexBuffer.upload(buffer.getIndexBuffer(), oldOffset, paramPtr);
               firstIndex = ibSegment.offset / 2;
            }

            DrawParametersBuffer.setIndexCount(paramPtr, indexCount);
            DrawParametersBuffer.setFirstIndex(paramPtr, firstIndex);
            DrawParametersBuffer.setVertexOffset(paramPtr, vertexOffset);
            DrawParametersBuffer.setBaseInstance(paramPtr, baseInstance);
         }

         this.updateUniformData(section);
         buffer.release();
      }
   }

   private void updateUniformData(RenderSection section) {
      int encodedOffset = this.encodeSectionOffset(section.xOffset(), section.yOffset(), section.zOffset());
      int ptrOffset = section.inAreaIndex * 4;
      MemoryAccess.memPutInt(this.sectionDataBuffer.getPointer() + ptrOffset, encodedOffset);
      if (section.getCompiledSection() == CompiledSection.UNCOMPILED) {
         long buildTime = System.currentTimeMillis();
         int idx = section.inAreaIndex;
         if (this.buildTimes[idx] == 0L) {
            this.activeSlots[this.activeSlotCount++] = idx;
         }
         this.buildTimes[idx] = buildTime;
         if (buildTime > this.latestBuildTime) {
            this.latestBuildTime = buildTime;
         }
      }
   }

   private void updateFadeUniform(long currentTime, int fadeTimeMs, float fadeTimeInv) {
      if (this.lastFadeUpdate < this.latestBuildTime + fadeTimeMs) {
         long basePtr = this.sectionDataBuffer.getPointer() + 2048;

         for (int i = 0; i < this.activeSlotCount; i++) {
            int idx = this.activeSlots[i];
            long delta = currentTime - this.buildTimes[idx];
            float fade = fadeTimeMs > 0 ? Mth.clamp((float)delta * fadeTimeInv, 0.0F, 1.0F) : 1.0F;
            MemoryAccess.memPutFloat(basePtr + idx * 4L, fade);
         }

         this.lastFadeUpdate = currentTime;
      }
   }

   private AreaBuffer getAreaBufferOrAlloc(TerrainRenderType renderType) {
      this.allocated = true;

      int initialSize = switch (renderType) {
         case SOLID -> 250000;
         case CUTOUT -> 500000;
         case TRANSLUCENT, TRIPWIRE -> 150000;
      };
      return this.vertexBuffers.computeIfAbsent(renderType, renderType1 -> new AreaBuffer(AreaBuffer.Usage.VERTEX, initialSize, this.vertexSize));
   }

   public AreaBuffer getAreaBuffer(TerrainRenderType r) {
      return this.vertexBuffers.get(r);
   }

   private boolean hasRenderType(TerrainRenderType r) {
      return this.vertexBuffers.containsKey(r);
   }

   private int encodeSectionOffset(int xOffset, int yOffset, int zOffset) {
      int xOffset1 = xOffset & 127;
      int zOffset1 = zOffset & 127;
      int yOffset1 = yOffset - this.minHeight & 127;
      return yOffset1 << 16 | zOffset1 << 8 | xOffset1;
   }

   private void updateChunkAreaOrigin(VkCommandBuffer commandBuffer, Pipeline pipeline, double camX, double camY, double camZ, MemoryStack stack) {
      float xOffset = (float)(this.origin.x + POS_OFFSET - camX);
      float yOffset = (float)(this.origin.y + POS_OFFSET - camY);
      float zOffset = (float)(this.origin.z + POS_OFFSET - camZ);
      ByteBuffer byteBuffer = stack.malloc(12);
      byteBuffer.putFloat(0, xOffset);
      byteBuffer.putFloat(4, yOffset);
      byteBuffer.putFloat(8, zOffset);
      VK10.vkCmdPushConstants(commandBuffer, pipeline.getLayout(), 1, 0, byteBuffer);
   }

   public void buildDrawBatchesIndirect(
      Vector3d cameraPos, IndirectBuffer indirectBuffer, StaticQueue<RenderSection> queue, TerrainRenderType terrainRenderType
   ) {
      long bufferPtr = cmdBufferPtr;
      boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;
      boolean backFaceCulling = Initializer.CONFIG.backFaceCulling && !isTranslucent;
      int drawCount = 0;
      long drawParamsBasePtr = this.drawParamsPtr + terrainRenderType.ordinal() * 512 * 7 * 16L;
      long facingsStride = 112L;
      int count = 0;
      if (backFaceCulling) {
         for (RenderSection section : queue) {
            this.sectionIndices[count] = section.inAreaIndex;
            this.masks[count] = this.getMask(cameraPos, section);
            count++;
         }

         long ptr = bufferPtr;

         for (int j = 0; j < count; j++) {
            int sectionIdx = this.sectionIndices[j];
            int mask = this.masks[j];
            long drawParamsBasePtr2 = drawParamsBasePtr + sectionIdx * 112L;
            int indexCount = 0;
            int firstIndex = 0;
            int vertexOffset = 0;
            int baseInstance = 0;

            for (int i = 0; i < QuadFacing.COUNT; i++) {
               if ((mask & 1 << i) == 0) {
                  drawParamsBasePtr2 += 16L;
                  if (indexCount > 0) {
                     MemoryAccess.memPutInt(ptr, indexCount);
                     MemoryAccess.memPutInt(ptr + 4L, 1);
                     MemoryAccess.memPutInt(ptr + 8L, firstIndex);
                     MemoryAccess.memPutInt(ptr + 12L, vertexOffset);
                     MemoryAccess.memPutInt(ptr + 16L, baseInstance);
                     ptr += 32L;
                     drawCount++;
                  }

                  indexCount = 0;
                  firstIndex = 0;
                  vertexOffset = 0;
                  baseInstance = 0;
               } else {
                  long drawParamsPtr = drawParamsBasePtr2;
                  int indexCount_i = DrawParametersBuffer.getIndexCount(drawParamsPtr);
                  int firstIndex_i = DrawParametersBuffer.getFirstIndex(drawParamsPtr);
                  int vertexOffset_i = DrawParametersBuffer.getVertexOffset(drawParamsPtr);
                  int baseInstance_i = DrawParametersBuffer.getBaseInstance(drawParamsPtr);
                  if (indexCount == 0) {
                     indexCount = indexCount_i;
                     firstIndex = firstIndex_i;
                     vertexOffset = vertexOffset_i;
                     baseInstance = baseInstance_i;
                  } else {
                     indexCount += indexCount_i;
                  }

                  drawParamsBasePtr2 += 16L;
               }
            }

            if (indexCount > 0) {
               MemoryAccess.memPutInt(ptr, indexCount);
               MemoryAccess.memPutInt(ptr + 4L, 1);
               MemoryAccess.memPutInt(ptr + 8L, firstIndex);
               MemoryAccess.memPutInt(ptr + 12L, vertexOffset);
               MemoryAccess.memPutInt(ptr + 16L, baseInstance);
               ptr += 32L;
               drawCount++;
            }
         }
      } else {
         for (RenderSection section : queue) {
            this.sectionIndices[count] = section.inAreaIndex;
            count++;
         }

         long facingOffset = UNDEFINED_FACING_IDX * 16L;
         drawParamsBasePtr += facingOffset;
         long ptr = bufferPtr;

         for (int i = 0; i < count; i++) {
            int sectionIdx = this.sectionIndices[i];
            long drawParamsPtr = drawParamsBasePtr + sectionIdx * 112L;
            int indexCount = DrawParametersBuffer.getIndexCount(drawParamsPtr);
            int firstIndex = DrawParametersBuffer.getFirstIndex(drawParamsPtr);
            int vertexOffset = DrawParametersBuffer.getVertexOffset(drawParamsPtr);
            int baseInstance = DrawParametersBuffer.getBaseInstance(drawParamsPtr);
            if (indexCount > 0) {
               MemoryAccess.memPutInt(ptr, indexCount);
               MemoryAccess.memPutInt(ptr + 4L, 1);
               MemoryAccess.memPutInt(ptr + 8L, firstIndex);
               MemoryAccess.memPutInt(ptr + 12L, vertexOffset);
               MemoryAccess.memPutInt(ptr + 16L, baseInstance);
               ptr += 32L;
               drawCount++;
            }
         }
      }

      if (drawCount != 0) {
         ByteBuffer byteBuffer = MemoryUtil.memByteBuffer(cmdBufferPtr, queue.size() * QuadFacing.COUNT * 32);
         indirectBuffer.recordCopyCmd(byteBuffer.position(0));
          VK10.vkCmdDrawIndexedIndirect(Renderer.getCommandBuffer(), indirectBuffer.getId(), indirectBuffer.getOffset(), drawCount, 32);
      }
   }

   public void buildDrawBatchesGpu(
      Vector3d cameraPos, StaticQueue<RenderSection> queue, TerrainRenderType renderType, int currentFrame
   ) {
      GpuDrawBatcher batcher = GpuDrawBatcher.getInstance();
      if (!batcher.isInitialized()) {
         batcher.init();
      }
      if (!batcher.isInitialized()) return;

      VkCommandBuffer cmdBuffer = Renderer.getCommandBuffer();
      int sectionCount = queue.size();
      if (sectionCount == 0) return;

      // Flatten queue into array for upload
      RenderSection[] sections = new RenderSection[sectionCount];
      int i = 0;
      for (RenderSection s : queue) {
         sections[i++] = s;
      }

      batcher.buildBatchesGpu(
         cmdBuffer, sections, sectionCount, cameraPos, renderType,
         this.drawParamsPtr, currentFrame
      );
   }

   public void buildDrawBatchesDirect(Vector3d cameraPos, StaticQueue<RenderSection> queue, TerrainRenderType terrainRenderType) {
      boolean isTranslucent = terrainRenderType == TerrainRenderType.TRANSLUCENT;
      boolean backFaceCulling = Initializer.CONFIG.backFaceCulling && !isTranslucent;
      VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
      long drawParamsBasePtr = this.drawParamsPtr + terrainRenderType.ordinal() * 512 * 7 * 16L;
      long facingsStride = 112L;
      int count = 0;
      if (backFaceCulling) {
         for (RenderSection section : queue) {
            this.sectionIndices[count] = section.inAreaIndex;
            this.masks[count] = this.getMask(cameraPos, section);
            count++;
         }

         for (int j = 0; j < count; j++) {
            int sectionIdx = this.sectionIndices[j];
            int mask = this.masks[j];
            long drawParamsBasePtr2 = drawParamsBasePtr + sectionIdx * 112L;
            int indexCount = 0;
            int firstIndex = 0;
            int vertexOffset = 0;
            int baseInstance = 0;

            for (int i = 0; i < QuadFacing.COUNT; i++) {
               if ((mask & 1 << i) == 0) {
                  drawParamsBasePtr2 += 16L;
                  if (indexCount > 0) {
                     VK10.vkCmdDrawIndexed(commandBuffer, indexCount, 1, firstIndex, vertexOffset, baseInstance);
                  }

                  indexCount = 0;
                  firstIndex = 0;
                  vertexOffset = 0;
                  baseInstance = 0;
               } else {
                  long drawParamsPtr = drawParamsBasePtr2;
                  int indexCount_i = DrawParametersBuffer.getIndexCount(drawParamsPtr);
                  int firstIndex_i = DrawParametersBuffer.getFirstIndex(drawParamsPtr);
                  int vertexOffset_i = DrawParametersBuffer.getVertexOffset(drawParamsPtr);
                  int baseInstance_i = DrawParametersBuffer.getBaseInstance(drawParamsPtr);
                  if (indexCount == 0) {
                     indexCount = indexCount_i;
                     firstIndex = firstIndex_i;
                     vertexOffset = vertexOffset_i;
                     baseInstance = baseInstance_i;
                  } else {
                     indexCount += indexCount_i;
                  }

                  drawParamsBasePtr2 += 16L;
               }
            }

            if (indexCount > 0) {
               VK10.vkCmdDrawIndexed(commandBuffer, indexCount, 1, firstIndex, vertexOffset, baseInstance);
            }
         }
      } else {
         long facingOffset = UNDEFINED_FACING_IDX * 16L;
         drawParamsBasePtr += facingOffset;

         for (RenderSection section : queue) {
            this.sectionIndices[count] = section.inAreaIndex;
            count++;
         }

         for (int i = 0; i < count; i++) {
            int sectionIdx = this.sectionIndices[i];
            long drawParamsPtr = drawParamsBasePtr + sectionIdx * 112L;
            int indexCount = DrawParametersBuffer.getIndexCount(drawParamsPtr);
            int firstIndex = DrawParametersBuffer.getFirstIndex(drawParamsPtr);
            int vertexOffset = DrawParametersBuffer.getVertexOffset(drawParamsPtr);
            int baseInstance = DrawParametersBuffer.getBaseInstance(drawParamsPtr);
            if (indexCount > 0) {
               VK10.vkCmdDrawIndexed(commandBuffer, indexCount, 1, firstIndex, vertexOffset, baseInstance);
            }
         }
      }
   }

   private int getMask(Vector3d camera, RenderSection section) {
      int secX = section.xOffset;
      int secY = section.yOffset;
      int secZ = section.zOffset;
      int mask = 1 << UNDEFINED_FACING_IDX;
      mask |= camera.x - secX >= 0.0 ? 1 << QuadFacing.X_POS.ordinal() : 0;
      mask |= camera.y - secY >= 0.0 ? 1 << QuadFacing.Y_POS.ordinal() : 0;
      mask |= camera.z - secZ >= 0.0 ? 1 << QuadFacing.Z_POS.ordinal() : 0;
      mask |= camera.x - (secX + 16) < 0.0 ? 1 << QuadFacing.X_NEG.ordinal() : 0;
      mask |= camera.y - (secY + 16) < 0.0 ? 1 << QuadFacing.Y_NEG.ordinal() : 0;
      return mask | (camera.z - (secZ + 16) < 0.0 ? 1 << QuadFacing.Z_NEG.ordinal() : 0);
   }

   public void bindBuffers(
      VkCommandBuffer commandBuffer,
      Pipeline pipeline,
      TerrainRenderType terrainRenderType,
      double camX,
      double camY,
      double camZ,
      long currentTime,
      int fadeTimeMs,
      float fadeTimeInv
   ) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         AreaBuffer vertexBuffer = this.getAreaBuffer(terrainRenderType);
         VK10.nvkCmdBindVertexBuffers(commandBuffer, 0, 1, stack.npointer(vertexBuffer.getId()), stack.npointer(0L));
         this.updateChunkAreaOrigin(commandBuffer, pipeline, camX, camY, camZ, stack);
      } catch (Throwable var18) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var17) {
               var18.addSuppressed(var17);
            }
         }

         throw var18;
      }

      if (stack != null) {
         stack.close();
      }

      this.updateFadeUniform(currentTime, fadeTimeMs, fadeTimeInv);
      UBO ubo = pipeline.getUBO(2);
      ubo.setUseGlobalBuffer(false);
      ubo.getBufferSlice().set(this.sectionDataBuffer, 0L, (int)this.sectionDataBuffer.getBufferSize());
      if (terrainRenderType == TerrainRenderType.TRANSLUCENT && this.indexBuffer != null) {
         VK10.vkCmdBindIndexBuffer(commandBuffer, this.indexBuffer.getId(), 0L, 0);
      }
   }

   public void releaseBuffers() {
      if (this.allocated) {
         this.vertexBuffers.values().forEach(AreaBuffer::freeBuffer);
         this.vertexBuffers.clear();
         if (this.indexBuffer != null) {
            this.indexBuffer.freeBuffer();
         }

         this.indexBuffer = null;
         this.allocated = false;
      }
   }

   public void free() {
      this.releaseBuffers();
      DrawParametersBuffer.freeBuffer(this.drawParamsPtr);
   }

   public boolean isAllocated() {
      return !this.vertexBuffers.isEmpty();
   }

   public EnumMap<TerrainRenderType, AreaBuffer> getVertexBuffers() {
      return this.vertexBuffers;
   }

   public AreaBuffer getIndexBuffer() {
      return this.indexBuffer;
   }

   public long getDrawParamsPtr() {
      return this.drawParamsPtr;
   }
}
