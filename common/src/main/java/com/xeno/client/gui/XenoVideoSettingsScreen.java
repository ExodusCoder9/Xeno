/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.gui;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import com.xeno.client.common.config.XenoConfig;
import com.xeno.client.common.render.texture.XenoSpriteTracker;
import com.xeno.client.gui.widget.XenoButton;
import com.xeno.client.gui.widget.XenoScroller;
import com.xeno.client.gui.widget.XenoSlider;
import com.xeno.client.gui.widget.XenoTab;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

public final class XenoVideoSettingsScreen extends Screen {

    public enum PerformanceImpact {
        LOW("xeno.options.impact.low", 0xFF4ADE80),
        MEDIUM("xeno.options.impact.medium", 0xFFFACC15),
        HIGH("xeno.options.impact.high", 0xFF8B5CF6);

        private final Component label;
        private final int color;

        PerformanceImpact(String translationKey, int color) {
            this.label = Component.translatable(translationKey);
            this.color = color;
        }

        public Component getText() {
            return this.label;
        }

        public int getColor() {
            return this.color;
        }
    }

    private record OptionEntry(AbstractWidget widget, Component description, PerformanceImpact impact, Component name, java.util.function.BooleanSupplier activeCondition) {
        OptionEntry(AbstractWidget widget, Component description, PerformanceImpact impact, Component name) {
            this(widget, description, impact, name, null);
        }
    }

    private static final Component TITLE = Component.translatable("xeno.options.title");
    private static final int ROW_HEIGHT = 30;

    private final Screen lastScreen;
    private final Options options;

    private final int oldMipmaps;
    private final int oldAnisotropyBit;
    private final TextureFilteringMethod oldTextureFiltering;

    private final List<XenoTab> tabs = new ArrayList<>();
    private int currentTab = 0;

    private final List<OptionEntry> currentTabOptions = new ArrayList<>();
    private final Map<OptionInstance<?>, Object> pendingChanges = new HashMap<>();

    private Monitor resolutionMonitor;
    private int pendingResolution = -1;
    private int initialResolution = -1;
    private boolean resolutionEdited = false;

    private XenoScroller scroller;
    private float scrollOffset;
    private float targetScrollOffset;
    private long warningBannerTime = 0;
    private OptionEntry lastHoveredOption = null;
    private long hoverStartTime = 0;

