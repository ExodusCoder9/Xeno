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

import com.xeno.client.gui.widget.XenoButton;
import com.xeno.client.gui.widget.XenoScroller;
import com.xeno.client.gui.widget.XenoSlider;
import com.xeno.client.gui.widget.XenoTab;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

public class XenoVideoSettingsScreen extends Screen {

    public enum PerformanceImpact {
        LOW("Low", 0xFF4ADE80),
        MEDIUM("Medium", 0xFFFACC15),
        HIGH("High", 0xFF8B5CF6);

        private final String label;
        private final int color;

        PerformanceImpact(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public Component getText() {
            return Component.literal(this.label);
        }

        public int getColor() {
            return this.color;
        }
    }

    private record OptionEntry(AbstractWidget widget, Component description, PerformanceImpact impact, String name) {
    }

    private static final Component TITLE = Component.translatable("options.videoTitle");
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

        int tabY = 40;
        String[] tabNames = {"General", "Quality", "Performance", "Advanced"};
        for (int i = 0; i < tabNames.length; i++) {
            XenoTab tab = new XenoTab(0, tabY, 110, 20, Component.literal(tabNames[i]), this.currentTab == i, _ -> {}) {
                @Override
                protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                    int x = this.getX();
                    int y = this.getY();
                    int w = this.getWidth();
                    int h = this.getHeight();
                    boolean hovered = this.isHoveredOrFocused();

                    int bgColor;
                    int textColor;

                    if (this.isActive()) {
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

                    if (this.isActive()) {
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
        this.addRenderableWidget(new XenoButton(applyX, this.height - 30, 120, 20, Component.literal("Apply Options"), _ -> this.applyOptions()) {
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
            addCycle(this.options.preferredGraphicsBackend(), "Graphics API",
                    "Chooses the preferred graphics rendering API. Default relies on native platforms; Vulkan offers modern hardware optimizations.",
                    PerformanceImpact.MEDIUM);
            addCycle(this.options.guiScale(), "GUI Scale",
                    "Adjusts the size of the user interface.",
                    PerformanceImpact.LOW);
            addSlider(this.options.renderDistance(), "Render Distance", 2, 32,
                    "Determines how far chunks are rendered around the player. Higher values increase visibility but cost more performance.",
                    PerformanceImpact.MEDIUM);
            addSlider(this.options.simulationDistance(), "Simulation Distance", 5, 32,
                    "Controls how far entity simulations and block ticks occur. Lower values reduce CPU load.",
                    PerformanceImpact.MEDIUM);
            addSlider(this.options.framerateLimit(), "FPS Limit", 10, 260,
                    "Caps the maximum frames per second. Lower values reduce GPU load and power consumption.",
                    PerformanceImpact.LOW);
            addToggle(this.options.enableVsync(), "VSync",
                    "Synchronizes frame output with monitor refresh rate. Reduces screen tearing but may add input lag.",
                    PerformanceImpact.LOW);
            addToggle(this.options.fullscreen(), "Fullscreen",
                    "Toggles between windowed and fullscreen display modes.",
                    PerformanceImpact.LOW);
            addToggle(this.options.exclusiveFullscreen(), "Exclusive Fullscreen",
                    "Enables exclusive control over the monitor display when running in fullscreen.",
                    PerformanceImpact.LOW);
            addCycle(this.options.attackIndicator(), "Attack Indicator",
                    "Sets the display style and position for the combat crosshair attack indicator.",
                    PerformanceImpact.LOW);
            addToggle(this.options.showAutosaveIndicator(), "Autosave Indicator",
                    "Enables a subtle disk indicator in the corner of the screen when the game performs an autosave.",
                    PerformanceImpact.LOW);

        } else if (tabIndex == 1) {
            addCycle(this.options.graphicsPreset(), "Graphics",
                    "Controls visual graphics preset. Fast disables extra lighting effects; Fabulous enables advanced layers.",
                    PerformanceImpact.HIGH);
            addToggle(this.options.ambientOcclusion(), "Smooth Lighting",
                    "Applies ambient shadows between blocks to present smooth, atmospheric surface illumination.",
                    PerformanceImpact.MEDIUM);
            addCycle(this.options.cloudStatus(), "Clouds",
                    "Adjusts cloud styling. Fancy renders beautiful volumetric clouds; OFF disables cloud layers completely.",
                    PerformanceImpact.LOW);
            addSlider(this.options.cloudRange(), "Cloud Distance", 2, 128,
                    "Specifies the maximum distance (in chunks) cloud models are rendered.",
                    PerformanceImpact.LOW);
            addCycle(this.options.biomeBlendRadius(), "Biome Blend",
                    "Determines blending radius for neighboring biome colors. OFF yields sharp boundaries; higher values blend smoothly.",
                    PerformanceImpact.LOW);
            addCycle(this.options.particles(), "Particles",
                    "Specifies density of environmental, combat, and command-driven particles.",
                    PerformanceImpact.MEDIUM);
            addCycle(this.options.mipmapLevels(), "Mipmap",
                    "Sharpens distant textures using multi-resolution texture maps. Lower levels can improve performance.",
                    PerformanceImpact.LOW);
            addToggle(this.options.entityShadows(), "Entity Shadows",
                    "Enables circular shadow silhouettes beneath living entity entities and items.",
                    PerformanceImpact.LOW);
            addToggle(this.options.cutoutLeaves(), "See-Through Leaves",
                    "Controls leaf block transparency. Solid opaque leaves bypass translucency checks and render faster.",
                    PerformanceImpact.MEDIUM);
            addSlider(this.options.weatherRadius(), "Weather Effect Radius", 2, 10,
                    "Defines block radius for weather rendering and audio sources centered on the player.",
                    PerformanceImpact.MEDIUM);
            addToggle(this.options.vignette(), "Show Vignette",
                    "Applies a cinematic darkening overlay around the borders of the screen.",
                    PerformanceImpact.LOW);

        } else if (tabIndex == 2) {
            addDoubleToggle(this.options.chunkSectionFadeInTime(), "Chunk Fade Time",
                    "Applies smooth fade-in animations to newly loaded chunk sections.",
                    PerformanceImpact.LOW);
            addDoubleSlider(this.options.entityDistanceScaling(), "Entity Distance", 0.5, 5.0,
                    "Adjusts the distance threshold for rendering entity models. Lower scaling values save significant GPU time.",
                    PerformanceImpact.MEDIUM);

        } else if (tabIndex == 3) {
            addCycle(this.options.textureFiltering(), "Texture Filtering",
                    "Applies texture sampling methods. RGSS or Anisotropic filtering keep oblique angles sharp.",
                    PerformanceImpact.LOW);
            addCycle(this.options.maxAnisotropyBit(), "Anisotropic Value",
                    "Specifies level of anisotropic filtering. Higher values retain fine textures on steep slopes.",
                    PerformanceImpact.LOW);
        }
    }

    private void addSlider(OptionInstance<Integer> option, String name, int min, int max, String desc, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        int current = getOptionValue(option);

        if ("Simulation Distance".equals(name) && current < 5) {
            current = 5;
        }
        if ("FPS Limit".equals(name) && current > 250) {
            current = 260;
        }
        if ("Weather Effect Radius".equals(name) && current < 3) {
            current = 10;
        }

        double sliderValue;
        if ("FPS Limit".equals(name)) {
            if (current >= 260) {
                sliderValue = 1.0;
            } else {
                sliderValue = (double) (current - 10) / 250.0;
            }
        } else {
            sliderValue = max > min ? (double) (current - min) / (max - min) : 0.0;
        }

        Component msg = getSliderValueText(name, current);

        XenoSlider slider = new XenoSlider(0, y, 80, 20, msg, sliderValue, s -> {
            int value;
            if ("FPS Limit".equals(name)) {
                int rawValue = 10 + (int) Math.round(s.getDoubleValue() * 250);
                value = ((rawValue + 5) / 10) * 10;
                if (value > 250) {
                    value = 260;
                }
            } else {
                value = min + (int) Math.round(s.getDoubleValue() * (max - min));
                if ("Simulation Distance".equals(name) && value < 5) {
                    value = 5;
                }
                if ("Weather Effect Radius".equals(name) && value < 3) {
                    value = 3;
                }
            }
            markOptionChanged(option, value);
            s.setMessage(getSliderValueText(name, value));
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
        this.currentTabOptions.add(new OptionEntry(slider, Component.literal(desc), impact, name));
    }

    private void addDoubleSlider(OptionInstance<Double> option, String name, double min, double max, String desc, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        double current = getOptionValue(option);

        double sliderValue = (current - min) / (max - min);
        Component msg = Component.literal(Math.round(current * 100) + "%");

        XenoSlider slider = new XenoSlider(0, y, 80, 20, msg, sliderValue, s -> {
            double rawVal = min + s.getDoubleValue() * (max - min);
            double value = Math.round(rawVal * 4.0) / 4.0;
            value = Math.clamp(value, min, max);
            markOptionChanged(option, value);
            s.setMessage(Component.literal(Math.round(value * 100) + "%"));
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
        this.currentTabOptions.add(new OptionEntry(slider, Component.literal(desc), impact, name));
    }

    private void addDoubleToggle(OptionInstance<Double> option, String name, String desc, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        double current = getOptionValue(option);
        Component msg = getOptionValueText(name, current);

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            double val = getOptionValue(option);
            double nextVal = val > 0.0 ? 0.0 : 0.75;
            markOptionChanged(option, nextVal);
            btn.setMessage(getOptionValueText(name, nextVal));
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
        this.currentTabOptions.add(new OptionEntry(button, Component.literal(desc), impact, name));
    }

    private void addToggle(OptionInstance<Boolean> option, String name, String desc, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        boolean current = getOptionValue(option);
        Component msg = getOptionValueText(name, current);

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            boolean val = getOptionValue(option);
            markOptionChanged(option, !val);
            btn.setMessage(getOptionValueText(name, !val));
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
        this.currentTabOptions.add(new OptionEntry(button, Component.literal(desc), impact, name));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addCycle(OptionInstance option, String name, String desc, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        Object current = getOptionValue(option);

        Component msg = getOptionValueText(name, current);

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            Object val = getOptionValue(option);
            Object nextVal;
            if (val instanceof Enum<?>) {
                Object[] constants = val.getClass().getEnumConstants();
                int next = (((Enum<?>) val).ordinal() + 1) % constants.length;
                nextVal = constants[next];
                if (nextVal == GraphicsPreset.CUSTOM) {
                    nextVal = GraphicsPreset.FAST;
                }
            } else if (val instanceof Boolean) {
                nextVal = !((Boolean) val);
            } else if (val instanceof Integer) {
                int intVal = (Integer) val;
                if ("Biome Blend".equals(name)) {
                    nextVal = (intVal + 1) % 8;
                } else if ("Anisotropic Value".equals(name)) {
                    nextVal = 1 + (intVal % 3);
                } else if ("Mipmap".equals(name) || "Mipmap Levels".equals(name)) {
                    nextVal = (intVal + 1) % 5;
                } else if ("GUI Scale".equals(name)) {
                    nextVal = intVal >= 4 ? 1 : intVal + 1;
                } else {
                    nextVal = intVal + 1;
                }
            } else {
                nextVal = val;
            }

            if (option == this.options.graphicsPreset()) {
                applyPresetToPending((GraphicsPreset) nextVal);
            } else {
                markOptionChanged(option, nextVal);
                btn.setMessage(getOptionValueText(name, nextVal));
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
        this.currentTabOptions.add(new OptionEntry(button, Component.literal(desc), impact, name));
    }

    private static Component getOptionValueText(String optionName, Object value) {
        if ("Chunk Fade Time".equals(optionName)) {
            return Component.literal((Double) value > 0.0 ? "ON" : "OFF");
        }
        if (value instanceof Boolean) {
            return Component.literal((Boolean) value ? "ON" : "OFF");
        }
        if (value instanceof PreferredGraphicsApi) {
            return ((PreferredGraphicsApi) value).caption();
        }
        if (value instanceof TextureFilteringMethod) {
            if (value == TextureFilteringMethod.NONE) return Component.literal("None");
            if (value == TextureFilteringMethod.RGSS) return Component.literal("RGSS");
            if (value == TextureFilteringMethod.ANISOTROPIC) return Component.literal("Anisotropic");
        }
        if (value instanceof AttackIndicatorStatus) {
            if (value == AttackIndicatorStatus.OFF) return Component.literal("OFF");
            if (value == AttackIndicatorStatus.CROSSHAIR) return Component.literal("Crosshair");
            if (value == AttackIndicatorStatus.HOTBAR) return Component.literal("Hotbar");
        }
        if (value instanceof Enum<?>) {
            return Component.literal(capitalize(((Enum<?>) value).name()));
        }
        if (value instanceof Integer) {
            int val = (Integer) value;
            if ("Biome Blend".equals(optionName)) {
                return Component.literal(val == 0 ? "OFF" : (val * 2 + 1) + "x" + (val * 2 + 1));
            }
            if ("Anisotropic Value".equals(optionName)) {
                return Component.literal((1 << val) + "x");
            }
            if ("Mipmap".equals(optionName) || "Mipmap Levels".equals(optionName)) {
                return Component.literal(val == 0 ? "Off" : val + "x");
            }
            if ("GUI Scale".equals(optionName)) {
                return Component.literal(val + "x");
            }
            return Component.literal(String.valueOf(val));
        }
        return Component.literal(value.toString());
    }

    private static Component getSliderValueText(String optionName, int value) {
        if ("FPS Limit".equals(optionName) && value == 260) {
            return Component.literal("Unlimited");
        }
        if ("Render Distance".equals(optionName) || "Simulation Distance".equals(optionName)) {
            return Component.literal(value + " chunks");
        }
        if ("Cloud Distance".equals(optionName)) {
            return Component.literal(value + " chunks");
        }
        if ("Weather Effect Radius".equals(optionName)) {
            return Component.literal(value + " blocks");
        }
        if ("Mipmap Levels".equals(optionName)) {
            return Component.literal(value == 0 ? "OFF" : value + "x");
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
            int controlWidth = widget.getWidth();
            int controlHeight = widget.getHeight();

            int widgetX = (widget instanceof XenoSlider) ? (this.width - 30 - 80) : (this.width - 30 - 120);
            int widgetY = yPos + (ROW_HEIGHT - controlHeight) / 2;

            boolean inBounds = (yPos >= visibleMinY - 2 && yPos + ROW_HEIGHT <= visibleMaxY + 2);
            widget.visible = inBounds;
            widget.active = inBounds;
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

                graphics.text(this.font, Component.literal(entry.name()), 140, yPos + (ROW_HEIGHT - 8) / 2, 0xFFFFFFFF);

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
        Component impactLabel = Component.literal("Performance Impact: ");
        Component impactValue = entry.impact().getText();
        int impactColor = entry.impact().getColor();

        int maxWidth = 220;
        List<FormattedCharSequence> lines = this.font.split(desc, maxWidth);

        int labelWidth = this.font.width(impactLabel);
        int valueWidth = this.font.width(impactValue);
        int impactWidth = labelWidth + valueWidth;

        int maxLineWidth = impactWidth;
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

        if (event.button() == 0) {
            if (mx >= 0 && mx <= 110) {
                for (int i = 0; i < 4; i++) {
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
                    if (event.button() == 0) {
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