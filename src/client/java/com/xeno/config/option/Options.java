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
package com.xeno.config.option;

import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import com.xeno.Initializer;
import com.xeno.config.Config;
import com.xeno.config.Platform;
import com.xeno.config.gui.OptionBlock;
import com.xeno.config.video.VideoModeManager;
import com.xeno.config.video.VideoModeSet;
import com.xeno.config.video.WindowMode;
import com.xeno.render.chunk.WorldRenderer;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.device.DeviceManager;

public abstract class Options {
   public static boolean fullscreenDirty = false;
   private static final Config config = Initializer.CONFIG;
   private static final Minecraft minecraft = Minecraft.getInstance();
   private static final Window window = minecraft.getWindow();
   private static final net.minecraft.client.Options mcOptions = minecraft.options;

   public static List<OptionPage> getOptionPages() {
      List<OptionPage> optionPages = new ArrayList<>();
      OptionPage page = new OptionPage(Component.translatable("Xeno.options.pages.video").getString(), getVideoOpts());
      optionPages.add(page);
      page = new OptionPage(Component.translatable("Xeno.options.pages.graphics").getString(), getGraphicsOpts());
      optionPages.add(page);
      page = new OptionPage(Component.translatable("Xeno.options.pages.optimizations").getString(), getOptimizationOpts());
      optionPages.add(page);
      page = new OptionPage(Component.translatable("Xeno.options.pages.other").getString(), getOtherOpts());
      optionPages.add(page);
      return optionPages;
   }

