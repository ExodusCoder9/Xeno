package com.xeno.vulkan.memory;

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


import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.nio.LongBuffer;
import java.util.List;
import java.util.function.Consumer;
import com.xeno.Initializer;
import com.xeno.render.chunk.buffer.AreaBuffer;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.queue.Queue;
import com.xeno.vulkan.texture.VulkanImage;
import com.xeno.vulkan.util.Pair;
import com.xeno.vulkan.util.VkResult;
import org.apache.commons.lang3.Validate;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaBudget;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;

public class MemoryManager {
   private static final boolean DEBUG = false;
   public static final long BYTES_IN_MB = 1048576L;
   private static MemoryManager INSTANCE;
   private static final long ALLOCATOR = Vulkan.getAllocator();
   private static final Long2ReferenceOpenHashMap<Buffer> buffers = new Long2ReferenceOpenHashMap();
   private static final Long2ReferenceOpenHashMap<VulkanImage> images = new Long2ReferenceOpenHashMap();
   static int Frames;
   private static long deviceMemory = 0L;
   private static long nativeMemory = 0L;
   private int currentFrame = 0;
   private final ObjectArrayList<Buffer.BufferInfo>[] freeableBuffers;
   private final ObjectArrayList<VulkanImage>[] freeableImages;
   private final ObjectArrayList<Runnable>[] frameOps;
   private final ObjectArrayList<Pair<AreaBuffer, Integer>>[] segmentsToFree;
   private ObjectArrayList<StackTraceElement[]>[] stackTraces;

   public static MemoryManager getInstance() {
      return INSTANCE;
   }

   public static void createInstance(int frames) {
      Frames = frames;
      INSTANCE = new MemoryManager();
   }

   MemoryManager() {
      this.freeableBuffers = new ObjectArrayList[Frames];
      this.freeableImages = new ObjectArrayList[Frames];
      this.frameOps = new ObjectArrayList[Frames];
      this.segmentsToFree = new ObjectArrayList[Frames];

      for (int i = 0; i < Frames; i++) {
         this.freeableBuffers[i] = new ObjectArrayList();
         this.freeableImages[i] = new ObjectArrayList();
         this.frameOps[i] = new ObjectArrayList();
         this.segmentsToFree[i] = new ObjectArrayList();
      }
   }

   public synchronized void initFrame(int frame) {
      this.setCurrentFrame(frame);
      this.freeBuffers(frame);
      this.freeImages(frame);
      this.doFrameOps(frame);
      this.freeSegments(frame);
   }

   public void setCurrentFrame(int frame) {
      Validate.isTrue(frame < Frames, "Out of bounds frame index", new Object[0]);
      this.currentFrame = frame;
   }

    public void freeAllBuffers() {
       for (int frame = 0; frame < Frames; frame++) {
          this.freeBuffers(frame);
          this.freeImages(frame);
          this.doFrameOps(frame);
       }
       // Safety net: free any remaining tracked buffers not in freeable lists
       synchronized (this) {
          if (!buffers.isEmpty()) {
             Initializer.LOGGER.warn("Freeing {} untracked buffer(s) at shutdown", buffers.size());
             long[] keys = buffers.keySet().toLongArray();
             for (long key : keys) {
                Buffer buf = buffers.get(key);
                if (buf != null) {
                   Vma.vmaDestroyBuffer(ALLOCATOR, key, buf.getAllocation());
                   buffers.remove(key);
                }
             }
          }
          if (!images.isEmpty()) {
             Initializer.LOGGER.warn("Freeing {} untracked image(s) at shutdown", images.size());
             long[] keys = images.keySet().toLongArray();
             for (long key : keys) {
                VulkanImage img = images.get(key);
                if (img != null) {
                   img.doFree();
                }
             }
          }
       }
    }

