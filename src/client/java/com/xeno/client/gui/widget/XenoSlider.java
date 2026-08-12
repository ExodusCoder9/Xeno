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

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

public class XenoSlider extends AbstractButton {
    public static final int DEFAULT_WIDTH = 130;
    public static final int DEFAULT_HEIGHT = 20;
    private static final int HANDLE_WIDTH = 6;

    private static final int TRACK_BG        = 0xFF141126;
    private static final int TRACK_BORDER    = 0xFFA855F7;
    private static final int HANDLE_COLOR    = 0xFFA855F7;
    private static final int HANDLE_HOVER    = 0xFFC085FF;
    private static final int TEXT_NORMAL     = 0xFF9333EA;
    private static final int TEXT_HOVER      = 0xFFF3E8FF;

    private double value;
    private boolean dragging;
    private final OnValueChange onChange;

    @FunctionalInterface
    public interface OnValueChange {
        void onChange(XenoSlider slider);
    }

    public XenoSlider(int x, int y, int width, int height, Component message, double initialValue, OnValueChange onChange) {
        super(x, y, width, height, message);
        this.value = Mth.clamp(initialValue, 0.0, 1.0);
        this.onChange = onChange;
    }

    public static XenoSlider builder(int x, int y, Component message, double initialValue, OnValueChange onChange) {
        return new XenoSlider(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT, message, initialValue, onChange);
    }

    public double getDoubleValue() {
        return this.value;
    }

    public void setDoubleValue(double value) {
        this.value = Mth.clamp(value, 0.0, 1.0);
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        this.dragging = this.active;
        this.setValueFromMouse(event);
    }

    @Override
    protected void onDrag(@NonNull MouseButtonEvent event, double dx, double dy) {
        this.setValueFromMouse(event);
    }

    @Override
    public void onRelease(@NonNull MouseButtonEvent event) {
        this.dragging = false;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (this.active && this.isFocused()) {
            boolean left = event.isLeft();
            boolean right = event.isRight();
            if (left || right) {
                double step = 1.0 / (this.width - HANDLE_WIDTH);
                this.setValue(this.value + (left ? -step : step));
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void setValueFromMouse(MouseButtonEvent event) {
        this.setValue((event.x() - (this.getX() + HANDLE_WIDTH / 2.0)) / (this.width - HANDLE_WIDTH));
    }

    private void setValue(double newValue) {
        double old = this.value;
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        if (old != this.value && this.onChange != null) {
            this.onChange.onChange(this);
        }
    }

    @Override
    protected void handleCursor(@NonNull GuiGraphicsExtractor graphics) {
        if (this.isHovered()) {
            graphics.requestCursor(this.dragging ? CursorTypes.RESIZE_EW : CursorTypes.POINTING_HAND);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hovered = this.isHoveredOrFocused();

        graphics.fill(x, y, x + w, y + h, TRACK_BG);
        graphics.outline(x, y, w, h, TRACK_BORDER);

        int handleX = x + (int) (this.value * (w - HANDLE_WIDTH));
        int handleColor = (hovered || this.dragging) ? HANDLE_HOVER : HANDLE_COLOR;
        graphics.fill(handleX, y, handleX + HANDLE_WIDTH, y + h, handleColor);

        int textColor = hovered ? TEXT_HOVER : TEXT_NORMAL;
        graphics.centeredText(
                Minecraft.getInstance().font,
                this.getMessage(),
                x + w / 2,
                y + (h - 8) / 2,
                textColor
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.slider", this.getMessage()));
    }
}