   public static OptionBlock[] getVideoOpts() {
      VideoModeManager.checkConfigVideoMode(config);
      VideoModeManager.selectBestMonitor(window);
      VideoModeSet[] resolutions = VideoModeManager.getVideoResolutions();
      VideoModeSet.VideoMode videoMode = config.videoMode;
      VideoModeSet videoModeSet = VideoModeManager.getVideoModeSet(videoMode);
      if (videoModeSet == null) {
         videoModeSet = resolutions[resolutions.length - 1];
         videoMode = videoModeSet.getVideoMode();
      }

      VideoModeManager.selectedVideoMode = videoMode;
      List<Integer> refreshRates = videoModeSet.getRefreshRates();
      Option<WindowMode> windowModeOption = new CyclingOption<>(Component.translatable("Xeno.options.windowMode"), WindowMode.values(), value -> {
         boolean exclusiveFullscreen = value == WindowMode.EXCLUSIVE_FULLSCREEN;
         mcOptions.fullscreen().set(exclusiveFullscreen);
         config.windowMode = value.mode;
         fullscreenDirty = true;
      }, () -> WindowMode.fromValue(config.windowMode)).setTranslator(value -> Component.translatable(WindowMode.getComponentName(value)));
      CyclingOption<Integer> refreshRateOption = (CyclingOption<Integer>)new CyclingOption<>(
            Component.translatable("Xeno.options.refreshRate"), refreshRates.toArray(new Integer[0]), value -> {
               VideoModeManager.selectedVideoMode.refreshRate = value;
               VideoModeManager.applySelectedVideoMode();
               if (mcOptions.fullscreen().get()) {
                  fullscreenDirty = true;
               }
            }, () -> VideoModeManager.selectedVideoMode.refreshRate
         )
         .setTranslator(refreshRate -> Component.nullToEmpty(refreshRate.toString()))
         .setActivationFn(() -> windowModeOption.getNewValue() == WindowMode.EXCLUSIVE_FULLSCREEN);
      Option<VideoModeSet> resolutionOption = new CyclingOption<>(Component.translatable("options.fullscreen.resolution"), resolutions, value -> {
            VideoModeManager.selectedVideoMode = value.getVideoMode(refreshRateOption.getNewValue());
            VideoModeManager.applySelectedVideoMode();
            if (mcOptions.fullscreen().get()) {
               fullscreenDirty = true;
            }
         }, () -> {
            VideoModeSet.VideoMode selectedVideoMode = VideoModeManager.selectedVideoMode;
            VideoModeSet selectedVideoModeSet = VideoModeManager.getVideoModeSet(selectedVideoMode);
            return selectedVideoModeSet != null ? selectedVideoModeSet : VideoModeSet.getDummy();
         })
         .setTranslator(resolution -> Component.nullToEmpty(resolution.toString()))
         .setActivationFn(() -> windowModeOption.getNewValue() == WindowMode.EXCLUSIVE_FULLSCREEN);
      resolutionOption.setOnChange(() -> {
         VideoModeSet newSet = resolutionOption.getNewValue();
         Integer[] rates = newSet.getRefreshRates().toArray(new Integer[0]);
         refreshRateOption.setValues(rates);
         refreshRateOption.setNewValue(rates[rates.length - 1]);
      });
      windowModeOption.setOnChange(() -> {
         resolutionOption.updateActiveState();
         refreshRateOption.updateActiveState();
      });
      return new OptionBlock[]{
         new OptionBlock(
            "",
            new Option[]{
               windowModeOption,
               resolutionOption,
               refreshRateOption,
               new RangeOption(
                  Component.translatable("options.framerateLimit"),
                  10,
                  260,
                  10,
                  value -> Component.nullToEmpty(value == 260 ? Component.translatable("options.framerateLimit.max").getString() : String.valueOf(value)),
                  value -> {
                     mcOptions.framerateLimit().set(value);
                     minecraft.getFramerateLimitTracker().setFramerateLimit(value);
                  },
                  () -> mcOptions.framerateLimit().get()
               ),
               new SwitchOption(Component.translatable("options.vsync"), value -> {
                  mcOptions.enableVsync().set(value);
                  window.updateVsync(value);
               }, () -> mcOptions.enableVsync().get()),
               new CyclingOption<>(
                     Component.translatable("options.inactivityFpsLimit"),
                     InactivityFpsLimit.values(),
                     value -> mcOptions.inactivityFpsLimit().set(value),
                     () -> mcOptions.inactivityFpsLimit().get()
                  )
                  .setTranslator(InactivityFpsLimit::caption)
            }
         ),
         new OptionBlock(
            "",
            new Option[]{
               new RangeOption(
                  Component.translatable("options.guiScale"),
                  0,
                  window.calculateScale(0, minecraft.isEnforceUnicode()),
                  1,
                  value -> Component.translatable(value == 0 ? "options.guiScale.auto" : String.valueOf(value)),
                  value -> {
                     mcOptions.guiScale().set(value);
                     minecraft.resizeGui();
                  },
                  () -> mcOptions.guiScale().get()
               ),
               new RangeOption(Component.translatable("options.gamma"), 0, 100, 1, value -> {
                  return Component.translatable(switch (value) {
                     case 0 -> "options.gamma.min";
                     case 50 -> "options.gamma.default";
                     case 100 -> "options.gamma.max";
                     default -> String.valueOf(value);
                  });
               }, value -> mcOptions.gamma().set(value.intValue() * 0.01), () -> (int)(mcOptions.gamma().get() * 100.0))
            }
         ),
         new OptionBlock(
            "",
            new Option[]{
               new CyclingOption<>(
                     Component.translatable("options.attackIndicator"),
                     AttackIndicatorStatus.values(),
                     value -> mcOptions.attackIndicator().set(value),
                     () -> mcOptions.attackIndicator().get()
                  )
                  .setTranslator(AttackIndicatorStatus::caption),
               new SwitchOption(
                  Component.translatable("options.autosaveIndicator"),
                  value -> mcOptions.showAutosaveIndicator().set(value),
                  () -> mcOptions.showAutosaveIndicator().get()
               )
            }
         )
      };
   }

