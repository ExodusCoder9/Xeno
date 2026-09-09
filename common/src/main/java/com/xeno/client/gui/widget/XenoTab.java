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

package com.xeno.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

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

    private boolean selected;
    private final OnTabPress onPress;

    @FunctionalInterface
    public interface OnTabPress {
        void onPress(XenoTab tab);
    }

    public XenoTab(int x, int y, int width, int height, Component message, boolean selected, OnTabPress onPress) {
        super(x, y, width, height, message);
        this.selected = selected;
        this.onPress = onPress;
    }

    public static XenoTab builder(int x, int y, Component message, boolean selected, OnTabPress onPress) {
        return new XenoTab(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT, message, selected, onPress);
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        if (this.onPress != null) {
            this.onPress.onPress(this);
        }
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hovered = this.isHoveredOrFocused();

        int bgColor;
        int textColor;

        if (this.selected) {
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

        if (this.selected) {
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
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.@NonNull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}