package com.xeno.config.gui.widget;

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


import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.gui.util.VGuiConstants;
import com.xeno.vulkan.util.ColorUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class VTextInputWidget extends VAbstractWidget {
   public boolean selected = false;
   Consumer<VTextInputWidget> onSearch;
   private String text;
   private final Component placeholder;
   private int cursorPos = 0;
   private int selectionEnd = 0;
   private long lastBlinkTime = 0L;
   private boolean showCursor = true;
   private static final int CURSOR_BLINK_INTERVAL = 500;

   public VTextInputWidget(int x, int y, int width, int height, Component placeholder, Consumer<VTextInputWidget> onSearch) {
      this.setPosition(x, y, width, height);
      this.placeholder = placeholder;
      this.onSearch = onSearch;
      this.text = "";
   }

   @Override
   public void renderWidget(double mouseX, double mouseY) {
      if (this.isVisible()) {
         boolean hasText = !this.text.isEmpty();
         boolean isFocused = this.focused || this.selected;
         int backgroundColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_BLACK, 0.45F);
         int textColor = hasText ? VGuiConstants.COLOR_WHITE : VGuiConstants.COLOR_GRAY;
         GuiRenderer.fill(this.x, this.y, this.x + this.width, this.y + this.height, backgroundColor);
         this.renderHovering(0, 0);
         if (isFocused && this.cursorPos != this.selectionEnd) {
            int start = Math.min(this.cursorPos, this.selectionEnd);
            int end = Math.max(this.cursorPos, this.selectionEnd);
            String before = this.text.substring(0, start);
            String selected = this.text.substring(start, end);
            int xBefore = this.x + 8 + Minecraft.getInstance().font.width(before);
            int xSelected = Minecraft.getInstance().font.width(selected);
            int selColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_RED, 0.55F);
            GuiRenderer.fill(xBefore, this.y + 4, xBefore + xSelected, this.y + this.height - 4, selColor);
         }

         Component displayText = (Component)(hasText ? Component.literal(this.text) : this.placeholder);
         int var10002 = this.x + 8;
         int var10003 = this.y + (this.height - 8) / 2;
         GuiRenderer.drawString(Minecraft.getInstance().font, displayText, var10002, var10003, textColor | 0xFF000000);
         if (isFocused && this.showCursor) {
            String beforeCursor = this.text.substring(0, this.cursorPos);
            int cursorX = this.x + 8 + Minecraft.getInstance().font.width(beforeCursor);
            GuiRenderer.fill(cursorX, this.y + 6, cursorX + 1, this.y + this.height - 6, VGuiConstants.COLOR_WHITE);
         }

         if (isFocused) {
            int borderColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_RED, 0.8F);
            GuiRenderer.renderBorder(this.x, this.y, this.x + this.width, this.y + this.height, 1, borderColor);
         }

         if (isFocused) {
            long time = Util.getMillis();
            if (time - this.lastBlinkTime > 500L) {
               this.showCursor = !this.showCursor;
               this.lastBlinkTime = time;
            }
         } else {
            this.showCursor = true;
         }
      }
   }

   public boolean keyPressed(KeyEvent keyEvent) {
      if (!this.focused && !this.selected) {
         return false;
      }

      boolean shift = keyEvent.hasShiftDown();
      boolean ctrl = keyEvent.hasControlDown();
      if (keyEvent.key() != 257 && keyEvent.key() != 335) {
         if (this.cursorPos != this.selectionEnd) {
            int start = Math.min(this.cursorPos, this.selectionEnd);
            int end = Math.max(this.cursorPos, this.selectionEnd);
            if (keyEvent.key() == 259 || keyEvent.key() == 261) {
               this.text = this.text.substring(0, start) + this.text.substring(end);
               this.cursorPos = start;
               this.selectionEnd = start;
               this.onSearch.accept(this);
               return true;
            }
         }

         if (keyEvent.key() == 259) {
            if (this.cursorPos > 0) {
               this.text = this.text.substring(0, this.cursorPos - 1) + this.text.substring(this.cursorPos);
               this.cursorPos--;
               this.selectionEnd = this.cursorPos;
               this.onSearch.accept(this);
            }

            return true;
         } else if (keyEvent.key() == 261) {
            if (this.cursorPos < this.text.length()) {
               this.text = this.text.substring(0, this.cursorPos) + this.text.substring(this.cursorPos + 1);
               this.onSearch.accept(this);
            }

            return true;
         } else {
            if (ctrl && keyEvent.key() == 65) {
               this.cursorPos = this.text.length();
               this.selectionEnd = 0;
               return true;
            }

            if (keyEvent.key() == 263) {
               if (this.cursorPos > 0) {
                  this.cursorPos--;
               }

               if (!shift) {
                  this.selectionEnd = this.cursorPos;
               }

               return true;
            } else if (keyEvent.key() == 262) {
               if (this.cursorPos < this.text.length()) {
                  this.cursorPos++;
               }

               if (!shift) {
                  this.selectionEnd = this.cursorPos;
               }

               return true;
            } else {
               String keyName = GLFW.glfwGetKeyName(keyEvent.key(), keyEvent.scancode());
               if (keyName != null && keyName.length() == 1) {
                  char c = keyEvent.hasShiftDown() ? keyName.toUpperCase().charAt(0) : keyName.charAt(0);
                  if (this.cursorPos != this.selectionEnd) {
                     int start = Math.min(this.cursorPos, this.selectionEnd);
                     int end = Math.max(this.cursorPos, this.selectionEnd);
                     this.text = this.text.substring(0, start) + c + this.text.substring(end);
                     this.cursorPos = start + 1;
                  } else {
                     this.text = this.text.substring(0, this.cursorPos) + c + this.text.substring(this.cursorPos);
                     this.cursorPos++;
                  }

                  this.selectionEnd = this.cursorPos;
                  this.onSearch.accept(this);
                  return true;
               } else {
                  return false;
               }
            }
         }
      } else {
         this.onSearch.accept(this);
         return true;
      }
   }

   public String getInput() {
      return this.text;
   }

   public void setInput(String input) {
      this.text = input != null ? input : "";
   }

   public void setSelected(boolean selected) {
      this.selected = selected;
   }

   public boolean isVisible() {
      return this.visible;
   }

   public boolean isActive() {
      return this.active;
   }

   @Override
   public void setActive(boolean active) {
      this.active = active;
   }

   @Nullable
   @Override
   public ComponentPath nextFocusPath(FocusNavigationEvent event) {
      return this.active && this.visible ? super.nextFocusPath(event) : null;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
      if (this.active && this.visible) {
         boolean clicked = this.clicked(event.x(), event.y());
         if (!clicked) {
            this.setFocused(false);
            this.selected = false;
            return false;
         }

         this.setFocused(true);
         this.selected = true;
         int relX = (int)event.x() - (this.x + 8);
         int pos = 0;

         for (int i = 0; i < this.text.length() && Minecraft.getInstance().font.width(this.text.substring(0, i + 1)) <= relX; i++) {
            pos = i + 1;
         }

         this.cursorPos = pos;
         this.selectionEnd = pos;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void setFocused(boolean focused) {
      super.setFocused(focused);
      if (!focused) {
         this.selected = false;
      }
   }
}
