package com.xeno.client.gui;

import com.xeno.client.gui.widget.XenoButton;
import com.xeno.client.gui.widget.XenoScroller;
import com.xeno.client.gui.widget.XenoSlider;
import com.xeno.client.gui.widget.XenoTab;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.Minecraft;
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
        HIGH("High", 0XFFA855F7);

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
    private static final int ROW_HEIGHT = 30; // Clean, modern vertical spacing

    private final Screen lastScreen;
    private final Options options;

    private final List<XenoTab> tabs = new ArrayList<>();
    private int currentTab = 0; // 0: General, 1: Quality, 2: Performance, 3: Advanced

    private final List<OptionEntry> currentTabOptions = new ArrayList<>();

    private XenoScroller scroller;
    private float scrollOffset;
    private float targetScrollOffset;

    public XenoVideoSettingsScreen(Screen lastScreen, Options options) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.options = options;
    }

    @Override
    protected void init() {
        this.tabs.clear();
        this.scrollOffset = 0;
        this.targetScrollOffset = 0;

        // Initialize sidebar tabs
        int tabY = 40;
        String[] tabNames = {"General", "Quality", "Performance", "Advanced"};
        for (int i = 0; i < tabNames.length; i++) {
            XenoTab tab = new XenoTab(0, tabY, 110, 20, Component.literal(tabNames[i]), this.currentTab == i, _ -> {}) {
                @Override
                protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                    int x = this.getX();
                    int y = this.getY();
                    int w = this.getWidth();
                    int h = this.getHeight();
                    boolean hovered = this.isHoveredOrFocused();

                    int bgColor;
                    int textColor;

                    if (this.isActive()) {
                        bgColor = 0x1AA855F7; // subtle highlight
                        textColor = 0xFFF1F5F9; // crisp white/lilac
                    } else if (hovered) {
                        bgColor = 0x11A855F7; // very subtle hover highlight
                        textColor = 0xFFF1F5F9;
                    } else {
                        bgColor = 0x00000000;
                        textColor = 0xFF64748B; // muted/unselected element color
                    }

                    if (bgColor != 0) {
                        graphics.fill(x, y, x + w, y + h, bgColor);
                    }

                    if (this.isActive()) {
                        // 2px wide violet line on the far left edge
                        graphics.fill(x, y, x + 2, y + h, 0XFFA855F7);
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

        // Populate active tab options
        populateOptionsForTab(this.currentTab);

        // Vertical Scroller (Width: 6px thumb, X: width - 12)
        this.scroller = XenoScroller.vertical(
                this.width - 12, 40,
                this.height - 50
        );
        this.addRenderableWidget(this.scroller);

        updateScrollerRange();
        updateWidgetPositions();
    }

    private void populateOptionsForTab(int tabIndex) {
        this.currentTabOptions.clear();

        if (tabIndex == 0) {
            // General
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
        } else if (tabIndex == 1) {
            // Quality
            addCycle(this.options.graphicsPreset(), "Graphics",
                    "Controls visual quality level. Fast disables effects, Fancy enables them, Fabulous adds translucency sorting.",
                    PerformanceImpact.HIGH);
            addCycle(this.options.cloudStatus(), "Clouds",
                    "Controls cloud rendering. Off disables clouds completely for a performance boost.",
                    PerformanceImpact.LOW);
            addCycle(this.options.particles(), "Particles",
                    "Controls particle density. Fewer particles reduce CPU overhead during combat and weather.",
                    PerformanceImpact.MEDIUM);
            addToggle(this.options.entityShadows(), "Entity Shadows",
                    "Renders simple circular shadows under entities. Disabling saves minor GPU time.",
                    PerformanceImpact.LOW);
            addSlider(this.options.biomeBlendRadius(), "Biome Blend", 0, 7,
                    "Controls color blending radius between biomes. Lower values are cheaper.",
                    PerformanceImpact.LOW);
        } else if (tabIndex == 2) {
            // Performance
            addSlider(this.options.mipmapLevels(), "Mipmap Levels", 0, 4,
                    "Controls texture mipmap quality. Lower values use less VRAM but may cause texture shimmer.",
                    PerformanceImpact.LOW);
        }
    }

    private void addSlider(OptionInstance<Integer> option, String name, int min, int max, String desc, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        int current = option.get();

        // Safe capping boundaries
        if ("Simulation Distance".equals(name) && current < 5) {
            current = 5;
            option.set(5);
        }
        if ("FPS Limit".equals(name) && current > 250) {
            current = 260;
            option.set(260);
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

        Component msg;
        if ("FPS Limit".equals(name) && current == 260) {
            msg = Component.literal("Unlimited");
        } else if ("Biome Blend".equals(name)) {
            msg = Component.literal(current == 0 ? "OFF" : (current * 2 + 1) + "x" + (current * 2 + 1));
        } else {
            msg = Component.literal(current + ("Render Distance".equals(name) || "Simulation Distance".equals(name) ? " chunks" : ""));
        }

        XenoSlider slider = new XenoSlider(0, y, 120, 20, msg, sliderValue, s -> {
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
            }
            option.set(value);

            Component newMsg;
            if ("FPS Limit".equals(name) && value == 260) {
                newMsg = Component.literal("Unlimited");
            } else if ("Biome Blend".equals(name)) {
                newMsg = Component.literal(value == 0 ? "OFF" : (value * 2 + 1) + "x" + (value * 2 + 1));
            } else {
                newMsg = Component.literal(value + ("Render Distance".equals(name) || "Simulation Distance".equals(name) ? " chunks" : ""));
            }
            s.setMessage(newMsg);
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();
                boolean hovered = this.isHoveredOrFocused();

                // Clean flat design: subtle background on hover
                if (hovered) {
                    graphics.fill(x, y, x + w, y + h, 0x11A855F7);
                }

                // Slider track
                graphics.fill(x, y + h / 2 - 1, x + w, y + h / 2 + 1, 0xFF120F20);

                // Slider handle
                int handleWidth = 4;
                int handleX = x + (int) (this.getDoubleValue() * (w - handleWidth));
                int handleColor = (hovered || this.isFocused()) ? 0xFFC084FC : 0xFFA855F7;

                // Draw filled part of the track
                graphics.fill(x, y + h / 2 - 1, handleX, y + h / 2 + 1, 0XFFA855F7);

                // Draw handle line
                graphics.fill(handleX, y, handleX + handleWidth, y + h, handleColor);

                // Right aligned inside control zones
                int textColor = hovered ? 0xFFF3E8FF : 0xFFC084FC;
                graphics.text(
                        Minecraft.getInstance().font,
                        this.getMessage(),
                        x + w - Minecraft.getInstance().font.width(this.getMessage()) - 6,
                        y + (h - 8) / 2,
                        textColor
                );
            }
        };

        this.addRenderableWidget(slider);
        this.currentTabOptions.add(new OptionEntry(slider, Component.literal(desc), impact, name));
    }

    private void addToggle(OptionInstance<Boolean> option, String name, String desc, PerformanceImpact impact) {
        int index = this.currentTabOptions.size();
        int y = 40 + index * ROW_HEIGHT;
        boolean current = option.get();
        Component msg = Component.literal(current ? "ON" : "OFF");

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            boolean val = option.get();
            option.set(!val);
            btn.setMessage(Component.literal(!val ? "ON" : "OFF"));
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();
                boolean hovered = this.isHoveredOrFocused();

                int bgColor = hovered ? 0x1AA855F7 : 0xFF120F20;
                graphics.fill(x, y, x + w, y + h, bgColor);
                graphics.outline(x, y, w, h, hovered ? 0XFFA855F7 : 0xFF3B0764);

                int textColor = hovered ? 0xFFF3E8FF : 0xFFC084FC;
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
        Enum<?> current = (Enum<?>) option.get();
        Component msg = Component.literal(capitalize(current.name()));

        XenoButton button = new XenoButton(0, y, 120, 20, msg, btn -> {
            Enum<?> val = (Enum<?>) option.get();
            Object[] constants = val.getDeclaringClass().getEnumConstants();
            int next = (val.ordinal() + 1) % constants.length;
            option.set(constants[next]);
            btn.setMessage(Component.literal(capitalize(((Enum<?>) constants[next]).name())));
        }) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();
                boolean hovered = this.isHoveredOrFocused();

                int bgColor = hovered ? 0x1AA855F7 : 0xFF120F20;
                graphics.fill(x, y, x + w, y + h, bgColor);
                graphics.outline(x, y, w, h, hovered ? 0XFFA855F7 : 0xFF3B0764);

                int textColor = hovered ? 0xFFF3E8FF : 0xFFC084FC;
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

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private void updateScrollerRange() {
        float contentHeight = this.currentTabOptions.size() * ROW_HEIGHT;
        float visibleHeight = this.height - 50;
        this.scroller.setScrollRange(contentHeight, visibleHeight);
    }

    private void updateWidgetPositions() {
        int visibleMinY = 40;
        int visibleMaxY = this.height - 10;

        for (int i = 0; i < this.currentTabOptions.size(); i++) {
            OptionEntry entry = this.currentTabOptions.get(i);
            AbstractWidget widget = entry.widget();

            int yPos = 40 + i * ROW_HEIGHT - (int) this.scrollOffset;
            int controlWidth = widget.getWidth();
            int controlHeight = widget.getHeight();
            int widgetX = this.width - 30 - controlWidth - 10;
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
        int visibleMaxY = this.height - 10;

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

        // 1. Draw Deep dark slate charcoal background with faint purple tint (0xFF0B0914)
        graphics.fill(0, 0, this.width, this.height, 0xFF0B0914);

        // 2. Draw Sidebar Panel Background (0xFF120F20)
        graphics.fill(0, 0, 110, this.height, 0xFF120F20);

        // 3. Draw Title (options.videoTitle) left-aligned in content panel
        graphics.text(this.font, this.getTitle(), 130, 15, 0xFFF1F5F9);

        // 4. Draw Option Rows
        int visibleMinY = 40;
        int visibleMaxY = this.height - 10;

        for (int i = 0; i < this.currentTabOptions.size(); i++) {
            OptionEntry entry = this.currentTabOptions.get(i);
            int yPos = 40 + i * ROW_HEIGHT - (int) this.scrollOffset;

            if (yPos + ROW_HEIGHT > visibleMinY && yPos < visibleMaxY) {
                // Low-profile dark background strip (0xFF18152A)
                graphics.fill(130, yPos, this.width - 30, yPos + ROW_HEIGHT - 2, 0xFF18152A);

                // Option Names: Crisp white/lilac (0xFFF1F5F9) left-aligned
                graphics.text(this.font, Component.literal(entry.name()), 140, yPos + (ROW_HEIGHT - 8) / 2, 0xFFF1F5F9);
            }
        }

        // Draw Widgets
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // 5. Draw Tooltip Box
        OptionEntry hovered = findHoveredOption(mouseX, mouseY);
        if (hovered != null) {
            renderTooltip(graphics, mouseX, mouseY, hovered);
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

        // Background (0xFF09070F), smooth thin border (0xFF3B0764)
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF09070F);
        graphics.outline(boxX, boxY, boxWidth, boxHeight, 0xFF3B0764);

        int textY = boxY + 6;
        for (FormattedCharSequence line : lines) {
            graphics.text(this.font, line, boxX + 6, textY, 0xFFF1F5F9);
            textY += 10;
        }

        textY += 2;
        graphics.text(this.font, impactLabel, boxX + 6, textY, 0xFF64748B);
        graphics.text(this.font, impactValue, boxX + 6 + labelWidth, textY, impactColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (event.button() == 0) {
            // Sidebar tab click check (X: 0 to 110, Y: 40 + (index * 24) to + 20)
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

        // Scroller bounds click check
        if (this.scroller != null) {
            if (mx >= this.width - 16 && mx <= this.width && my >= 40 && my <= this.height - 10) {
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
        this.minecraft.options.save();
        this.minecraft.gui.setScreen(this.lastScreen);
    }
}
