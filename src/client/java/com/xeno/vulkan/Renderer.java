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


import com.mojang.blaze3d.opengl.GlStateManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import com.xeno.Initializer;
import com.xeno.gl.VkGlFramebuffer;
import com.xeno.mixin.window.WindowAccessor;
import com.xeno.render.chunk.WorldRenderer;
import com.xeno.render.entity.EntityBatcher;
import com.xeno.render.chunk.buffer.UploadManager;
import com.xeno.render.profiling.Profiler;
import com.xeno.render.shader.PipelineManager;
import com.xeno.render.texture.ImageUploadHelper;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.framebuffer.Framebuffer;
import com.xeno.vulkan.framebuffer.RenderPass;
import com.xeno.vulkan.framebuffer.SwapChain;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.pass.DefaultMainPass;
import com.xeno.vulkan.pass.MainPass;
import com.xeno.vulkan.queue.CommandPool;
import com.xeno.vulkan.queue.TransferQueue;
import com.xeno.vulkan.shader.GraphicsPipeline;
import com.xeno.vulkan.shader.Pipeline;
import com.xeno.vulkan.shader.PipelineState;
import com.xeno.vulkan.shader.Uniforms;
import com.xeno.vulkan.shader.layout.PushConstants;
import com.xeno.vulkan.texture.VTextureSelector;
import com.xeno.vulkan.util.VkResult;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearRect;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkClearAttachment.Buffer;

public class Renderer {
   private static Renderer INSTANCE;
   private static VkDevice device;
   private static boolean swapChainUpdate = false;
   private final Set<Pipeline> usedPipelines = new ObjectOpenHashSet();
   private Pipeline boundPipeline;
   private long boundPipelineHandle;
    private Drawer drawer;
    private EntityBatcher entityBatcher;
    private SwapChain swapChain;
   private int framesNum;
   private List<VkCommandBuffer> mainCommandBuffers;
   private long[] imageAvailableSemaphores;
   private long[] renderFinishedSemaphores;
   private long[] inFlightFences;
   private List<CommandPool.CommandBuffer> transferCbs;
   private Framebuffer boundFramebuffer;
   private RenderPass boundRenderPass;
    private static int currentFrame = 0;
    private static int imageIndex;
    private static int lastReset = -1;
    private VkCommandBuffer currentCmdBuffer;
    private boolean recordingCmds = false;
    private boolean consumedAcquireSemaphore = false;
    int recursion = 0;
   MainPass mainPass;
   private final List<Runnable> onResizeCallbacks = new ObjectArrayList();

   public static void initRenderer() {
      INSTANCE = new Renderer();
      INSTANCE.init();
   }

   public static Renderer getInstance() {
      return INSTANCE;
   }

    public static Drawer getDrawer() {
       return INSTANCE.drawer;
    }

    public static EntityBatcher getEntityBatcher() {
       return INSTANCE.entityBatcher;
    }

   public static int getCurrentFrame() {
      return currentFrame;
   }

   public static int getCurrentImage() {
      return imageIndex;
   }

   public Renderer() {
      device = Vulkan.getVkDevice();
      this.framesNum = Initializer.CONFIG.frameQueueSize;
   }

   public static void setLineWidth(float width) {
      if (INSTANCE.boundFramebuffer != null) {
         VK10.vkCmdSetLineWidth(INSTANCE.currentCmdBuffer, width);
      }
   }

   private void init() {
      MemoryManager.createInstance(getFramesNum());
      Vulkan.createStagingBuffers();
      this.swapChain = new SwapChain();
      this.mainPass = DefaultMainPass.create();
      this.drawer = new Drawer();
       this.drawer.createResources(this.framesNum);
       this.entityBatcher = new EntityBatcher();
       Uniforms.setupDefaultUniforms();
      PipelineManager.init();
      UploadManager.createInstance();
      this.allocateCommandBuffers();
      this.createSyncObjects();
   }

