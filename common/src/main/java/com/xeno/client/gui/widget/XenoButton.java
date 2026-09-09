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

public class XenoButton extends AbstractButton {
    public static final int DEFAULT_WIDTH = 200;
    public static final int DEFAULT_HEIGHT = 20;

    private static final int BG_NORMAL      = 0xFF141126;
    private static final int BG_HOVER       = 0x33A855F7;
    private static final int BORDER_COLOR   = 0xFFA855F7;
    private static final int TEXT_NORMAL    = 0xFF9333EA;
    private static final int TEXT_HOVER     = 0xFFF3E8FF;

    private final OnPress onPress;

    @FunctionalInterface
    public interface OnPress {
        void onPress(XenoButton button);
    }

    public XenoButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public static XenoButton builder(int x, int y, Component message, OnPress onPress) {
        return new XenoButton(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT, message, onPress);
    }

    public static XenoButton builder(int x, int y, int width, Component message, OnPress onPress) {
        return new XenoButton(x, y, width, DEFAULT_HEIGHT, message, onPress);
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
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

        int bgColor = hovered ? BG_HOVER : BG_NORMAL;
        int textColor = hovered ? TEXT_HOVER : TEXT_NORMAL;

        graphics.fill(x, y, x + w, y + h, bgColor);

        if (hovered) {
            graphics.outline(x, y, w, h, BORDER_COLOR);
        }

        graphics.centeredText(
                Minecraft.getInstance().font,
                this.getMessage(),
                x + w / 2,
                y + (h - 8) / 2,
                textColor
        );
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.@NonNull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}