   public void createBuffer(long size, int usage, int properties, LongBuffer pBuffer, PointerBuffer pBufferMemory) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack);
         bufferInfo.sType(12);
         bufferInfo.size(size);
         bufferInfo.usage(usage);
         VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
         allocationInfo.requiredFlags(properties);
         int result = Vma.vmaCreateBuffer(ALLOCATOR, bufferInfo, allocationInfo, pBuffer, pBufferMemory, null);
         if (result != 0) {
            Initializer.LOGGER.info(String.format("Failed to create buffer with size: %.3f MB", (float)size / 1048576.0F));
            Initializer.LOGGER.info(String.format("Tracked Device Memory used: %d/%d MB", this.getAllocatedDeviceMemoryMB(), this.getDeviceMemoryMB()));
            Initializer.LOGGER.info(this.getHeapStats());
            throw new RuntimeException("Failed to create buffer: %s".formatted(VkResult.decode(result)));
         }
      } catch (Throwable var12) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var11) {
               var12.addSuppressed(var11);
            }
         }

         throw var12;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public synchronized void createBuffer(Buffer buffer, long size, int usage, int properties) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         LongBuffer pBuffer = stack.mallocLong(1);
         PointerBuffer pAllocation = stack.pointers(0L);
         this.createBuffer(size, usage, properties, pBuffer, pAllocation);
         buffer.setId(pBuffer.get(0));
         buffer.setAllocation(pAllocation.get(0));
         buffer.setBufferSize(size);
         if ((properties & 1) != 0) {
            deviceMemory += size;
         } else {
            nativeMemory += size;
         }

         buffers.putIfAbsent(buffer.getId(), buffer);
      } catch (Throwable var10) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public void createImage(
      int width,
      int height,
      int arrayLayers,
      int mipLevels,
      int format,
      int tiling,
      int usage,
      int flags,
      int memProperties,
      LongBuffer pTextureImage,
      PointerBuffer pTextureImageMemory
   ) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack);
         imageInfo.sType(14);
         imageInfo.imageType(1);
         imageInfo.extent().width(width);
         imageInfo.extent().height(height);
         imageInfo.extent().depth(1);
         imageInfo.mipLevels(mipLevels);
         imageInfo.arrayLayers(arrayLayers);
         imageInfo.format(format);
         imageInfo.tiling(tiling);
         imageInfo.initialLayout(0);
         imageInfo.usage(usage);
         imageInfo.samples(1);
         imageInfo.flags(flags);
         imageInfo.pQueueFamilyIndices(stack.ints(Queue.getQueueFamilies().graphicsFamily, Queue.getQueueFamilies().computeFamily));
         VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
         allocationInfo.requiredFlags(memProperties);
         int result = Vma.vmaCreateImage(ALLOCATOR, imageInfo, allocationInfo, pTextureImage, pTextureImageMemory, null);
         if (result != 0) {
            Initializer.LOGGER.info(String.format("Failed to create image with size: %dx%d", width, height));
            throw new RuntimeException("Failed to create image: %s".formatted(VkResult.decode(result)));
         }
      } catch (Throwable var17) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var16) {
               var17.addSuppressed(var16);
            }
         }

         throw var17;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static void addImage(VulkanImage image) {
      images.putIfAbsent(image.getId(), image);
      deviceMemory = deviceMemory + image.size;
   }

   public static void MapAndCopy(long allocation, Consumer<PointerBuffer> consumer) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         PointerBuffer data = stack.mallocPointer(1);
         Vma.vmaMapMemory(ALLOCATOR, allocation, data);
         consumer.accept(data);
         Vma.vmaUnmapMemory(ALLOCATOR, allocation);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public PointerBuffer Map(long allocation) {
      PointerBuffer data = MemoryUtil.memAllocPointer(1);
      Vma.vmaMapMemory(ALLOCATOR, allocation, data);
      return data;
   }

   public static void freeBuffer(long buffer, long allocation) {
      Vma.vmaDestroyBuffer(ALLOCATOR, buffer, allocation);
      buffers.remove(buffer);
   }

   private static void freeBuffer(Buffer.BufferInfo bufferInfo) {
      Vma.vmaDestroyBuffer(ALLOCATOR, bufferInfo.id(), bufferInfo.allocation());
      if (bufferInfo.type() == MemoryType.Type.DEVICE_LOCAL) {
         deviceMemory = deviceMemory - bufferInfo.bufferSize();
      } else {
         nativeMemory = nativeMemory - bufferInfo.bufferSize();
      }

      buffers.remove(bufferInfo.id());
   }

   public static void freeImage(long imageId, long allocation) {
      Vma.vmaDestroyImage(ALLOCATOR, imageId, allocation);
      VulkanImage image = (VulkanImage)images.remove(imageId);
      deviceMemory = deviceMemory - image.size;
   }

   public synchronized void addToFreeable(Buffer buffer) {
      Buffer.BufferInfo bufferInfo = buffer.getBufferInfo();
      this.checkBuffer(bufferInfo);
      this.freeableBuffers[this.currentFrame].add(bufferInfo);
   }

   public synchronized void addToFreeable(VulkanImage image) {
      this.freeableImages[this.currentFrame].add(image);
   }

   public synchronized void addFrameOp(Runnable runnable) {
      this.frameOps[this.currentFrame].add(runnable);
   }

   public void doFrameOps(int frame) {
      ObjectListIterator var2 = this.frameOps[frame].iterator();

      while (var2.hasNext()) {
         Runnable runnable = (Runnable)var2.next();
         runnable.run();
      }

      this.frameOps[frame].clear();
   }

   private void freeBuffers(int frame) {
      List<Buffer.BufferInfo> bufferList = this.freeableBuffers[frame];

      for (Buffer.BufferInfo bufferInfo : bufferList) {
         freeBuffer(bufferInfo);
      }

      bufferList.clear();
   }

   private void freeImages(int frame) {
      List<VulkanImage> bufferList = this.freeableImages[frame];

      for (VulkanImage image : bufferList) {
         image.doFree();
      }

      bufferList.clear();
   }

   private void checkBuffer(Buffer.BufferInfo bufferInfo) {
      if (buffers.get(bufferInfo.id()) == null) {
         throw new RuntimeException("trying to free not present buffer");
      }
   }

   private void freeSegments(int frame) {
      ObjectArrayList<Pair<AreaBuffer, Integer>> list = this.segmentsToFree[frame];
      ObjectListIterator var3 = list.iterator();

      while (var3.hasNext()) {
         Pair<AreaBuffer, Integer> pair = (Pair<AreaBuffer, Integer>)var3.next();
         pair.first.setSegmentFree(pair.second);
      }

      list.clear();
   }

   public void addToFreeSegment(AreaBuffer areaBuffer, int offset) {
      this.segmentsToFree[this.currentFrame].add(new Pair<>(areaBuffer, offset));
   }

   public int getNativeMemoryMB() {
      return this.bytesInMb(nativeMemory);
   }

   public int getAllocatedDeviceMemoryMB() {
      return this.bytesInMb(deviceMemory);
   }

   public int getDeviceMemoryMB() {
      return this.bytesInMb(MemoryTypes.GPU_MEM.vkMemoryHeap.size());
   }

   int bytesInMb(long bytes) {
      return (int)(bytes / 1048576L);
   }

   public String getHeapStats() {
      MemoryStack stack = MemoryStack.stackPush();

      String var8;
      try {
         org.lwjgl.util.vma.VmaBudget.Buffer vmaBudgets = VmaBudget.calloc(DeviceManager.memoryProperties.memoryHeapCount(), stack);
         Vma.vmaGetHeapBudgets(ALLOCATOR, vmaBudgets);
         VmaBudget vmaBudget = (VmaBudget)vmaBudgets.get(MemoryTypes.GPU_MEM.vkMemoryType.heapIndex());
         long usage = vmaBudget.usage();
         long budget = vmaBudget.budget();
         var8 = String.format("Device Memory Heap Usage: %d/%dMB", this.bytesInMb(usage), this.bytesInMb(budget));
      } catch (Throwable var10) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (stack != null) {
         stack.close();
      }

      return var8;
   }
}
