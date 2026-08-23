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

import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

public final class XenoScroller extends AbstractContainerEventHandler implements Renderable, NarratableEntry {

    public static final int DEFAULT_THUMB_WIDTH = 6;
    public static final int DEFAULT_THUMB_HEIGHT = 32;

    private static final int TRACK_COLOR       = 0x40A855F7;
    private static final int THUMB_COLOR        = 0xFFA855F7;
    private static final int THUMB_HOVER_COLOR  = 0xFFC085FF;

    private int x;
    private int y;
    private int trackHeight;
    private int thumbWidth;
    private int thumbHeight;

    private float scrollAmount;
    private float maxScroll;
    private boolean dragging;
    private double dragOffsetY;
    private boolean thumbHovered;

    public XenoScroller(int x, int y, int trackHeight, int thumbWidth, int thumbHeight) {
        this.x = x;
        this.y = y;
        this.trackHeight = trackHeight;
        this.thumbWidth = thumbWidth;
        this.thumbHeight = thumbHeight;
        this.scrollAmount = 0.0F;
        this.maxScroll = 1.0F;
    }

    public static XenoScroller vertical(int x, int y, int trackHeight) {
        return new XenoScroller(x, y, trackHeight, DEFAULT_THUMB_WIDTH, DEFAULT_THUMB_HEIGHT);
    }

    public void setScrollRange(float contentHeight, float visibleHeight) {
        this.maxScroll = Math.max(0.0F, contentHeight - visibleHeight);
        if (this.scrollAmount > this.maxScroll) {
            this.scrollAmount = this.maxScroll;
        }
    }

    public float getScrollAmount() {
        return this.scrollAmount;
    }

    public void setScrollAmount(float amount) {
        this.scrollAmount = Math.max(0.0F, Math.min(amount, this.maxScroll));
    }

    public float getScrollFraction() {
        return this.maxScroll > 0.0F ? this.scrollAmount / this.maxScroll : 0.0F;
    }

    public void applyScrollDelta(double scrollDelta) {
        this.scrollAmount = Mth.clamp(
                this.scrollAmount - (float) (scrollDelta * 18.0),
                0.0F,
                this.maxScroll
        );
    }

    public int getThumbY() {
        int usableTrack = this.trackHeight - this.thumbHeight;
        return this.y + Math.round(usableTrack * getScrollFraction());
    }

    public int getX() {
        return this.x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getTrackHeight() {
        return this.trackHeight;
    }

    public void setTrackHeight(int height) {
        this.trackHeight = height;
    }

    public boolean isThumbHovered() {
        return this.thumbHovered;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int trackX = this.x + (this.thumbWidth - 1) / 2;
        graphics.fill(trackX, this.y, trackX + 1, this.y + this.trackHeight, TRACK_COLOR);

        int thumbX = this.x;
        int thumbY = this.getThumbY();
        this.thumbHovered = mouseX >= thumbX && mouseX < thumbX + this.thumbWidth
                && mouseY >= thumbY && mouseY < thumbY + this.thumbHeight;

        int thumbColor = this.thumbHovered || this.dragging ? THUMB_HOVER_COLOR : THUMB_COLOR;
        graphics.fill(thumbX, thumbY, thumbX + this.thumbWidth, thumbY + this.thumbHeight, thumbColor);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        if (event.buttonInfo().button() != 0) {
            return false;
        }

        int thumbX = this.x;
        int thumbY = this.getThumbY();

        if (event.x() >= thumbX && event.x() < thumbX + this.thumbWidth
                && event.y() >= thumbY && event.y() < thumbY + this.thumbHeight) {
            this.dragging = true;
            this.dragOffsetY = event.y() - thumbY;
            return true;
        }

        int clickY = (int) event.y();
        int trackCenter = this.y + this.thumbHeight / 2;
        if (clickY < trackCenter) {
            this.scrollAmount = Math.max(0.0F, this.scrollAmount - this.thumbHeight);
        } else {
            this.scrollAmount = Math.min(this.maxScroll, this.scrollAmount + this.thumbHeight);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(final @NonNull MouseButtonEvent event, final double dx, final double dy) {
        if (!this.dragging || event.buttonInfo().button() != 0) {
            return false;
        }

        int usableTrack = this.trackHeight - this.thumbHeight;
        if (usableTrack <= 0) {
            return true;
        }

        double rawY = event.y() - this.y - this.dragOffsetY;
        float fraction = (float) Mth.clamp(rawY / usableTrack, 0.0, 1.0);
        this.scrollAmount = fraction * this.maxScroll;
        return true;
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event) {
        if (event.buttonInfo().button() == 0) {
            this.dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return Collections.emptyList();
    }

    @Override
    public @NonNull NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public int getTabOrderGroup() {
        return 0;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("narration.scroll_bar"));
    }
}