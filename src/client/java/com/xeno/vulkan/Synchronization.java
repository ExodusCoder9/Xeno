package com.xeno.vulkan;

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


import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.LongBuffer;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.queue.CommandPool;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;

public class Synchronization {
   private static final int ALLOCATION_SIZE = 500;
   public static final Synchronization INSTANCE = new Synchronization(ALLOCATION_SIZE);
   private final LongBuffer fences;
   private int idx = 0;
   private final ObjectArrayList<CommandPool.CommandBuffer> fenceCbs = new ObjectArrayList();
   private final LongArrayList semaphores = new LongArrayList();
   private final ObjectArrayList<CommandPool.CommandBuffer> semaphoreCbs = new ObjectArrayList();

   Synchronization(int allocSize) {
      this.fences = MemoryUtil.memAllocLong(allocSize);
   }

   public void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
      this.addCommandBuffer(commandBuffer, false);
   }

   public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer, boolean useSemaphore) {
      if (!useSemaphore) {
         this.addFence(commandBuffer.getFence());
         this.fenceCbs.add(commandBuffer);
      } else {
         this.semaphores.add(commandBuffer.getSemaphore());
         this.semaphoreCbs.add(commandBuffer);
      }
   }

   public synchronized void addFence(long fence) {
      if (this.idx >= ALLOCATION_SIZE) {
         this.waitFences();
      }

      this.fences.put(this.idx, fence);
      this.idx++;
   }

   public synchronized void waitFences() {
      if (this.idx != 0) {
         VkDevice device = Vulkan.getVkDevice();
         this.fences.limit(this.idx);
         VK10.vkWaitForFences(device, this.fences, false, -1L);
         this.fenceCbs.forEach(CommandPool.CommandBuffer::reset);
         this.fenceCbs.clear();
         this.fences.limit(50);
         this.idx = 0;
      }
   }

   public synchronized void addWaitSemaphore(long semaphore) {
      this.semaphores.add(semaphore);
   }

   public int getWaitSemaphoreCount() {
      return this.semaphores.size();
   }

   public void getWaitSemaphores(LongBuffer buffer) {
      buffer.put(this.semaphores.elements(), 0, this.semaphores.size());
      this.semaphores.clear();
   }

   public void scheduleCbReset() {
      ObjectArrayList<CommandPool.CommandBuffer> pending = this.semaphoreCbs.clone();
      MemoryManager.getInstance().addFrameOp(() -> pending.forEach(CommandPool.CommandBuffer::reset));
      this.semaphoreCbs.clear();
   }

   public static void waitFence(long fence) {
      VkDevice device = Vulkan.getVkDevice();
      VK10.vkWaitForFences(device, fence, true, -1L);
   }

   public static boolean checkFenceStatus(long fence) {
      VkDevice device = Vulkan.getVkDevice();
      return VK10.vkGetFenceStatus(device, fence) == 0;
   }
}