   private void allocateCommandBuffers() {
      if (this.mainCommandBuffers != null) {
         this.mainCommandBuffers.forEach(commandBuffer -> VK10.vkFreeCommandBuffers(device, Vulkan.getCommandPool(), commandBuffer));
      }

      this.mainCommandBuffers = new ArrayList<>(this.framesNum);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
         allocInfo.sType(40);
         allocInfo.commandPool(Vulkan.getCommandPool());
         allocInfo.level(0);
         allocInfo.commandBufferCount(this.framesNum);
         PointerBuffer pCommandBuffers = stack.mallocPointer(this.framesNum);
         int vkResult = VK10.vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers);
         if (vkResult != 0) {
            throw new RuntimeException("Failed to allocate command buffers: %s".formatted(VkResult.decode(vkResult)));
         }

         for (int i = 0; i < this.framesNum; i++) {
            this.mainCommandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), device));
         }
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

      if (this.transferCbs != null) {
         this.transferCbs.forEach(commandBuffer -> {
            VK10.vkResetCommandBuffer(commandBuffer.handle, 0);
            commandBuffer.reset();
         });
      }

      this.transferCbs = new ArrayList<>(this.framesNum);

      for (int i = 0; i < this.framesNum; i++) {
         this.transferCbs.add(DeviceManager.getTransferQueue().getCommandPool().getCommandBuffer());
      }
   }

   private void createSyncObjects() {
      int swapChainImages = this.swapChain.getImagesNum();
      this.renderFinishedSemaphores = new long[swapChainImages];
      this.imageAvailableSemaphores = new long[this.framesNum];
      this.inFlightFences = new long[this.framesNum];
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
         semaphoreInfo.sType(9);
         VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
         fenceInfo.sType(8);
         fenceInfo.flags(1);
         LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
         LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
         LongBuffer pFence = stack.mallocLong(1);

         for (int i = 0; i < this.framesNum; i++) {
            if (VK10.vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != 0 || VK10.vkCreateFence(device, fenceInfo, null, pFence) != 0) {
               throw new RuntimeException("Failed to create synchronization objects for the frame: " + i);
            }

            this.imageAvailableSemaphores[i] = pImageAvailableSemaphore.get(0);
            this.inFlightFences[i] = pFence.get(0);
         }

         for (int i = 0; i < this.swapChain.getImagesNum(); i++) {
            if (VK10.vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != 0) {
               throw new RuntimeException("Failed to create synchronization objects for the image: " + i);
            }

            this.renderFinishedSemaphores[i] = pRenderFinishedSemaphore.get(0);
         }
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

   public void preInitFrame() {
      Profiler p = Profiler.getMainProfiler();
      p.pop();
      p.round();
      p.push("Frame_ops");
      this.drawer.resetBuffers(currentFrame);
      WorldRenderer.getInstance().uploadSections();
      UploadManager.INSTANCE.submitUploads();
   }

   public void beginFrame() {
      this.recursion++;
      if (swapChainUpdate && this.recursion <= 1) {
         this.recreateSwapChain();
         swapChainUpdate = false;
      }

      if (this.recursion > 1) {
         this.endFrame();
      }

      Profiler p = Profiler.getMainProfiler();
      p.pop();
      p.push("Frame_fence");
      VK10.vkWaitForFences(device, this.inFlightFences[currentFrame], true, -1L);
      p.pop();
      p.push("Begin_rendering");
      this.submitUploads();
      MemoryManager.getInstance().initFrame(currentFrame);
      this.drawer.setCurrentFrame(currentFrame);
      Vulkan.getStagingBuffers().beginFrame(currentFrame);
      this.preInitFrame();
      this.resetDescriptors();
      this.currentCmdBuffer = this.mainCommandBuffers.get(currentFrame);
      VK10.vkResetCommandBuffer(this.currentCmdBuffer, 0);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         if (this.swapChain.hasImages()) {
            IntBuffer pImageIndex = stack.mallocInt(1);
            long semaphore = this.imageAvailableSemaphores[currentFrame];
            int vkResult = KHRSwapchain.vkAcquireNextImageKHR(device, this.swapChain.getId(), -1L, semaphore, 0L, pImageIndex);
            if (vkResult != 1000001003 && vkResult != -1000001004 && !swapChainUpdate) {
               if (vkResult != 0) {
                  throw new RuntimeException("Cannot acquire next swap chain image: %s".formatted(VkResult.decode(vkResult)));
               }
            } else {
               swapChainUpdate = true;
            }

            imageIndex = pImageIndex.get(0);
            this.swapChain.setAcquired(true);
         }

         this.beginMainRenderPass(stack);
      } catch (Throwable var8) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (stack != null) {
         stack.close();
      }

      p.pop();
   }

   private void beginMainRenderPass(MemoryStack stack) {
      VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
      beginInfo.sType(42);
      beginInfo.flags(1);
      VkCommandBuffer commandBuffer = this.currentCmdBuffer;
      int vkResult = VK10.vkBeginCommandBuffer(commandBuffer, beginInfo);
      if (vkResult != 0) {
         throw new RuntimeException("Failed to begin recording command buffer: %s".formatted(VkResult.decode(vkResult)));
      }

      this.recordingCmds = true;
      this.mainPass.begin(commandBuffer, stack);
      resetDynamicState(commandBuffer);
   }

   public void endFrame() {
      if (this.recordingCmds) {
         if (this.recursion != 0) {
            this.recursion--;
            Profiler p = Profiler.getMainProfiler();
            p.push("End_rendering");
            this.mainPass.end(this.currentCmdBuffer);
            this.submitUploads();
            this.waitFences();
            this.submitFrame();
            this.recordingCmds = false;
            this.boundRenderPass = null;
            this.boundFramebuffer = null;
            p.pop();
            p.push("Post_rendering");
         }
      }
   }

    private void submitFrame() {
       MemoryStack stack = MemoryStack.stackPush();

       label80: {
          try {
             VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
             submitInfo.sType(4);
             int waitSemaphoreCount = Synchronization.INSTANCE.getWaitSemaphoreCount();
             int totalWaitSemaphores = waitSemaphoreCount;
             if (this.swapChain.isAcquired() && !this.consumedAcquireSemaphore) {
                totalWaitSemaphores++;
             }

             LongBuffer waitSemaphores = stack.mallocLong(totalWaitSemaphores);
             IntBuffer waitDstStageMask = stack.mallocInt(totalWaitSemaphores);
             Synchronization.INSTANCE.getWaitSemaphores(waitSemaphores);

             for (int i = 0; i < waitSemaphoreCount; i++) {
                waitDstStageMask.put(i, 1);
             }

             if (this.swapChain.isAcquired() && !this.consumedAcquireSemaphore) {
                waitSemaphores.put(totalWaitSemaphores - 1, this.imageAvailableSemaphores[currentFrame]);
                waitDstStageMask.put(totalWaitSemaphores - 1, 1024);
             }

             waitSemaphores.position(0);
             waitSemaphores.limit(totalWaitSemaphores);
             submitInfo.pWaitSemaphores(waitSemaphores);
             submitInfo.waitSemaphoreCount(waitSemaphores.limit());
             submitInfo.pWaitDstStageMask(waitDstStageMask);
             submitInfo.pCommandBuffers(stack.pointers(this.currentCmdBuffer));
             if (this.swapChain.isAcquired()) {
                submitInfo.pSignalSemaphores(stack.longs(this.renderFinishedSemaphores[imageIndex]));
             }

             VK10.vkResetFences(device, this.inFlightFences[currentFrame]);
             int vkResult;
             if ((vkResult = VK10.vkQueueSubmit(DeviceManager.getGraphicsQueue().vkQueue(), submitInfo, this.inFlightFences[currentFrame])) != 0) {
                VK10.vkResetFences(device, this.inFlightFences[currentFrame]);
                throw new RuntimeException("Failed to submit draw command buffer: %s".formatted(VkResult.decode(vkResult)));
             }

             Synchronization.INSTANCE.scheduleCbReset();
             if (this.swapChain.isAcquired()) {
                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
                presentInfo.sType(1000001001);
                presentInfo.pWaitSemaphores(stack.longs(this.renderFinishedSemaphores[imageIndex]));
                presentInfo.swapchainCount(1);
                presentInfo.pSwapchains(stack.longs(this.swapChain.getId()));
                presentInfo.pImageIndices(stack.ints(imageIndex));
                vkResult = KHRSwapchain.vkQueuePresentKHR(DeviceManager.getPresentQueue().vkQueue(), presentInfo);
                if (vkResult == -1000001004 || vkResult == 1000001003 || swapChainUpdate) {
                   swapChainUpdate = true;
                   break label80;
                }

                if (vkResult != 0) {
                   throw new RuntimeException("Failed to present rendered frame: %s".formatted(VkResult.decode(vkResult)));
                }
             }

             Vulkan.getStagingBuffers().endFrame(currentFrame);
             currentFrame = (currentFrame + 1) % this.framesNum;
             this.swapChain.setAcquired(false);
             this.consumedAcquireSemaphore = false;
          } catch (Throwable var10) {
             if (stack != null) {
                try {
                   stack.close();
                } catch (Throwable var9) {
                   var9.addSuppressed(var9);
                }
             }

             throw var10;
          }

          if (stack != null) {
             stack.close();
          }

          return;
       }

        if (stack != null) {
           stack.close();
        }
     }

     public void flushCmds() {
       if (this.recordingCmds) {
          MemoryStack stack = MemoryStack.stackPush();

          try {
             this.endRenderPass(this.currentCmdBuffer);
             VK10.vkEndCommandBuffer(this.currentCmdBuffer);
             VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
             submitInfo.sType(4);
             int waitSemaphoreCount = Synchronization.INSTANCE.getWaitSemaphoreCount();
             int totalWaitSemaphores = waitSemaphoreCount;
             if (this.swapChain.isAcquired()) {
                totalWaitSemaphores++;
             }

             LongBuffer waitSemaphores = stack.mallocLong(totalWaitSemaphores);
             IntBuffer waitDstStageMask = stack.mallocInt(totalWaitSemaphores);
             Synchronization.INSTANCE.getWaitSemaphores(waitSemaphores);

             for (int i = 0; i < waitSemaphoreCount; i++) {
                waitDstStageMask.put(i, 1);
             }

             if (this.swapChain.isAcquired()) {
                waitSemaphores.put(totalWaitSemaphores - 1, this.imageAvailableSemaphores[currentFrame]);
                waitDstStageMask.put(totalWaitSemaphores - 1, 1024);
             }

             waitSemaphores.position(0);
             waitSemaphores.limit(totalWaitSemaphores);
             submitInfo.pWaitSemaphores(waitSemaphores);
             submitInfo.waitSemaphoreCount(waitSemaphores.limit());
             submitInfo.pWaitDstStageMask(waitDstStageMask);
             submitInfo.pCommandBuffers(stack.pointers(this.currentCmdBuffer));
             this.submitUploads();
             this.waitFences();
             VK10.vkResetFences(device, this.inFlightFences[currentFrame]);
             int vkResult;
             if ((vkResult = VK10.vkQueueSubmit(DeviceManager.getGraphicsQueue().vkQueue(), submitInfo, this.inFlightFences[currentFrame])) != 0) {
                VK10.vkResetFences(device, this.inFlightFences[currentFrame]);
                throw new RuntimeException("Failed to submit draw command buffer: %s".formatted(VkResult.decode(vkResult)));
             }

             VK10.vkWaitForFences(device, this.inFlightFences[currentFrame], true, -1L);
             if (this.swapChain.isAcquired()) {
                this.consumedAcquireSemaphore = true;
             }
             this.beginMainRenderPass(stack);
          } catch (Throwable var9) {
             if (stack != null) {
                try {
                   stack.close();
                } catch (Throwable var8) {
                   var9.addSuppressed(var8);
                }
             }

             throw var9;
          }

          if (stack != null) {
             stack.close();
          }
       }
    }

   public void submitUploads() {
      CommandPool.CommandBuffer transferCb = this.transferCbs.get(currentFrame);
      if (transferCb.isRecording()) {
         TransferQueue transferQueue = DeviceManager.getTransferQueue();
         MemoryStack stack = MemoryStack.stackPush();

         try {
            transferCb.submitCommands(stack, transferQueue.vkQueue(), true);
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

         Synchronization.INSTANCE.addCommandBuffer(transferCb, true);
         this.transferCbs.set(currentFrame, transferQueue.getCommandPool().getCommandBuffer());
      }

      ImageUploadHelper.INSTANCE.submitCommands();
   }

   public void endRenderPass() {
      this.endRenderPass(this.currentCmdBuffer);
   }

   public void endRenderPass(VkCommandBuffer commandBuffer) {
      if (this.recordingCmds && this.boundFramebuffer != null) {
         this.boundRenderPass.endRenderPass(commandBuffer);
         this.boundRenderPass = null;
         this.boundFramebuffer = null;
         VkGlFramebuffer.resetBoundFramebuffer();
      }
   }

   public boolean beginRenderPass(RenderPass renderPass, Framebuffer framebuffer) {
      if (!this.recordingCmds) {
         this.beginFrame();
         this.recordingCmds = true;
      }

      if (this.boundFramebuffer != framebuffer) {
         this.endRenderPass(this.currentCmdBuffer);
         MemoryStack stack = MemoryStack.stackPush();

         try {
            framebuffer.beginRenderPass(this.currentCmdBuffer, renderPass, stack);
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

         this.boundFramebuffer = framebuffer;
         this.boundRenderPass = renderPass;
         setViewportState(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
         setScissor(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
      }

      return true;
   }

   public void addUsedPipeline(Pipeline pipeline) {
      this.usedPipelines.add(pipeline);
   }

   public void removeUsedPipeline(Pipeline pipeline) {
      this.usedPipelines.remove(pipeline);
   }

   private void waitFences() {
      Synchronization.INSTANCE.waitFences();
      Vulkan.getStagingBuffer().reset();
   }

   private void resetDescriptors() {
      for (Pipeline pipeline : this.usedPipelines) {
         pipeline.resetDescriptorPool(currentFrame);
      }

      this.usedPipelines.clear();
      this.boundPipeline = null;
      this.boundPipelineHandle = 0L;
   }

   void waitForSwapChain() {
      VK10.vkResetFences(device, this.inFlightFences[currentFrame]);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkSubmitInfo info = VkSubmitInfo.calloc(stack)
            .sType$Default()
            .pWaitSemaphores(stack.longs(this.imageAvailableSemaphores[currentFrame]))
            .pWaitDstStageMask(stack.ints(65536));
         VK10.vkQueueSubmit(DeviceManager.getGraphicsQueue().vkQueue(), info, this.inFlightFences[currentFrame]);
         VK10.vkWaitForFences(device, this.inFlightFences[currentFrame], true, -1L);
      } catch (Throwable var5) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }
         }

         throw var5;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void recreateSwapChain() {
      this.submitUploads();
      this.waitFences();
      Vulkan.waitIdle();
      this.mainCommandBuffers.forEach(commandBuffer -> VK10.vkResetCommandBuffer(commandBuffer, 0));
      this.recordingCmds = false;
      this.swapChain.recreate();
      this.destroySyncObjects();
      int newFramesNum = Initializer.CONFIG.frameQueueSize;
      if (this.framesNum != newFramesNum) {
         UploadManager.INSTANCE.submitUploads();
         this.framesNum = newFramesNum;
         MemoryManager.getInstance().freeAllBuffers();
         MemoryManager.createInstance(newFramesNum);
         Vulkan.createStagingBuffers();
         this.allocateCommandBuffers();
         Pipeline.recreateDescriptorSets(this.framesNum);
         this.drawer.createResources(this.framesNum);
      }

      this.createSyncObjects();
      this.mainPass.onResize();
      this.onResizeCallbacks.forEach(Runnable::run);
      ((WindowAccessor)(Object)Minecraft.getInstance().getWindow()).getEventHandler().resizeGui();
      currentFrame = 0;
   }

   public void cleanUpResources() {
      WorldRenderer.getInstance().cleanUp();
      this.destroySyncObjects();
      this.drawer.cleanUpResources();
      this.mainPass.cleanUp();
      this.swapChain.cleanUp();
      PipelineManager.destroyPipelines();
      VTextureSelector.getWhiteTexture().free();
   }

   private void destroySyncObjects() {
      for (int i = 0; i < this.framesNum; i++) {
         VK10.vkDestroyFence(device, this.inFlightFences[i], null);
         VK10.vkDestroySemaphore(device, this.imageAvailableSemaphores[i], null);
      }

      for (int i = 0; i < this.swapChain.getImagesNum(); i++) {
         VK10.vkDestroySemaphore(device, this.renderFinishedSemaphores[i], null);
      }
   }

   public void addOnResizeCallback(Runnable runnable) {
      this.onResizeCallbacks.add(runnable);
   }

   public void bindGraphicsPipeline(GraphicsPipeline pipeline) {
      VkCommandBuffer commandBuffer = this.currentCmdBuffer;
      PipelineState currentState = PipelineState.getCurrentPipelineState(this.boundRenderPass);
      long handle = pipeline.getHandle(currentState);
      if (this.boundPipelineHandle != handle) {
         VK10.vkCmdBindPipeline(commandBuffer, 0, handle);
         this.boundPipelineHandle = handle;
         this.boundPipeline = pipeline;
         this.addUsedPipeline(pipeline);
      }
   }

   public void uploadAndBindUBOs(Pipeline pipeline) {
      VkCommandBuffer commandBuffer = this.currentCmdBuffer;
      pipeline.bindDescriptorSets(commandBuffer, currentFrame);
   }

   public void pushConstants(Pipeline pipeline) {
      VkCommandBuffer commandBuffer = this.currentCmdBuffer;
      PushConstants pushConstants = pipeline.getPushConstants();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer buffer = stack.malloc(pushConstants.getSize());
         long ptr = MemoryUtil.memAddress0(buffer);
         pushConstants.update(ptr);
         VK10.nvkCmdPushConstants(commandBuffer, pipeline.getLayout(), 1, 0, pushConstants.getSize(), ptr);
      } catch (Throwable var9) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public Pipeline getBoundPipeline() {
      return this.boundPipeline;
   }

   public void setBoundFramebuffer(Framebuffer framebuffer) {
      this.boundFramebuffer = framebuffer;
   }

   public Framebuffer getBoundFramebuffer() {
      return this.boundFramebuffer;
   }

   public void setBoundRenderPass(RenderPass boundRenderPass) {
      this.boundRenderPass = boundRenderPass;
   }

   public RenderPass getBoundRenderPass() {
      return this.boundRenderPass;
   }

   public void setMainPass(MainPass mainPass) {
      this.mainPass = mainPass;
   }

   public MainPass getMainPass() {
      return this.mainPass;
   }

   public SwapChain getSwapChain() {
      return this.swapChain;
   }

   public CommandPool.CommandBuffer getTransferCb() {
      return this.transferCbs.get(currentFrame);
   }

   private static void resetDynamicState(VkCommandBuffer commandBuffer) {
      VK10.vkCmdSetDepthBias(commandBuffer, 0.0F, 0.0F, 0.0F);
      VK10.vkCmdSetLineWidth(commandBuffer, 1.0F);
   }

   public static void setDepthBias(float constant, float slope) {
      VkCommandBuffer commandBuffer = INSTANCE.currentCmdBuffer;
      VK10.vkCmdSetDepthBias(commandBuffer, constant, 0.0F, slope);
   }

   public static void clearAttachments(int attachments) {
      clearAttachments(INSTANCE.currentCmdBuffer, attachments);
   }

   public static void clearAttachments(VkCommandBuffer commandBuffer, int attachments) {
      Framebuffer framebuffer = getInstance().boundFramebuffer;
      if (framebuffer != null) {
         clearAttachments(commandBuffer, attachments, framebuffer.getWidth(), framebuffer.getHeight());
      }
   }

   public static void clearAttachments(int attachments, int width, int height) {
      clearAttachments(INSTANCE.currentCmdBuffer, attachments, width, height);
   }

   public static void clearAttachments(int attachments, int x, int y, int width, int height) {
      clearAttachments(INSTANCE.currentCmdBuffer, attachments, x, y, width, height);
   }

   public static void clearAttachments(VkCommandBuffer commandBuffer, int attachments, int width, int height) {
      clearAttachments(commandBuffer, attachments, 0, 0, width, height);
   }

   public static void clearAttachments(VkCommandBuffer commandBuffer, int attachments, int x, int y, int width, int height) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkClearValue colorValue = VkClearValue.calloc(stack);
         colorValue.color().float32(VRenderSystem.clearColor);
         VkClearValue depthValue = VkClearValue.calloc(stack);
         depthValue.depthStencil().set(VRenderSystem.clearDepthValue, 0);
         int attachmentsCount = attachments == 16640 ? 2 : 1;
         Buffer pAttachments = VkClearAttachment.malloc(attachmentsCount, stack);
         switch (attachments) {
            case 256: {
               VkClearAttachment clearDepth = (VkClearAttachment)pAttachments.get(0);
               clearDepth.aspectMask(2);
               clearDepth.colorAttachment(0);
               clearDepth.clearValue(depthValue);
               break;
            }
            case 16384: {
               VkClearAttachment clearColor = (VkClearAttachment)pAttachments.get(0);
               clearColor.aspectMask(1);
               clearColor.colorAttachment(0);
               clearColor.clearValue(colorValue);
               break;
            }
            case 16640: {
               VkClearAttachment clearColor = (VkClearAttachment)pAttachments.get(0);
               clearColor.aspectMask(1);
               clearColor.colorAttachment(0);
               clearColor.clearValue(colorValue);
               VkClearAttachment clearDepth = (VkClearAttachment)pAttachments.get(1);
               clearDepth.aspectMask(2);
               clearDepth.colorAttachment(0);
               clearDepth.clearValue(depthValue);
               break;
            }
            default:
               throw new RuntimeException("unexpected value");
         }

         VkRect2D renderArea = VkRect2D.malloc(stack);
         renderArea.offset().set(x, y);
         renderArea.extent().set(width, height);
         org.lwjgl.vulkan.VkClearRect.Buffer pRect = VkClearRect.malloc(1, stack);
         pRect.rect(renderArea);
         pRect.baseArrayLayer(0);
         pRect.layerCount(1);
         VK10.vkCmdClearAttachments(commandBuffer, pAttachments, pRect);
      } catch (Throwable var14) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }
         }

         throw var14;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static void setInvertedViewport(int x, int y, int width, int height) {
      setViewportState(x, y + height, width, -height);
   }

   public static void resetViewport() {
      Framebuffer framebuffer = INSTANCE.getMainPass().getMainFramebuffer();
      if (framebuffer != null) {
         int width = framebuffer.getWidth();
         int height = framebuffer.getHeight();
         if (width > 0 && height > 0) {
            setViewportState(0, 0, width, height);
         }
      }
   }

   public static void setViewportState(int x, int y, int width, int height) {
      GlStateManager._viewport(x, y, width, height);
   }

   public static void setViewport(int x, int y, int width, int height) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         setViewport(x, y, width, height, stack);
      } catch (Throwable var8) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static void setViewport(int x, int y, int width, int height, MemoryStack stack) {
      if (INSTANCE.recordingCmds) {
         org.lwjgl.vulkan.VkViewport.Buffer viewport = VkViewport.malloc(1, stack);
         viewport.x(x);
         viewport.y(height + y);
         viewport.width(width);
         viewport.height(-height);
         viewport.minDepth(0.0F);
         viewport.maxDepth(1.0F);
         VK10.vkCmdSetViewport(INSTANCE.currentCmdBuffer, 0, viewport);
      }
   }

   public static void setScissor(int x, int y, int width, int height) {
      if (INSTANCE.recordingCmds && INSTANCE.boundFramebuffer != null) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            Framebuffer framebuffer = INSTANCE.boundFramebuffer;
            int framebufferHeight = framebuffer.getHeight();
            x = Math.max(0, x);
            width = Math.min(width, framebuffer.getWidth());
            org.lwjgl.vulkan.VkRect2D.Buffer scissor = VkRect2D.malloc(1, stack);
            scissor.offset().set(x, framebufferHeight - (y + height));
            scissor.extent().set(width, height);
            VK10.vkCmdSetScissor(INSTANCE.currentCmdBuffer, 0, scissor);
         } catch (Throwable var9) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (stack != null) {
            stack.close();
         }
      }
   }

   public static void resetScissor() {
      if (INSTANCE.boundFramebuffer != null) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            org.lwjgl.vulkan.VkRect2D.Buffer scissor = INSTANCE.boundFramebuffer.scissor(stack);
            VK10.vkCmdSetScissor(INSTANCE.currentCmdBuffer, 0, scissor);
         } catch (Throwable var4) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }
            }

            throw var4;
         }

         if (stack != null) {
            stack.close();
         }
      }
   }

   public static void pushDebugSection(String s) {
   }

   public static void popDebugSection() {
   }

   public static void popPushDebugSection(String s) {
      popDebugSection();
      pushDebugSection(s);
   }

   public static int getFramesNum() {
      return INSTANCE.framesNum;
   }

   public static VkCommandBuffer getCommandBuffer() {
      return INSTANCE.currentCmdBuffer;
   }

   public static boolean isRecording() {
      return INSTANCE.recordingCmds;
   }

   public static void scheduleSwapChainUpdate() {
      swapChainUpdate = true;
   }
}