   public static OptionBlock[] getGraphicsOpts() {
      Option<TextureFilteringMethod> texFilteringOption = new CyclingOption<>(
            Component.translatable("options.textureFiltering"),
            TextureFilteringMethod.values(),
            value -> {
               TextureFilteringMethod oldValue = mcOptions.textureFiltering().get();
               if (oldValue == TextureFilteringMethod.ANISOTROPIC && value != TextureFilteringMethod.ANISOTROPIC
                  || value == TextureFilteringMethod.ANISOTROPIC && oldValue != TextureFilteringMethod.ANISOTROPIC) {
                  minecraft.delayTextureReload();
                  WorldRenderer.getInstance().resetSampler();
               }

               mcOptions.textureFiltering().set(value);
            },
            () -> mcOptions.textureFiltering().get()
         )
         .setTranslator(TextureFilteringMethod::caption)
         .setTooltip(value -> {
            return switch (value) {
               case NONE -> Component.translatable("options.textureFiltering.none.tooltip");
               case RGSS -> Component.translatable("options.textureFiltering.rgss.tooltip");
               case ANISOTROPIC -> Component.translatable("options.textureFiltering.anisotropic.tooltip");
            };
         })
         .setImpact(PerformanceImpact.MEDIUM);
      Option<Integer> maxAnisotropyOption = new RangeOption(Component.translatable("options.maxAnisotropy"), 1, 3, 1, value -> {
            Integer oldValue = mcOptions.maxAnisotropyBit().get();
            if (mcOptions.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC && !oldValue.equals(value)) {
               minecraft.delayTextureReload();
               WorldRenderer.getInstance().resetSampler();
            }

            mcOptions.maxAnisotropyBit().set(value);
         }, () -> mcOptions.maxAnisotropyBit().get())
         .setTranslator(value -> Component.translatable("options.multiplier", Integer.toString(1 << value)))
         .setTooltip(v -> Component.translatable("options.maxAnisotropy.tooltip"));
      maxAnisotropyOption.setActivationFn(() -> texFilteringOption.getNewValue() == TextureFilteringMethod.ANISOTROPIC);
      texFilteringOption.setOnChange(maxAnisotropyOption::updateActiveState);
      return new OptionBlock[]{
         new OptionBlock(
            "",
            new Option[]{
               new RangeOption(
                     Component.translatable("options.renderDistance"),
                     2,
                     32,
                     1,
                     value -> mcOptions.renderDistance().set(value),
                     () -> mcOptions.renderDistance().get()
                  )
                  .setTooltip(v -> Component.literal("Chunk render distance"))
                  .setImpact(PerformanceImpact.HIGH),
               new RangeOption(
                  Component.translatable("options.simulationDistance"),
                  5,
                  32,
                  1,
                  value -> mcOptions.simulationDistance().set(value),
                  () -> mcOptions.simulationDistance().get()
               ),
               new CyclingOption<>(
                     Component.translatable("options.prioritizeChunkUpdates"),
                     PrioritizeChunkUpdates.values(),
                     value -> mcOptions.prioritizeChunkUpdates().set(value),
                     () -> mcOptions.prioritizeChunkUpdates().get()
                  )
                  .setTranslator(PrioritizeChunkUpdates::caption)
            }
         ),
         new OptionBlock(
            "",
            new Option[]{
               new CyclingOption<>(
                     Component.translatable("options.graphics.preset"),
                     new GraphicsPreset[]{GraphicsPreset.FAST, GraphicsPreset.FANCY, GraphicsPreset.CUSTOM},
                     value -> mcOptions.graphicsPreset().set(value),
                     () -> mcOptions.graphicsPreset().get()
                  )
                  .setTranslator(g -> Component.translatable(g.getKey())),
               texFilteringOption,
               maxAnisotropyOption,
               new CyclingOption<>(
                     Component.translatable("options.particles"),
                     new ParticleStatus[]{ParticleStatus.MINIMAL, ParticleStatus.DECREASED, ParticleStatus.ALL},
                     value -> mcOptions.particles().set(value),
                     () -> mcOptions.particles().get()
                  )
                  .setImpact(PerformanceImpact.MEDIUM)
                  .setTranslator(ParticleStatus::caption),
               new CyclingOption<>(
                     Component.translatable("options.renderClouds"),
                     CloudStatus.values(),
                     value -> mcOptions.cloudStatus().set(value),
                     () -> mcOptions.cloudStatus().get()
                  )
                  .setTranslator(CloudStatus::caption),
               new RangeOption(
                  Component.translatable("options.renderCloudsDistance"),
                  2,
                  128,
                  1,
                  value -> mcOptions.cloudRange().set(value),
                  () -> mcOptions.cloudRange().get()
               ),
               new SwitchOption(
                     Component.translatable("options.cutoutLeaves"), value -> mcOptions.cutoutLeaves().set(value), () -> mcOptions.cutoutLeaves().get()
                  )
                  .setTooltip(value -> Component.translatable("options.cutoutLeaves.tooltip")),
               new RangeOption(
                     Component.translatable("options.chunkFade"),
                     0,
                     40,
                     1,
                     value -> mcOptions.chunkSectionFadeInTime().set(value.intValue() / 20.0),
                     () -> (int)(mcOptions.chunkSectionFadeInTime().get() * 20.0)
                  )
                  .setTranslator(value -> Component.literal(String.valueOf(value.intValue() / 20.0F)))
                  .setTooltip(v -> Component.translatable("options.chunkFade.tooltip")),
               new CyclingOption<>(Component.translatable("options.ao"), new Integer[]{0, 1, 2}, value -> {
                     mcOptions.ambientOcclusion().set(value > 0);
                     config.ambientOcclusion = value;
                     minecraft.levelRenderer.allChanged();
                  }, () -> config.ambientOcclusion)
                  .setTranslator(value -> {
                     return Component.translatable(switch (value) {
                        case 0 -> "options.off";
                        case 1 -> "options.on";
                        case 2 -> "Xeno.options.ao.subBlock";
                        default -> "Xeno.options.unknown";
                     });
                  })
                  .setTooltip(value -> value == 2 ? Component.translatable("Xeno.options.ao.subBlock.tooltip") : Component.empty())
                  .setImpact(PerformanceImpact.LOW),
               new RangeOption(
                  Component.translatable("options.biomeBlendRadius"),
                  0,
                  7,
                  1,
                  value -> Component.nullToEmpty("%d x %d".formatted(value * 2 + 1, value * 2 + 1)),
                  value -> {
                     mcOptions.biomeBlendRadius().set(value);
                     minecraft.levelRenderer.allChanged();
                  },
                  () -> mcOptions.biomeBlendRadius().get()
               )
            }
         ),
         new OptionBlock(
            "",
            new Option[]{
               new SwitchOption(
                     Component.translatable("options.entityShadows"), value -> mcOptions.entityShadows().set(value), () -> mcOptions.entityShadows().get()
                  )
                  .setImpact(PerformanceImpact.LOW),
               new RangeOption(
                     Component.translatable("options.entityDistanceScaling"),
                     2,
                     20,
                     1,
                     value -> mcOptions.entityDistanceScaling().set(value.intValue() / 4.0),
                     () -> (int)(mcOptions.entityDistanceScaling().get() * 4.0)
                  )
                  .setImpact(PerformanceImpact.HIGH)
                  .setTranslator(value -> Component.literal(String.valueOf(value.intValue() / 4.0))),
               new CyclingOption<>(Component.translatable("options.mipmapLevels"), new Integer[]{0, 1, 2, 3, 4}, value -> {
                  mcOptions.mipmapLevels().set(value);
                  minecraft.updateMaxMipLevel(value);
                  minecraft.delayTextureReload();
               }, () -> mcOptions.mipmapLevels().get()).setTranslator(v -> Component.literal(String.valueOf(v))).setImpact(PerformanceImpact.LOW),
               new RangeOption(
                     Component.translatable("options.weatherRadius"),
                     3,
                     10,
                     1,
                     value -> mcOptions.weatherRadius().set(value),
                     () -> mcOptions.weatherRadius().get()
                  )
                  .setTooltip(value -> Component.translatable("options.weatherRadius.tooltip")),
               new SwitchOption(Component.translatable("options.vignette"), value -> mcOptions.vignette().set(value), () -> mcOptions.vignette().get())
                  .setTooltip(value -> Component.translatable("options.vignette.tooltip"))
            }
         )
      };
   }

