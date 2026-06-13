package com.xeno.config.gui;

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


import com.mojang.blaze3d.opengl.GlStateManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.gui.widget.OptionWidget;
import com.xeno.config.gui.widget.VAbstractWidget;
import com.xeno.config.option.Option;
import com.xeno.vulkan.util.ColorUtil;
import org.jetbrains.annotations.Nullable;

public class VOptionList extends GuiElement {
   private final List<VOptionList.Entry> children = new ObjectArrayList();
   boolean scrolling = false;
   float scrollAmount = 0.0F;
   int itemWidth;
   int totalItemHeight;
   int itemHeight;
   int itemMargin;
   int listLength = 0;
   VOptionList.Entry focused;

   public VOptionList(int x, int y, int width, int height, int itemHeight) {
      this.setPosition(x, y, width, height);
      this.width = width;
      this.height = height;
      this.itemWidth = this.width - 7;
      this.itemHeight = itemHeight;
      this.itemMargin = 3;
      this.totalItemHeight = this.itemHeight + this.itemMargin;
   }

   public void addButton(OptionWidget<?> widget) {
      this.addEntry(new VOptionList.Entry(widget, this.itemMargin, null));
   }

   public void addAll(OptionBlock[] blocks) {
      for (OptionBlock block : blocks) {
         int x0 = this.x;
         int width = this.itemWidth;
         int height = this.itemHeight;
         String title = block.title();
         if (title != null && !title.isEmpty()) {
            this.addEntry(new VOptionList.Entry(null, 8, title));
         }

         Option<?>[] options = block.options();

         for (Option<?> option : options) {
            int margin = this.itemMargin;
            OptionWidget<?> widget = option.getWidget();
            widget.setDimensions(x0, 0, width, height);
            this.addEntry(new VOptionList.Entry(widget, margin, null));
         }

         this.addEntry(new VOptionList.Entry(null, 12, null));
      }
   }

   private void addEntry(VOptionList.Entry entry) {
      this.children.add(entry);
      this.listLength = this.listLength + entry.getTotalHeight();
   }

   public void clearEntries() {
      this.listLength = 0;
      this.children.clear();
   }

   protected void updateScrollingState(double mouseX, int button) {
      this.scrolling = button == 0 && mouseX >= this.getScrollbarPosition() && mouseX < this.getScrollbarPosition() + 6;
   }

   protected float getScrollAmount() {
      return this.scrollAmount;
   }

   public void setScrollAmount(double d) {
      this.scrollAmount = (float)Mth.clamp(d, 0.0, this.getMaxScroll());
   }

   private int getItemCount() {
      return this.children.size();
   }

   GuiEventListener getFocused() {
      return this.focused;
   }

   void setFocused(VOptionList.Entry focussed) {
      this.focused = focussed;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
      this.updateScrollingState(event.x(), event.button());
      if (this.isMouseOver(event.x(), event.y())) {
         VOptionList.Entry entry = this.getEntryAtPos(event.x(), event.y());
         if (entry != null && entry.mouseClicked(event, bl)) {
            this.setFocused(entry);
            entry.setFocused(true);
            return true;
         } else {
            return event.button() == 0;
         }
      } else {
         return false;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.isValidClickButton(event.button())) {
         VOptionList.Entry entry = this.getEntryAtPos(event.x(), event.y());
         if (entry != null && entry.mouseReleased(event)) {
            entry.setFocused(false);
            this.setFocused(null);
            return true;
         }
      }

      return false;
   }

   public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
      if (event.button() != 0) {
         return false;
      }

      if (this.getFocused() != null) {
         return this.getFocused().mouseDragged(event, deltaX, deltaY);
      }

      if (!this.scrolling) {
         return false;
      }

      double maxScroll = this.getMaxScroll();
      if (event.y() < this.y) {
         this.setScrollAmount(0.0);
      } else if (event.y() > this.getBottom()) {
         this.setScrollAmount(maxScroll);
      } else if (maxScroll > 0.0) {
         double barHeight = (double)this.height * this.height / this.getTotalLength();
         double scrollFactor = Math.max(1.0, maxScroll / (this.height - barHeight));
         this.setScrollAmount(this.getScrollAmount() + deltaY * scrollFactor);
      }

