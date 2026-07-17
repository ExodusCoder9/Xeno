package com.xeno.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A tab-style button with left-aligned text and an optional active indicator bar.
 * Used for category/navigation tabs. Zero vanilla textures.
 */
public class XenoTab extends AbstractButton {
    public static final int DEFAULT_WIDTH = 130;
    public static final int DEFAULT_HEIGHT = 24;
    private static final int ACCENT_BAR_WIDTH = 2;

    private static final int BG_NORMAL    = 0xFF141126;
    private static final int BG_HOVER     = 0x33A855F7;
    private static final int BG_ACTIVE    = 0x1AA855F7;
    private static final int ACCENT_COLOR = 0xFFA855F7;
    private static final int TEXT_NORMAL  = 0xFF9333EA;
    private static final int TEXT_HOVER   = 0xFFF3E8FF;
    private static final int TEXT_ACTIVE  = 0xFFF3E8FF;

    private static final int TEXT_PADDING_LEFT = 10;

    private boolean active;
    private final OnTabPress onPress;

    @FunctionalInterface
    public interface OnTabPress {
        void onPress(XenoTab tab);
    }

    public XenoTab(int x, int y, int width, int height, Component message, boolean active, OnTabPress onPress) {
        super(x, y, width, height, message);
        this.active = active;
        this.onPress = onPress;
    }

    public static XenoTab builder(int x, int y, Component message, boolean active, OnTabPress onPress) {
        return new XenoTab(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT, message, active, onPress);
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (this.onPress != null) {
            this.onPress.onPress(this);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hovered = this.isHoveredOrFocused();

        int bgColor;
        int textColor;

        if (this.active) {
            bgColor = BG_ACTIVE;
            textColor = TEXT_ACTIVE;
        } else if (hovered) {
            bgColor = BG_HOVER;
            textColor = TEXT_HOVER;
        } else {
            bgColor = BG_NORMAL;
            textColor = TEXT_NORMAL;
        }

        graphics.fill(x, y, x + w, y + h, bgColor);

        if (this.active) {
            graphics.fill(x, y, x + ACCENT_BAR_WIDTH, y + h, ACCENT_COLOR);
        }

        graphics.text(
                Minecraft.getInstance().font,
                this.getMessage(),
                x + TEXT_PADDING_LEFT,
                y + (h - 8) / 2,
                textColor
        );
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