   public static OptionBlock[] getOptimizationOpts() {
      return new OptionBlock[]{
         new OptionBlock(
            "",
            new Option[]{
               new CyclingOption<>(
                     Component.translatable("Xeno.options.advCulling"),
                     new Integer[]{1, 2, 3, 10},
                     value -> config.advCulling = value,
                     () -> config.advCulling
                  )
                  .setTranslator(v -> {
                     return Component.translatable(switch (v) {
                        case 1 -> "Xeno.options.advCulling.aggressive";
                        case 2 -> "Xeno.options.advCulling.normal";
                        case 3 -> "Xeno.options.advCulling.conservative";
                        default -> "Xeno.options.unknown";
                        case 10 -> "options.off";
                     });
                  })
                  .setTooltip(v -> v <= 3 ? Component.translatable("Xeno.options.advCulling.tooltip") : Component.empty())
                  .setImpact(PerformanceImpact.HIGH),
               new SwitchOption(Component.translatable("Xeno.options.entityCulling"), v -> config.entityCulling = v, () -> config.entityCulling)
                  .setTooltip(v -> Component.translatable("Xeno.options.entityCulling.tooltip"))
                  .setImpact(PerformanceImpact.HIGH),
               new SwitchOption(Component.translatable("Xeno.options.uniqueOpaqueLayer"), v -> {
                     config.uniqueOpaqueLayer = v;
                     TerrainRenderType.updateMapping();
                     minecraft.levelRenderer.allChanged();
                  }, () -> config.uniqueOpaqueLayer)
                  .setTooltip(v -> Component.translatable("Xeno.options.uniqueOpaqueLayer.tooltip"))
                  .setImpact(PerformanceImpact.HIGH),
               new SwitchOption(Component.translatable("Xeno.options.backfaceCulling"), v -> {
                  config.backFaceCulling = v;
                  minecraft.levelRenderer.allChanged();
               }, () -> config.backFaceCulling).setTooltip(v -> Component.translatable("Xeno.options.backfaceCulling.tooltip")).setImpact(
                  PerformanceImpact.HIGH
               ),
               new SwitchOption(Component.translatable("Xeno.options.indirectDraw"), v -> config.indirectDraw = v, () -> config.indirectDraw)
                  .setTooltip(v -> Component.translatable("Xeno.options.indirectDraw.tooltip"))
                  .setImpact(PerformanceImpact.HIGH)
            }
         )
      };
   }