      return true;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double xScroll, double yScroll) {
      this.setScrollAmount(this.getScrollAmount() - yScroll * this.totalItemHeight / 2.0);
      return true;
   }

   public int getMaxScroll() {
      return Math.max(0, this.getTotalLength() - this.height);
   }

   protected int getTotalLength() {
      return this.listLength;
   }

   public int getBottom() {
      return this.y + this.height;
   }

   @Nullable
   protected VOptionList.Entry getEntryAtPos(double x, double y) {
      int x0 = this.x;
      if (!(x > this.getScrollbarPosition()) && !(x < x0)) {
         for (VOptionList.Entry entry : this.children) {
            VAbstractWidget widget = entry.widget;
            if (widget != null && y >= widget.y && y <= widget.y + widget.height) {
               return entry;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Override
   public void updateState(double mX, double mY) {
      if (this.focused == null) {
         super.updateState(mX, mY);
      }
   }

   public void renderWidget(int mouseX, int mouseY) {
      GuiRenderer.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);
      this.renderList(mouseX, mouseY);
      GuiRenderer.disableScissor();
      int maxScroll = this.getMaxScroll();
      if (maxScroll > 0) {
         GlStateManager._enableBlend();
         int height = this.getHeight();
         int totalLength = this.getTotalLength();
         int barHeight = (int)((float)(height * height) / totalLength);
         barHeight = Mth.clamp(barHeight, 32, height - 8);
         int scrollAmount = (int)this.getScrollAmount();
         int barY = scrollAmount * (height - barHeight) / maxScroll + this.getY();
         barY = Math.max(barY, this.getY());
         int scrollbarPosition = this.getScrollbarPosition();
         int thickness = 3;
          int backgroundColor = ColorUtil.ARGB.pack(0.5F, 0.25F, 0.5F, 0.15F);
          GuiRenderer.fill(scrollbarPosition, this.getY(), scrollbarPosition + thickness, this.getY() + height, backgroundColor);
          int barColor = ColorUtil.ARGB.pack(0.6F, 0.2F, 0.8F, 0.6F);
         GuiRenderer.fill(scrollbarPosition, barY, scrollbarPosition + thickness, barY + barHeight, barColor);
      }
   }

   protected int getScrollbarPosition() {
      return this.x + this.width;
   }

   public VAbstractWidget getHoveredWidget(double mouseX, double mouseY) {
      if (this.focused != null) {
         return this.focused.widget;
      }

      if (!this.isMouseOver(mouseX, mouseY)) {
         return null;
      }

      for (VOptionList.Entry entry : this.children) {
         VAbstractWidget widget = entry.widget;
         if (widget != null && widget.isMouseOver(mouseX, mouseY)) {
            return widget;
         }
      }

      return null;
   }

   protected void renderList(int mouseX, int mouseY) {
      int itemCount = this.getItemCount();
      int rowTop = this.y - (int)this.getScrollAmount();

      for (int j = 0; j < itemCount; j++) {
         VOptionList.Entry entry = this.getEntry(j);
         if (rowTop + entry.getTotalHeight() >= this.y && rowTop <= this.y + this.height) {
            boolean updateState = this.focused == null;
            entry.render(rowTop, mouseX, mouseY, updateState, this.x);
         }

         rowTop += entry.getTotalHeight();
      }
   }

   private VOptionList.Entry getEntry(int j) {
      return this.children.get(j);
   }

   protected boolean isValidClickButton(int i) {
      return i == 0;
   }

   protected static class Entry implements GuiEventListener {
      final VAbstractWidget widget;
      final int margin;
      final String headerTitle;

      private Entry(OptionWidget<?> widget, int margin, String headerTitle) {
         this.widget = widget;
         this.margin = margin;
         this.headerTitle = headerTitle;
      }

      public void render(int y, int mouseX, int mouseY, boolean updateState, int listX) {
         if (this.headerTitle != null && !this.headerTitle.isEmpty()) {
            int headerY = y + 4;
            GuiRenderer.drawString(Minecraft.getInstance().font, Component.literal(this.headerTitle), listX + 8, headerY, -1);
         } else if (this.widget != null) {
            this.widget.y = y;
            if (updateState) {
               this.widget.updateState(mouseX, mouseY);
            }

            this.widget.render(mouseX, mouseY);
         }
      }

      public int getTotalHeight() {
         if (this.headerTitle != null && !this.headerTitle.isEmpty()) {
            return 9 + this.margin;
         } else {
            return this.widget != null ? this.widget.height + this.margin : this.margin;
         }
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
         return this.widget == null ? false : this.widget.mouseClicked(event, bl);
      }

      public boolean mouseReleased(MouseButtonEvent event) {
         return this.widget == null ? false : this.widget.mouseReleased(event);
      }

      public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
         return this.widget == null ? false : this.widget.mouseDragged(event, deltaX, deltaY);
      }

      public boolean isFocused() {
         return false;
      }

      public void setFocused(boolean bl) {
         if (this.widget != null) {
            this.widget.setFocused(bl);
         }
      }
   }
}
