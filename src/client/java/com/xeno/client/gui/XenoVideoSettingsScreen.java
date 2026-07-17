package com.xeno.client.gui;

import com.xeno.client.gui.widget.XenoButton;
import com.xeno.client.gui.widget.XenoScroller;
import com.xeno.client.gui.widget.XenoSlider;
import com.xeno.client.gui.widget.XenoTab;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class XenoVideoSettingsScreen extends Screen {

    public enum PerformanceImpact {
        LOW("Low", 0xFF55FF55),
        MEDIUM("Medium", 0xFFFFFF55),
        HIGH("High", 0xFFA855F7);

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

    private record OptionEntry(AbstractWidget widget, Component description, PerformanceImpact impact) {
    }

    private static final Component TITLE = Component.translatable("options.videoTitle");
    private static final Component DONE = Component.translatable("gui.done");

    private static final int SIDEBAR_X = 10;
    private static final int SIDEBAR_WIDTH = 110;
    private static final int CONTENT_X = 130;
    private static final int OPTION_WIDTH = 200;
    private static final int ROW_HEIGHT = 26;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_SPACING = 2;
    private static final int TITLE_AREA_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 36;

    private static final int BG_PANEL    = 0xCC1A1133;
    private static final int BG_OVERLAY  = 0xB0141126;
    private static final int ACCENT      = 0xFFA855F7;
    private static final int TEXT_TITLE  = 0xFFF3E8FF;

    private static final int TOOLTIP_BG     = 0xB00D0B18;
    private static final int TOOLTIP_BORDER = 0xFFA855F7;
    private static final int TOOLTIP_TEXT   = 0xFF9333EA;

    private final Screen lastScreen;
    private final Options options;

    private final List<XenoTab> tabs = new ArrayList<>();
    private XenoTab activeTab;

    private final List<OptionEntry> generalOptions = new ArrayList<>();
    private final List<OptionEntry> qualityOptions = new ArrayList<>();
    private final List<OptionEntry> performanceOptions = new ArrayList<>();
    private final List<OptionEntry> advancedOptions = new ArrayList<>();
    private final List<List<OptionEntry>> allTabOptions = new ArrayList<>();
    private List<OptionEntry> currentTabOptions = new ArrayList<>();

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

        int tabY = TITLE_AREA_HEIGHT + 5;

        XenoTab tabGeneral = XenoTab.builder(SIDEBAR_X, tabY, Component.literal("General"), true, _ -> switchTab(0));
        tabY += TAB_HEIGHT + TAB_SPACING;
        XenoTab tabQuality = XenoTab.builder(SIDEBAR_X, tabY, Component.literal("Quality"), false, _ -> switchTab(1));
        tabY += TAB_HEIGHT + TAB_SPACING;
        XenoTab tabPerformance = XenoTab.builder(SIDEBAR_X, tabY, Component.literal("Performance"), false, _ -> switchTab(2));
        tabY += TAB_HEIGHT + TAB_SPACING;
        XenoTab tabAdvanced = XenoTab.builder(SIDEBAR_X, tabY, Component.literal("Advanced"), false, _ -> switchTab(3));

        this.tabs.add(tabGeneral);
        this.tabs.add(tabQuality);
        this.tabs.add(tabPerformance);
        this.tabs.add(tabAdvanced);

        for (XenoTab tab : this.tabs) {
            this.addRenderableWidget(tab);
        }

        populateOptions();

        this.activeTab = tabGeneral;
        this.currentTabOptions = this.generalOptions;

        this.scroller = XenoScroller.vertical(
                this.width - 16, TITLE_AREA_HEIGHT + 5,
                this.height - TITLE_AREA_HEIGHT - FOOTER_HEIGHT - 10
        );
        this.addRenderableWidget(this.scroller);

        int doneY = this.height - FOOTER_HEIGHT + 8;
        this.addRenderableWidget(new XenoButton(
                this.width / 2 - 100, doneY, 200, 20, DONE, _ -> this.onClose()
        ));

        updateScrollerRange();
        updateWidgetPositions();
    }

    private void populateOptions() {
        this.generalOptions.clear();
        this.qualityOptions.clear();
        this.performanceOptions.clear();
        this.advancedOptions.clear();

        addSlider(this.generalOptions, this.options.renderDistance(), "Render Distance", 2, 32,
                "Determines how far chunks are rendered around the player. Higher values increase visibility but cost more performance.",
                PerformanceImpact.MEDIUM);
        addSlider(this.generalOptions, this.options.simulationDistance(), "Simulation Distance", 2, 32,
                "Controls how far entity simulations and block ticks occur. Lower values reduce CPU load.",
                PerformanceImpact.MEDIUM);
        addSlider(this.generalOptions, this.options.framerateLimit(), "FPS Limit", 10, 260,
                "Caps the maximum frames per second. Lower values reduce GPU load and power consumption.",
                PerformanceImpact.LOW);
        addToggle(this.generalOptions, this.options.enableVsync(), "VSync",
                "Synchronizes frame output with monitor refresh rate. Reduces screen tearing but may add input lag.",
                PerformanceImpact.LOW);

        addCycle(this.qualityOptions, this.options.graphicsPreset(), "Graphics",
                "Controls visual quality level. Fast disables effects, Fancy enables them, Fabulous adds translucency sorting.",
                PerformanceImpact.HIGH);
        addCycle(this.qualityOptions, this.options.cloudStatus(), "Clouds",
                "Controls cloud rendering. Off disables clouds completely for a performance boost.",
                PerformanceImpact.LOW);
        addCycle(this.qualityOptions, this.options.particles(), "Particles",
                "Controls particle density. Fewer particles reduce CPU overhead during combat and weather.",
                PerformanceImpact.MEDIUM);
        addToggle(this.qualityOptions, this.options.entityShadows(), "Entity Shadows",
                "Renders simple circular shadows under entities. Disabling saves minor GPU time.",
                PerformanceImpact.LOW);
        addSlider(this.qualityOptions, this.options.biomeBlendRadius(), "Biome Blend", 0, 7,
                "Controls color blending radius between biomes. Lower values are cheaper.",
                PerformanceImpact.LOW);

        addSlider(this.performanceOptions, this.options.mipmapLevels(), "Mipmap Levels", 0, 4,
                "Controls texture mipmap quality. Lower values use less VRAM but may cause texture shimmer.",
                PerformanceImpact.LOW);

        this.allTabOptions.clear();
        this.allTabOptions.add(this.generalOptions);
        this.allTabOptions.add(this.qualityOptions);
        this.allTabOptions.add(this.performanceOptions);
        this.allTabOptions.add(this.advancedOptions);

        for (List<OptionEntry> tab : this.allTabOptions) {
            for (OptionEntry entry : tab) {
                this.addRenderableWidget(entry.widget());
            }
        }
    }

    private void addSlider(List<OptionEntry> list, OptionInstance<Integer> option, String name, int min, int max, String desc, PerformanceImpact impact) {
        int index = list.size();
        int y = TITLE_AREA_HEIGHT + 10 + index * ROW_HEIGHT;
        int current = (Integer) option.get();
        double sliderValue = max > min ? (double) (current - min) / (max - min) : 0.0;
        Component msg = Component.literal(name + ": " + current);

        XenoSlider slider = new XenoSlider(CONTENT_X, y, OPTION_WIDTH, 20, msg, sliderValue, s -> {
            int value = min + (int) Math.round(s.getDoubleValue() * (max - min));
            option.set(value);
            s.setMessage(Component.literal(name + ": " + value));
        });

        list.add(new OptionEntry(slider, Component.literal(desc), impact));
    }

    private void addToggle(List<OptionEntry> list, OptionInstance<Boolean> option, String name, String desc, PerformanceImpact impact) {
        int index = list.size();
        int y = TITLE_AREA_HEIGHT + 10 + index * ROW_HEIGHT;
        boolean current = (Boolean) option.get();
        Component msg = Component.literal(name + ": " + (current ? "ON" : "OFF"));

        XenoButton[] holder = new XenoButton[1];
        holder[0] = new XenoButton(CONTENT_X, y, OPTION_WIDTH, 20, msg, _ -> {
            boolean val = (Boolean) option.get();
            option.set(!val);
            holder[0].setMessage(Component.literal(name + ": " + (!val ? "ON" : "OFF")));
        });

        list.add(new OptionEntry(holder[0], Component.literal(desc), impact));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addCycle(List<OptionEntry> list, OptionInstance option, String name, String desc, PerformanceImpact impact) {
        int index = list.size();
        int y = TITLE_AREA_HEIGHT + 10 + index * ROW_HEIGHT;
        Enum<?> current = (Enum<?>) option.get();
        Component msg = Component.literal(name + ": " + capitalize(current.name()));

        XenoButton[] holder = new XenoButton[1];
        holder[0] = new XenoButton(CONTENT_X, y, OPTION_WIDTH, 20, msg, _ -> {
            Enum<?> val = (Enum<?>) option.get();
            Object[] constants = val.getDeclaringClass().getEnumConstants();
            int next = (val.ordinal() + 1) % constants.length;
            option.set(constants[next]);
            holder[0].setMessage(Component.literal(name + ": " + capitalize(((Enum<?>) constants[next]).name())));
        });

        list.add(new OptionEntry(holder[0], Component.literal(desc), impact));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private void switchTab(int index) {
        for (int i = 0; i < this.tabs.size(); i++) {
            this.tabs.get(i).setActive(i == index);
        }
        this.activeTab = this.tabs.get(index);
        this.currentTabOptions = this.allTabOptions.get(index);
        this.scrollOffset = 0;
        this.targetScrollOffset = 0;
        if (this.scroller != null) {
            this.scroller.setScrollAmount(0);
        }
        updateScrollerRange();
        updateWidgetPositions();
    }

    private void updateScrollerRange() {
        float contentHeight = this.currentTabOptions.size() * ROW_HEIGHT;
        float visibleHeight = this.height - TITLE_AREA_HEIGHT - FOOTER_HEIGHT;
        this.scroller.setScrollRange(contentHeight, visibleHeight);
    }

    private void updateWidgetPositions() {
        for (int tabIdx = 0; tabIdx < this.allTabOptions.size(); tabIdx++) {
            List<OptionEntry> options = this.allTabOptions.get(tabIdx);
            boolean visible = (tabIdx == this.tabs.indexOf(this.activeTab));
            for (int i = 0; i < options.size(); i++) {
                AbstractWidget widget = options.get(i).widget();
                int yPos = TITLE_AREA_HEIGHT + 10 + i * ROW_HEIGHT - (int) this.scrollOffset;
                widget.visible = visible;
                widget.active = visible;
                if (visible) {
                    widget.setPosition(CONTENT_X, yPos);
                }
            }
        }
    }

    private OptionEntry findHoveredOption() {
        for (OptionEntry entry : this.currentTabOptions) {
            if (entry.widget().isHovered()) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.scrollOffset += (this.targetScrollOffset - this.scrollOffset) * 0.25F;

        if (this.scroller != null) {
            this.targetScrollOffset = this.scroller.getScrollAmount();
        }

        updateWidgetPositions();

        graphics.fill(0, 0, this.width, this.height, BG_OVERLAY);

        graphics.fill(
                SIDEBAR_X - 4, TITLE_AREA_HEIGHT,
                SIDEBAR_X + SIDEBAR_WIDTH + 4, this.height - FOOTER_HEIGHT,
                BG_PANEL
        );

        graphics.fill(
                CONTENT_X - 8, TITLE_AREA_HEIGHT,
                CONTENT_X + OPTION_WIDTH + 8, this.height - FOOTER_HEIGHT,
                BG_PANEL
        );

        graphics.fill(
                CONTENT_X - 8, TITLE_AREA_HEIGHT,
                CONTENT_X + OPTION_WIDTH + 8, TITLE_AREA_HEIGHT + 1,
                ACCENT
        );

        graphics.centeredText(this.font, this.getTitle(), this.width / 2, 14, TEXT_TITLE);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        OptionEntry hovered = findHoveredOption();
        if (hovered != null) {
            renderTooltip(graphics, mouseX, mouseY, hovered);
        }
    }

    private void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, OptionEntry entry) {
        Component desc = entry.description();
        Component impactLabel = Component.literal("Performance Impact: ");
        Component impactText = entry.impact().getText();
        int impactColor = entry.impact().getColor();

        int descWidth = this.font.width(desc);
        int impactWidth = this.font.width(impactLabel) + this.font.width(impactText);
        int boxWidth = Math.max(descWidth, impactWidth) + 10;
        int boxHeight = 30;

        int boxX = mouseX + 12;
        int boxY = mouseY - boxHeight - 4;

        if (boxX + boxWidth > this.width) {
            boxX = mouseX - boxWidth - 4;
        }
        if (boxY < 0) {
            boxY = mouseY + 12;
        }

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, TOOLTIP_BG);
        graphics.outline(boxX, boxY, boxWidth, boxHeight, TOOLTIP_BORDER);

        graphics.text(this.font, desc, boxX + 5, boxY + 5, TOOLTIP_TEXT);
        graphics.text(this.font, impactLabel, boxX + 5, boxY + 17, TOOLTIP_TEXT);
        graphics.text(this.font, impactText, boxX + 5 + this.font.width(impactLabel), boxY + 17, impactColor);
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