   public static OptionBlock[] getOtherOpts() {
      return new OptionBlock[]{
         new OptionBlock(
            "",
            new Option[]{
               new RangeOption(Component.translatable("Xeno.options.builderThreads"), 0, Runtime.getRuntime().availableProcessors() - 1, 1, value -> {
                     config.builderThreads = value;
                     WorldRenderer.getInstance().getTaskDispatcher().createThreads(value);
                  }, () -> config.builderThreads)
                  .setTranslator(v -> v == 0 ? Component.translatable("Xeno.options.builderThreads.auto") : Component.literal(String.valueOf(v))),
               new RangeOption(Component.translatable("Xeno.options.frameQueue"), 2, 5, 1, value -> {
                  config.frameQueueSize = value;
                  Renderer.scheduleSwapChainUpdate();
               }, () -> config.frameQueueSize).setTooltip(v -> Component.translatable("Xeno.options.frameQueue.tooltip")),
               new SwitchOption(
                  Component.translatable("Xeno.options.textureAnimations"), v -> config.textureAnimations = v, () -> config.textureAnimations
               )
            }
         ),
         new OptionBlock(
            "",
            new Option[]{
               new SwitchOption(Component.translatable("Xeno.options.wayland"), v -> config.useWayland = v, () -> config.useWayland)
                  .setActivationFn(Platform::isLinux)
                  .setTooltip(v -> Component.translatable("Xeno.options.wayland.tooltip")),
               new CyclingOption<>(
                     Component.translatable("Xeno.options.deviceSelector"),
                     IntStream.range(-1, DeviceManager.suitableDevices.size()).boxed().toArray(Integer[]::new),
                     value -> config.device = value,
                     () -> config.device
                  )
                  .setTranslator(
                     v -> Component.translatable(v == -1 ? "Xeno.options.deviceSelector.auto" : DeviceManager.suitableDevices.get(v).deviceName)
                  )
                  .setTooltip(
                     v -> Component.literal(
                        Component.translatable("Xeno.options.deviceSelector.tooltip").getString() + ": " + DeviceManager.device.deviceName
                     )
                  )
            }
         )
      };
   }
}