    public XenoVideoSettingsScreen(Screen lastScreen, Options options) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.options = options;
        this.oldMipmaps = options.mipmapLevels().get();
        this.oldAnisotropyBit = options.maxAnisotropyBit().get();
        this.oldTextureFiltering = options.textureFiltering().get();
    }

    @Override
    protected void init() {
        this.tabs.clear();
        this.scrollOffset = 0;
        this.targetScrollOffset = 0;

        Window window = this.minecraft.getWindow();
        this.resolutionMonitor = window.findBestMonitor();
        this.initialResolution = queryInitialResolution();
        if (!this.resolutionEdited) {
            this.pendingResolution = this.initialResolution;
        }

        int tabY = 40;
        String[] tabKeys = {
                "xeno.options.tab.display",
                "xeno.options.tab.quality",
                "xeno.options.tab.performance",
                "xeno.options.tab.preferences",
                "xeno.options.tab.advanced"
        };
        for (int i = 0; i < tabKeys.length; i++) {
            final int tabIndex = i;
            XenoTab tab = new XenoTab(0, tabY, 110, 20, Component.translatable(tabKeys[i]), this.currentTab == i, _ -> {
                if (this.currentTab != tabIndex) {
                    this.currentTab = tabIndex;
                    this.scrollOffset = 0;
                    if (this.scroller != null) {
                        this.scroller.setScrollAmount(0);
                    }
                    this.rebuildWidgets();
                }
            }) {
                @Override
                protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                    int x = this.getX();
                    int y = this.getY();
                    int w = this.getWidth();
                    int h = this.getHeight();
                    boolean hovered = this.isHoveredOrFocused();

                    int bgColor;
                    int textColor;

                    if (this.isSelected()) {
                        bgColor = 0x1A8B5CF6;
                        textColor = 0xFFFFFFFF;
                    } else if (hovered) {
                        bgColor = 0x1AFFFFFF;
                        textColor = 0xFFFFFFFF;
                    } else {
                        bgColor = 0x00000000;
                        textColor = 0xFFAAAAAA;
                    }

                    if (bgColor != 0) {
                        graphics.fill(x, y, x + w, y + h, bgColor);
                    }

                    if (this.isSelected()) {
                        graphics.fill(x, y, x + 2, y + h, 0xFF8B5CF6);
                    }

                    graphics.text(
                            Minecraft.getInstance().font,
                            this.getMessage(),
                            x + 10,
                            y + (h - 8) / 2,
                            textColor
                    );
                }
            };
            this.tabs.add(tab);
            this.addRenderableWidget(tab);
            tabY += 24;
        }

        populateOptionsForTab(this.currentTab);

        this.scroller = XenoScroller.vertical(
                this.width - 12, 40,
                this.height - 40 - 35
        );
        this.addRenderableWidget(this.scroller);

        int applyX = this.width - 30 - 120;
        this.addRenderableWidget(new XenoButton(applyX, this.height - 30, 120, 20, Component.translatable("xeno.options.apply"), _ -> this.applyOptions()) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();
                boolean hovered = this.isHoveredOrFocused();

                int bgColor = hovered ? 0x25A78BFA : 0x10A78BFA;
                int borderColor = hovered ? 0xFFA78BFA : 0x60A78BFA;

                graphics.fill(x, y, x + w, y + h, bgColor);
                graphics.outline(x, y, w, h, borderColor);

                int textColor = hovered ? 0xFFFFFFFF : 0xFFE9D5FF;
                graphics.centeredText(
                        Minecraft.getInstance().font,
                        this.getMessage(),
                        x + w / 2,
                        y + (h - 8) / 2,
                        textColor
                );
            }
        });

        updateScrollerRange();
        updateWidgetPositions();
    }

    private int queryInitialResolution() {
        if (this.resolutionMonitor == null) {
            return -1;
        }
        Window window = this.minecraft.getWindow();
        Optional<VideoMode> preferred = window.getPreferredFullscreenVideoMode();
        return preferred.map(this.resolutionMonitor::indexOfMode).orElse(-1);
    }

    @SuppressWarnings("unchecked")
    private <T> T getOptionValue(OptionInstance<T> option) {
        if (this.pendingChanges.containsKey(option)) {
            return (T) this.pendingChanges.get(option);
        }
        return option.get();
    }

    private void markOptionChanged(OptionInstance<?> option, Object value) {
        this.pendingChanges.put(option, value);
        if (option != this.options.graphicsPreset()) {
            setGraphicsPresetToCustom();
        }
    }

    private void setGraphicsPresetToCustom() {
        this.pendingChanges.put(this.options.graphicsPreset(), GraphicsPreset.CUSTOM);
    }

    private void applyPresetToPending(GraphicsPreset preset) {
        if (preset == GraphicsPreset.CUSTOM) return;

        this.pendingChanges.put(this.options.graphicsPreset(), preset);

        if (preset == GraphicsPreset.FAST) {
            this.pendingChanges.put(this.options.biomeBlendRadius(), 1);
            this.pendingChanges.put(this.options.renderDistance(), 8);
            this.pendingChanges.put(this.options.simulationDistance(), 6);
            this.pendingChanges.put(this.options.ambientOcclusion(), false);
            this.pendingChanges.put(this.options.cloudStatus(), CloudStatus.FAST);
            this.pendingChanges.put(this.options.particles(), ParticleStatus.DECREASED);
            this.pendingChanges.put(this.options.mipmapLevels(), 2);
            this.pendingChanges.put(this.options.entityShadows(), false);
            this.pendingChanges.put(this.options.entityDistanceScaling(), 0.75);
            this.pendingChanges.put(this.options.cloudRange(), 32);
            this.pendingChanges.put(this.options.cutoutLeaves(), false);
            this.pendingChanges.put(this.options.improvedTransparency(), false);
            this.pendingChanges.put(this.options.weatherRadius(), 5);
            this.pendingChanges.put(this.options.maxAnisotropyBit(), 1);
            this.pendingChanges.put(this.options.textureFiltering(), TextureFilteringMethod.NONE);
        } else if (preset == GraphicsPreset.FANCY) {
            this.pendingChanges.put(this.options.biomeBlendRadius(), 2);
            this.pendingChanges.put(this.options.renderDistance(), 16);
            this.pendingChanges.put(this.options.simulationDistance(), 12);
            this.pendingChanges.put(this.options.ambientOcclusion(), true);
            this.pendingChanges.put(this.options.cloudStatus(), CloudStatus.FANCY);
            this.pendingChanges.put(this.options.particles(), ParticleStatus.ALL);
            this.pendingChanges.put(this.options.mipmapLevels(), 4);
            this.pendingChanges.put(this.options.entityShadows(), true);
            this.pendingChanges.put(this.options.entityDistanceScaling(), 1.0);
            this.pendingChanges.put(this.options.cloudRange(), 64);
            this.pendingChanges.put(this.options.cutoutLeaves(), true);
            this.pendingChanges.put(this.options.improvedTransparency(), false);
            this.pendingChanges.put(this.options.weatherRadius(), 10);
            this.pendingChanges.put(this.options.maxAnisotropyBit(), 1);
            this.pendingChanges.put(this.options.textureFiltering(), TextureFilteringMethod.RGSS);
        } else if (preset == GraphicsPreset.FABULOUS) {
            this.pendingChanges.put(this.options.biomeBlendRadius(), 2);
            this.pendingChanges.put(this.options.renderDistance(), 32);
            this.pendingChanges.put(this.options.simulationDistance(), 12);
            this.pendingChanges.put(this.options.ambientOcclusion(), true);
            this.pendingChanges.put(this.options.cloudStatus(), CloudStatus.FANCY);
            this.pendingChanges.put(this.options.particles(), ParticleStatus.ALL);
            this.pendingChanges.put(this.options.mipmapLevels(), 4);
            this.pendingChanges.put(this.options.entityShadows(), true);
            this.pendingChanges.put(this.options.entityDistanceScaling(), 1.25);
            this.pendingChanges.put(this.options.cloudRange(), 128);
            this.pendingChanges.put(this.options.cutoutLeaves(), true);
            this.pendingChanges.put(this.options.improvedTransparency(), true);
            this.pendingChanges.put(this.options.weatherRadius(), 10);
            this.pendingChanges.put(this.options.maxAnisotropyBit(), 2);
            this.pendingChanges.put(this.options.textureFiltering(), TextureFilteringMethod.ANISOTROPIC);
        }

        this.rebuildWidgets();
    }

    private void populateOptionsForTab(int tabIndex) {
        this.currentTabOptions.clear();

        if (tabIndex == 0) {
            addCycle(this.options.preferredGraphicsBackend(), "graphics_api", PerformanceImpact.MEDIUM);
            addResolution();
            addDoubleSlider(this.options.gamma(), "brightness", 0.0, 1.0, PerformanceImpact.LOW);
            addCycle(this.options.guiScale(), "gui_scale", PerformanceImpact.LOW);
            addToggle(this.options.fullscreen(), "fullscreen", PerformanceImpact.LOW);
            addToggle(this.options.exclusiveFullscreen(), "exclusive_fullscreen", PerformanceImpact.LOW);
            addToggle(this.options.enableVsync(), "vsync", PerformanceImpact.LOW);
            addSlider(this.options.framerateLimit(), "fps_limit", 10, 260, PerformanceImpact.LOW);

        } else if (tabIndex == 1) {
            addCycle(this.options.graphicsPreset(), "graphics", PerformanceImpact.HIGH);
            addToggle(this.options.ambientOcclusion(), "smooth_lighting", PerformanceImpact.MEDIUM);
            addCycle(this.options.cloudStatus(), "clouds", PerformanceImpact.LOW);
            addSlider(this.options.cloudRange(), "cloud_distance", 2, 128, PerformanceImpact.LOW);
            addCycle(this.options.biomeBlendRadius(), "biome_blend", PerformanceImpact.LOW);
            addCycle(this.options.particles(), "particles", PerformanceImpact.MEDIUM);
            addCycle(this.options.mipmapLevels(), "mipmap", PerformanceImpact.LOW);
            addToggle(this.options.cutoutLeaves(), "cutout_leaves", PerformanceImpact.MEDIUM);
            addToggle(this.options.improvedTransparency(), "improved_transparency", PerformanceImpact.MEDIUM);
            addToggle(this.options.vignette(), "vignette", PerformanceImpact.LOW);
            addToggle(this.options.entityShadows(), "entity_shadows", PerformanceImpact.LOW);
            addSlider(this.options.weatherRadius(), "weather_radius", 2, 10, PerformanceImpact.MEDIUM);

        } else if (tabIndex == 2) {
            addSlider(this.options.renderDistance(), "render_distance", 2, 32, PerformanceImpact.MEDIUM);
            addSlider(this.options.simulationDistance(), "simulation_distance", 5, 32, PerformanceImpact.MEDIUM);
            addDoubleToggle(this.options.chunkSectionFadeInTime(), "chunk_fade_time", PerformanceImpact.LOW);
            addDoubleSlider(this.options.entityDistanceScaling(), "entity_distance", 0.5, 5.0, PerformanceImpact.MEDIUM);
            addCycle(this.options.prioritizeChunkUpdates(), "priority_chunk_updates", PerformanceImpact.MEDIUM);
            addCycle(this.options.inactivityFpsLimit(), "inactivity_fps_limit", PerformanceImpact.LOW);
            addDirectToggle(
                    "animate_only_visible_textures",
                    PerformanceImpact.MEDIUM,
                    XenoSpriteTracker::isAnimationCullingEnabled,
                    XenoSpriteTracker::setAnimationCullingEnabled
            );
            addDirectToggle(
                    "gl_no_error",
                    PerformanceImpact.LOW,
                    XenoConfig::isGlNoErrorContext,
                    val -> {
                        XenoConfig.setGlNoErrorContext(val);
                        this.warningBannerTime = System.currentTimeMillis() + 5000;
                    },
                    () -> getOptionValue(this.options.preferredGraphicsBackend()) != PreferredGraphicsApi.VULKAN
            );

        } else if (tabIndex == 3) {
            addCycle(this.options.attackIndicator(), "attack_indicator", PerformanceImpact.LOW);
            addToggle(this.options.showAutosaveIndicator(), "autosave_indicator", PerformanceImpact.LOW);

        } else if (tabIndex == 4) {
            addCycle(this.options.textureFiltering(), "texture_filtering", PerformanceImpact.LOW);
            addCycle(this.options.maxAnisotropyBit(), "anisotropic_value", PerformanceImpact.LOW);
            addSlider(this.options.menuBackgroundBlurriness(), "menu_background_blurriness", 0, 10, PerformanceImpact.LOW);
        }
    }

    private Component getResolutionText() {
        if (this.resolutionMonitor == null) {
            return Component.translatable("xeno.options.value.unavailable");
        }
        if (this.pendingResolution == -1) {
            return Component.translatable("xeno.options.value.current");
        }
        VideoMode mode = this.resolutionMonitor.mode(this.pendingResolution);
        return Component.literal(mode.getWidth() + " x " + mode.getHeight());
    }

    private void addResolution() {
        if (this.resolutionMonitor == null) {
            return;
        }
        int maxMode = this.resolutionMonitor.modeCount() - 1;
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;

        double sliderValue = maxMode >= 0 ? (double) (this.pendingResolution + 1) / (maxMode + 1) : 0.0;
        Component msg = getResolutionText();

        XenoSlider slider = new XenoSlider(0, y, 80, 20, msg, sliderValue, s -> {
            if (maxMode >= 0) {
                int raw = (int) Math.round(s.getDoubleValue() * (maxMode + 1)) - 1;
                if (raw < -1) {
                    raw = -1;
                }
                if (raw > maxMode) {
                    raw = maxMode;
                }
                this.pendingResolution = raw;
                this.resolutionEdited = true;
                s.setMessage(getResolutionText());
            }
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();

                graphics.fill(x, y + h / 2 - 1, x + w, y + h / 2 + 1, 0x40FFFFFF);

                int handleWidth = 4;
                int handleX = x + (int) (this.getDoubleValue() * (w - handleWidth));
                graphics.fill(handleX, y, handleX + handleWidth, y + h, 0xFF8B5CF6);
            }
        };

        this.addRenderableWidget(slider);
        this.currentTabOptions.add(new OptionEntry(
                slider,
                Component.translatable("xeno.options.resolution.desc"),
                PerformanceImpact.LOW,
                Component.translatable("xeno.options.resolution")
        ));
    }

    private void addSlider(OptionInstance<Integer> option, String key, int min, int max, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        int current = getOptionValue(option);

        boolean isSimDist = option == this.options.simulationDistance();
        boolean isFpsLimit = option == this.options.framerateLimit();
        boolean isWeather = option == this.options.weatherRadius();

        if (isSimDist && current < 5) {
            current = 5;
        }
        if (isFpsLimit && current > 250) {
            current = 260;
        }
        if (isWeather && current < 3) {
            current = 10;
        }

        double sliderValue;
        if (isFpsLimit) {
            if (current >= 260) {
                sliderValue = 1.0;
            } else {
                sliderValue = (double) (current - 10) / 250.0;
            }
        } else {
            sliderValue = max > min ? (double) (current - min) / (max - min) : 0.0;
        }

        Component msg = getSliderValueText(option, current);

        XenoSlider slider = new XenoSlider(0, y, 80, 20, msg, sliderValue, s -> {
            int value;
            if (isFpsLimit) {
                int rawValue = 10 + (int) Math.round(s.getDoubleValue() * 250);
                value = ((rawValue + 5) / 10) * 10;
                if (value > 250) {
                    value = 260;
                }
            } else {
                value = min + (int) Math.round(s.getDoubleValue() * (max - min));
                if (isSimDist && value < 5) {
                    value = 5;
                }
                if (isWeather && value < 3) {
                    value = 3;
                }
            }
            markOptionChanged(option, value);
            s.setMessage(getSliderValueText(option, value));
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();

                graphics.fill(x, y + h / 2 - 1, x + w, y + h / 2 + 1, 0x40FFFFFF);

                int handleWidth = 4;
                int handleX = x + (int) (this.getDoubleValue() * (w - handleWidth));
                graphics.fill(handleX, y, handleX + handleWidth, y + h, 0xFF8B5CF6);
            }
        };

        this.addRenderableWidget(slider);
        this.currentTabOptions.add(new OptionEntry(slider, Component.translatable("xeno.options." + key + ".desc"), impact, Component.translatable("xeno.options." + key)));
    }

    private void addDoubleSlider(OptionInstance<Double> option, String key, double min, double max, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        boolean isGamma = option == this.options.gamma();
        double current = getOptionValue(option);

        double sliderValue = (current - min) / (max - min);
        Component msg = isGamma ? getOptionValueText(option, current) : Component.literal(Math.round(current * 100) + "%");

        XenoSlider slider = new XenoSlider(0, y, 80, 20, msg, sliderValue, s -> {
            double rawVal = min + s.getDoubleValue() * (max - min);
            double value = isGamma ? Math.round(rawVal * 100.0) / 100.0 : Math.round(rawVal * 4.0) / 4.0;
            value = Math.clamp(value, min, max);
            markOptionChanged(option, value);
            s.setMessage(isGamma ? getOptionValueText(option, value) : Component.literal(Math.round(value * 100) + "%"));
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();

                graphics.fill(x, y + h / 2 - 1, x + w, y + h / 2 + 1, 0x40FFFFFF);

                int handleWidth = 4;
                int handleX = x + (int) (this.getDoubleValue() * (w - handleWidth));
                graphics.fill(handleX, y, handleX + handleWidth, y + h, 0xFF8B5CF6);
            }
        };

        this.addRenderableWidget(slider);
        this.currentTabOptions.add(new OptionEntry(slider, Component.translatable("xeno.options." + key + ".desc"), impact, Component.translatable("xeno.options." + key)));
    }

    private void addDoubleToggle(OptionInstance<Double> option, String key, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        double current = getOptionValue(option);
        Component msg = getOptionValueText(option, current);

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            double val = getOptionValue(option);
            double nextVal = val > 0.0 ? 0.0 : 0.75;
            markOptionChanged(option, nextVal);
            btn.setMessage(getOptionValueText(option, nextVal));
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();
                boolean hovered = this.isHoveredOrFocused();

                int textColor = hovered ? 0xFFFFFFFF : 0xFF8B5CF6;
                graphics.text(
                        Minecraft.getInstance().font,
                        this.getMessage(),
                        x + w - Minecraft.getInstance().font.width(this.getMessage()) - 6,
                        y + (h - 8) / 2,
                        textColor
                );
            }
        };

        this.addRenderableWidget(button);
        this.currentTabOptions.add(new OptionEntry(button, Component.translatable("xeno.options." + key + ".desc"), impact, Component.translatable("xeno.options." + key)));
    }

    private void addDirectToggle(
            String key,
            PerformanceImpact impact,
            java.util.function.BooleanSupplier getter,
            java.util.function.Consumer<Boolean> setter
    ) {
        addDirectToggle(key, impact, getter, setter, null);
    }

    private void addDirectToggle(
            String key,
            PerformanceImpact impact,
            java.util.function.BooleanSupplier getter,
            java.util.function.Consumer<Boolean> setter,
            java.util.function.BooleanSupplier activeCondition
    ) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        boolean current = getter.getAsBoolean();
        Component msg = Component.translatable(current ? "xeno.options.value.on" : "xeno.options.value.off");

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            boolean next = !getter.getAsBoolean();
            setter.accept(next);
            btn.setMessage(Component.translatable(next ? "xeno.options.value.on" : "xeno.options.value.off"));
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();
                boolean hovered = this.isHoveredOrFocused();

                int textColor = !this.active ? 0xFF6B7280 : (hovered ? 0xFFFFFFFF : 0xFF8B5CF6);
                graphics.text(
                        Minecraft.getInstance().font,
                        this.getMessage(),
                        x + w - Minecraft.getInstance().font.width(this.getMessage()) - 6,
                        y + (h - 8) / 2,
                        textColor
                );
            }
        };

        this.addRenderableWidget(button);
        this.currentTabOptions.add(new OptionEntry(button, Component.translatable("xeno.options." + key + ".desc"), impact, Component.translatable("xeno.options." + key), activeCondition));
    }

    private void addToggle(OptionInstance<Boolean> option, String key, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        boolean current = getOptionValue(option);
        Component msg = getOptionValueText(option, current);

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            boolean val = getOptionValue(option);
            markOptionChanged(option, !val);
            btn.setMessage(getOptionValueText(option, !val));
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();
                boolean hovered = this.isHoveredOrFocused();

                int textColor = hovered ? 0xFFFFFFFF : 0xFF8B5CF6;
                graphics.text(
                        Minecraft.getInstance().font,
                        this.getMessage(),
                        x + w - Minecraft.getInstance().font.width(this.getMessage()) - 6,
                        y + (h - 8) / 2,
                        textColor
                );
            }
        };

        this.addRenderableWidget(button);
        this.currentTabOptions.add(new OptionEntry(button, Component.translatable("xeno.options." + key + ".desc"), impact, Component.translatable("xeno.options." + key)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addCycle(OptionInstance option, String key, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        Object current = getOptionValue(option);

        Component msg = getOptionValueText(option, current);

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            Object val = getOptionValue(option);
            Object nextVal;
            switch (val) {
                case Enum<?> anEnum -> {
                    Object[] constants = val.getClass().getEnumConstants();
                    int next = (anEnum.ordinal() + 1) % constants.length;
                    nextVal = constants[next];
                    if (nextVal == GraphicsPreset.CUSTOM) {
                        nextVal = GraphicsPreset.FAST;
                    }
                }
                case Boolean b -> nextVal = !b;
                case Integer intVal -> {
                    if (option == this.options.biomeBlendRadius()) {
                        nextVal = (intVal + 1) % 8;
                    } else if (option == this.options.maxAnisotropyBit()) {
                        nextVal = 1 + (intVal % 3);
                    } else if (option == this.options.mipmapLevels()) {
                        nextVal = (intVal + 1) % 5;
                    } else if (option == this.options.guiScale()) {
                        nextVal = intVal >= 4 ? 1 : intVal + 1;
                    } else {
                        nextVal = intVal + 1;
                    }
                }
                case null, default -> nextVal = val;
            }

            if (option == this.options.graphicsPreset()) {
                applyPresetToPending((GraphicsPreset) nextVal);
            } else {
                markOptionChanged(option, nextVal);
                btn.setMessage(getOptionValueText(option, nextVal));
            }
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();
                boolean hovered = this.isHoveredOrFocused();

                int textColor = hovered ? 0xFFFFFFFF : 0xFFD8B4FE;
                graphics.text(
                        Minecraft.getInstance().font,
                        this.getMessage(),
                        x + w - Minecraft.getInstance().font.width(this.getMessage()) - 6,
                        y + (h - 8) / 2,
                        textColor
                );
            }
        };

        this.addRenderableWidget(button);
        this.currentTabOptions.add(new OptionEntry(button, Component.translatable("xeno.options." + key + ".desc"), impact, Component.translatable("xeno.options." + key)));
    }

    private Component getOptionValueText(OptionInstance<?> option, Object value) {
        if (option == this.options.chunkSectionFadeInTime()) {
            return Component.translatable((Double) value > 0.0 ? "xeno.options.value.on" : "xeno.options.value.off");
        }
        if (value instanceof Boolean) {
            return Component.translatable((Boolean) value ? "xeno.options.value.on" : "xeno.options.value.off");
        }
        if (option == this.options.gamma()) {
            int progress = (int) (((Double) value) * 100.0);
            if (progress == 0) {
                return Component.translatable("xeno.options.value.moody");
            } else if (progress == 50) {
                return Component.translatable("xeno.options.value.default");
            } else if (progress == 100) {
                return Component.translatable("xeno.options.value.bright");
            }
            return Component.literal(String.valueOf(progress));
        }
        if (value instanceof PreferredGraphicsApi) {
            return ((PreferredGraphicsApi) value).caption();
        }
        if (value instanceof TextureFilteringMethod) {
            if (value == TextureFilteringMethod.NONE) return Component.translatable("xeno.options.value.none");
            if (value == TextureFilteringMethod.RGSS) return Component.translatable("xeno.options.value.rgss");
            if (value == TextureFilteringMethod.ANISOTROPIC) return Component.translatable("xeno.options.value.anisotropic");
        }
        if (value instanceof AttackIndicatorStatus) {
            switch (value) {
                case AttackIndicatorStatus.OFF -> {
                    return Component.translatable("xeno.options.value.off");
                }
                case AttackIndicatorStatus.CROSSHAIR -> {
                    return Component.translatable("xeno.options.value.crosshair");
                }
                case AttackIndicatorStatus.HOTBAR -> {
                    return Component.translatable("xeno.options.value.hotbar");
                }
                default -> throw new IllegalStateException("Unexpected value: " + value);
            }
        }
        if (value instanceof InactivityFpsLimit) {
            return ((InactivityFpsLimit) value).caption();
        }
        if (value instanceof PrioritizeChunkUpdates) {
            return ((PrioritizeChunkUpdates) value).caption();
        }
        if (value instanceof Enum<?>) {
            return Component.literal(capitalize(((Enum<?>) value).name()));
        }
        if (value instanceof Integer) {
            int val = (Integer) value;
            if (option == this.options.biomeBlendRadius()) {
                return val == 0
                        ? Component.translatable("xeno.options.value.off")
                        : Component.literal((val * 2 + 1) + "x" + (val * 2 + 1));
            }
            if (option == this.options.maxAnisotropyBit()) {
                return Component.literal((1 << val) + "x");
            }
            if (option == this.options.mipmapLevels()) {
                return val == 0 ? Component.translatable("xeno.options.value.off_title") : Component.literal(val + "x");
            }
            if (option == this.options.guiScale()) {
                return val == 0 ? Component.translatable("xeno.options.value.auto") : Component.literal(val + "x");
            }
            return Component.literal(String.valueOf(val));
        }
        return Component.literal(value.toString());
    }

    private Component getSliderValueText(OptionInstance<Integer> option, int value) {
        if (option == this.options.framerateLimit() && value == 260) {
            return Component.translatable("xeno.options.value.unlimited");
        }
        if (option == this.options.renderDistance()
                || option == this.options.simulationDistance()
                || option == this.options.cloudRange()) {
            return Component.translatable("xeno.options.value.chunks", value);
        }
        if (option == this.options.weatherRadius()) {
            return Component.translatable("xeno.options.value.blocks", value);
        }
        return Component.literal(String.valueOf(value));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private void applyOptions() {
        boolean restartWantedBefore = this.options.isRestartRequiredToApplyVideoSettings();

        boolean backendChanged = false;
        if (this.pendingChanges.containsKey(this.options.preferredGraphicsBackend())) {
            Object newVal = this.pendingChanges.get(this.options.preferredGraphicsBackend());
            if (newVal != this.options.preferredGraphicsBackend().get()) {
                backendChanged = true;
            }
        }

        for (Map.Entry<OptionInstance<?>, Object> entry : this.pendingChanges.entrySet()) {
            OptionInstance option = entry.getKey();
            Object value = entry.getValue();
            option.set(value);
        }

        this.pendingChanges.clear();
        this.minecraft.options.save();

        if (this.resolutionMonitor != null && this.pendingResolution != this.initialResolution) {
            Window window = this.minecraft.getWindow();
            window.setPreferredFullscreenVideoMode(
                    this.pendingResolution == -1
                            ? Optional.empty()
                            : Optional.of(this.resolutionMonitor.mode(this.pendingResolution))
            );
        }
        this.minecraft.getWindow().changeFullscreenVideoMode();

        int currentMip = this.options.mipmapLevels().get();
        int currentAniso = this.options.maxAnisotropyBit().get();
        TextureFilteringMethod currentFilter = this.options.textureFiltering().get();

        if (currentMip != this.oldMipmaps || currentAniso != this.oldAnisotropyBit || currentFilter != this.oldTextureFiltering) {
            this.minecraft.updateMaxMipLevel(currentMip);
            this.minecraft.delayTextureReload();
        }

        boolean restartWantedAfter = this.options.isRestartRequiredToApplyVideoSettings();
        if (backendChanged || (!restartWantedBefore && restartWantedAfter)) {
            this.warningBannerTime = System.currentTimeMillis() + 5000;
        }
    }

    private void updateScrollerRange() {
        float contentHeight = this.currentTabOptions.size() * ROW_HEIGHT;
        float visibleHeight = this.height - 40 - 35;
        this.scroller.setScrollRange(contentHeight, visibleHeight);
    }

    private void updateWidgetPositions() {
        int visibleMinY = 40;
        int visibleMaxY = this.height - 35;

        for (int i = 0; i < this.currentTabOptions.size(); i++) {
            OptionEntry entry = this.currentTabOptions.get(i);
            AbstractWidget widget = entry.widget();

            int yPos = 40 + i * ROW_HEIGHT - (int) this.scrollOffset;
            int controlHeight = widget.getHeight();

            int widgetX = (widget instanceof XenoSlider) ? (this.width - 30 - 80) : (this.width - 30 - 120);
            int widgetY = yPos + (ROW_HEIGHT - controlHeight) / 2;

            boolean inBounds = (yPos >= visibleMinY - 2 && yPos + ROW_HEIGHT <= visibleMaxY + 2);
            widget.visible = inBounds;
            widget.active = inBounds && (entry.activeCondition() == null || entry.activeCondition().getAsBoolean());
            if (inBounds) {
                widget.setPosition(widgetX, widgetY);
            }
        }
    }

    private OptionEntry findHoveredOption(double mouseX, double mouseY) {
        int visibleMinY = 40;
        int visibleMaxY = this.height - 35;

        for (int i = 0; i < this.currentTabOptions.size(); i++) {
            OptionEntry entry = this.currentTabOptions.get(i);
            int yPos = 40 + i * ROW_HEIGHT - (int) this.scrollOffset;

            if (yPos + ROW_HEIGHT > visibleMinY && yPos < visibleMaxY) {
                if (mouseX >= 130 && mouseX <= this.width - 30 && mouseY >= yPos && mouseY <= yPos + ROW_HEIGHT - 2) {
                    return entry;
                }
            }
        }
        return null;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.scrollOffset += (this.targetScrollOffset - this.scrollOffset) * 0.25F;

        if (this.scroller != null) {
            this.targetScrollOffset = this.scroller.getScrollAmount();
        }

        updateWidgetPositions();

        graphics.fill(0, 0, this.width, this.height, 0x99050505);

        graphics.fill(0, 0, 110, this.height, 0xB3020202);

        graphics.text(this.font, this.getTitle(), 130, 15, 0xFFFFFFFF);

        int visibleMinY = 40;
        int visibleMaxY = this.height - 35;

        OptionEntry hovered = findHoveredOption(mouseX, mouseY);

        for (int i = 0; i < this.currentTabOptions.size(); i++) {
            OptionEntry entry = this.currentTabOptions.get(i);
            int yPos = 40 + i * ROW_HEIGHT - (int) this.scrollOffset;

            if (yPos + ROW_HEIGHT > visibleMinY && yPos < visibleMaxY) {
                if (hovered == entry) {
                    graphics.fill(130, yPos, this.width - 30, yPos + ROW_HEIGHT - 2, 0x1AFFFFFF);
                }

                int nameColor = entry.widget().active ? 0xFFFFFFFF : 0xFF6B7280;
                graphics.text(this.font, entry.name(), 140, yPos + (ROW_HEIGHT - 8) / 2, nameColor);

                if (entry.widget() instanceof XenoSlider) {
                    Component valMsg = entry.widget().getMessage();
                    int textWidth = this.font.width(valMsg);
                    int textColor = (hovered == entry) ? 0xFFFFFFFF : 0xFFD8B4FE;
                    graphics.text(this.font, valMsg, this.width - 115 - textWidth, yPos + (ROW_HEIGHT - 8) / 2, textColor);
                }
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (hovered != this.lastHoveredOption) {
            this.lastHoveredOption = hovered;
            if (hovered != null) {
                this.hoverStartTime = System.currentTimeMillis();
            } else {
                this.hoverStartTime = 0;
            }
        }

        if (hovered != null && this.hoverStartTime > 0 && System.currentTimeMillis() - this.hoverStartTime >= 500) {
            renderTooltip(graphics, mouseX, mouseY, hovered);
        }

        if (System.currentTimeMillis() < this.warningBannerTime) {
            graphics.fill(0, 0, this.width, 14, 0xDDDC2626);
            Component msg = Component.translatable("options.restartRequired");
            graphics.centeredText(this.font, msg, this.width / 2, 3, 0xFFFFFFFF);
        }
    }

    private void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, OptionEntry entry) {
        Component desc = entry.description();
        Component impactLabel = Component.translatable("xeno.options.performance_impact");
        Component impactValue = entry.impact().getText();
        int impactColor = entry.impact().getColor();

        int maxWidth = 220;
        List<FormattedCharSequence> lines = this.font.split(desc, maxWidth);

        int labelWidth = this.font.width(impactLabel);
        int valueWidth = this.font.width(impactValue);

        int maxLineWidth = labelWidth + valueWidth;
        for (FormattedCharSequence line : lines) {
            maxLineWidth = Math.max(maxLineWidth, this.font.width(line));
        }

        int boxWidth = maxLineWidth + 12;
        int boxHeight = (lines.size() + 1) * 10 + 14;

        int boxX = mouseX + 12;
        int boxY = mouseY - boxHeight - 4;

        if (boxX + boxWidth > this.width) {
            boxX = mouseX - boxWidth - 4;
        }
        if (boxY < 0) {
            boxY = mouseY + 12;
        }

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFA000000);
        graphics.outline(boxX, boxY, boxWidth, boxHeight, 0xFF8B5CF6);

        int textY = boxY + 6;
        for (FormattedCharSequence line : lines) {
            graphics.text(this.font, line, boxX + 6, textY, 0xFFFFFFFF);
            textY += 10;
        }

        textY += 2;
        graphics.text(this.font, impactLabel, boxX + 6, textY, 0xFFAAAAAA);
        graphics.text(this.font, impactValue, boxX + 6 + labelWidth, textY, impactColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (event.button() == 1 || event.button() == 0) {
            if (mx >= 0 && mx <= 110) {
                for (int i = 0; i < this.tabs.size(); i++) {
                    int minY = 40 + i * 24;
                    int maxY = minY + 20;
                    if (my >= minY && my <= maxY) {
                        if (this.currentTab != i) {
                            this.currentTab = i;
                            this.scrollOffset = 0;
                            if (this.scroller != null) {
                                this.scroller.setScrollAmount(0);
                            }
                            this.rebuildWidgets();
                        }
                        return true;
                    }
                }
            }
        }

        if (this.scroller != null) {
            if (mx >= this.width - 16 && mx <= this.width && my >= 40 && my <= this.height - 35) {
                if (this.scroller.mouseClicked(event, doubleClick)) {
                    this.setFocused(this.scroller);
                    if (event.button() == 1 || event.button() == 0) {
                        this.setDragging(true);
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.scroller != null) {
            this.scroller.applyScrollDelta(scrollY);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.lastScreen);
    }
}
