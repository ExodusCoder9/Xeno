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


import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;


import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.gui.util.SearchHelper;
import com.xeno.config.gui.util.VGuiConstants;
import com.xeno.config.gui.widget.ModIconWidget;
import com.xeno.config.gui.widget.VAbstractWidget;
import com.xeno.config.gui.widget.VButtonWidget;
import com.xeno.config.gui.widget.VTextInputWidget;
import com.xeno.config.option.CyclingOption;
import com.xeno.config.option.Option;
import com.xeno.config.option.OptionPage;
import com.xeno.config.option.PerformanceImpact;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.util.ColorUtil;

public class VOptionScreen extends Screen {
   public static final int MARGIN = 10;
   private final Screen parent;
   private final List<ModSettingsEntry> modSettingsEntries;
   private final List<OptionPage> optionPages;
   private OptionPage searchResultsPage;
   private int currentListIdx = 0;
   private boolean isSearchActive = false;
   private int tooltipX;
   private int tooltipY;
   private int tooltipWidth;
   private VButtonWidget applyButton;
   private VButtonWidget undoButton;
   private VTextInputWidget searchField;
   private final List<VAbstractWidget> iconWidgets = Lists.newArrayList();
   private final List<VButtonWidget> pageButtons = Lists.newArrayList();
   private final List<VButtonWidget> buttons = Lists.newArrayList();

   public VOptionScreen(Component title, Screen parent) {
      super(title);
      this.parent = parent;
      this.optionPages = new ArrayList<>();
      this.modSettingsEntries = new ArrayList<>(ModSettingsRegistry.INSTANCE.getModEntries());
   }

   protected void init() {
      this.initOptionsPages();
      if (this.optionPages.isEmpty()) {
         throw new IllegalStateException("Default Options weren't added!");
      }

      int top = 32;
      int bottom = 60;
      int itemHeight = 20;
      int leftMargin = 116;
      int listWidth = Math.min(this.width - leftMargin - 10, 420);
      int listHeight = this.height - top - bottom;
      this.buildLists(leftMargin, top, listWidth, listHeight, itemHeight);
      this.searchField = this.createSearchField();
      int x = leftMargin + listWidth + 6;
      int tooltipWidth = Math.min(this.width - x - 10, 420);
      int y = top + itemHeight + 6;
      if (tooltipWidth < 200) {
         x = leftMargin + 3;
         tooltipWidth = listWidth;
         y = this.height - bottom + 10;
      }

      this.tooltipX = x;
      this.tooltipY = y;
      this.tooltipWidth = tooltipWidth;
      this.buildPage();
      this.applyButton.active = false;
      this.undoButton.visible = false;
   }

   private void initOptionsPages() {
      this.optionPages.clear();

      for (ModSettingsEntry modPageSet : this.modSettingsEntries) {
         modPageSet.initPages();
         this.optionPages.addAll(modPageSet.getPages());
      }
   }

    private VTextInputWidget createSearchField() {
       int rightMargin = 10;
       int topBarRight = this.width - rightMargin;

       int leftMargin = 116;
      int width = Math.min(topBarRight - leftMargin - 4, 413);
      return new VTextInputWidget(
         leftMargin, 4, width, 20, Component.translatable("xeno.options.searchFieldPlaceholder"), widget -> this.performSearch(widget.getInput())
      );
   }

   private void buildLists(int left, int top, int listWidth, int listHeight, int itemHeight) {
      for (OptionPage page : this.optionPages) {
         page.createList(left, top, listWidth, listHeight, itemHeight);
         page.updateOptionStates();
      }
   }

   private void performSearch(String query) {
      if (query != null && !query.trim().isEmpty()) {
         String searchTerm = query.toLowerCase().trim();
         List<OptionBlock> searchResults = new ArrayList<>();

         for (OptionPage page : this.optionPages) {
            List<Option<?>> matchingOptions = new ArrayList<>();

            for (OptionBlock block : page.optionBlocks) {
               for (Option<?> option : block.options()) {
                  boolean matches = false;
                  String optionName = option.getName().getString().toLowerCase();
                  String optionTooltip = option.getTooltip() != null ? option.getTooltip().getString().toLowerCase() : "";
                  String displayedValue = option.getDisplayedValue().getString().toLowerCase();
                  if (optionName.contains(searchTerm) || optionTooltip.contains(searchTerm) || displayedValue.contains(searchTerm)) {
                     matches = true;
                  } else if (option instanceof CyclingOption<?> cycling && SearchHelper.matchesAnyValue(cycling, searchTerm)) {
                     matches = true;
                  }

                  if (matches) {
                     matchingOptions.add(option);
                  }
               }
            }

            if (!matchingOptions.isEmpty()) {
               searchResults.add(new OptionBlock("§l" + page.name, matchingOptions.toArray(new Option[0])));
               searchResults.add(new OptionBlock("", new Option[0]));
            }
         }

         this.searchResultsPage = new OptionPage("Search Results", searchResults.toArray(new OptionBlock[0]));
         int top = 32;
         int bottom = 60;
         int itemHeight = 20;
         int leftMargin = 116;
         int listWidth = Math.min(this.width - leftMargin - 10, 420);
         int listHeight = this.height - top - bottom;
         this.searchResultsPage.createList(leftMargin, top, listWidth, listHeight, itemHeight);
         this.isSearchActive = true;
         this.buildPage();
      } else {
         this.isSearchActive = false;
         this.currentListIdx = 0;
         this.buildPage();
      }
   }

   private void buildPage() {
      this.buttons.clear();
      this.pageButtons.clear();
      this.iconWidgets.clear();
      String savedInput = this.searchField != null ? this.searchField.getInput() : "";
      boolean savedFocused = this.searchField != null && this.searchField.focused;
      boolean savedSelected = this.searchField != null && this.searchField.selected;
      this.clearWidgets();
      int x = 10;
      int y = 4;
      int width = 100;
      int j = 0;

      for (ModSettingsEntry modEntry : this.modSettingsEntries) {
         ModIconWidget iconWidget = new ModIconWidget(modEntry.modName, modEntry.getIcon(), x, y, width, 28);
         this.iconWidgets.add(iconWidget);
         this.addWidget(iconWidget);
         y += 28;

         for (OptionPage page : modEntry.getPages()) {
            int finalIdx = j;
            VButtonWidget widget = new VButtonWidget(x, y, width, 20, Component.nullToEmpty(page.name), button -> this.setOptionList(finalIdx));
            widget.setTextLayout(false, 12);
            this.buttons.add(widget);
            this.pageButtons.add(widget);
            this.addWidget(widget);
            y += 20;
            j++;
         }
      }

      if (!this.isSearchActive) {
         this.pageButtons.get(this.currentListIdx).setSelected(true);
         VOptionList currentList = this.optionPages.get(this.currentListIdx).getOptionList();
         this.addWidget(currentList);
      } else if (this.searchResultsPage != null) {
         VOptionList searchList = this.searchResultsPage.getOptionList();
         this.addWidget(searchList);
         this.searchResultsPage.updateOptionStates();
      }

      this.addButtonsWithSearchBar();
      this.searchField.setInput(savedInput);
      if (savedFocused) {
         this.searchField.setFocused(true);
         this.searchField.setSelected(savedSelected);
      }
   }

   private void addButtonsWithSearchBar() {
      int rightMargin = 10;
      int padding = 10;
      int buttonWidth = Minecraft.getInstance().font.width(CommonComponents.GUI_DONE) + 2 * padding;
      int x0 = this.width - buttonWidth - rightMargin;
      int y0 = this.height - 20 - 7;
      VButtonWidget doneButton = new VButtonWidget(x0, y0, buttonWidth, 20, CommonComponents.GUI_DONE, button -> Minecraft.getInstance().setScreen(this.parent));
      buttonWidth = Minecraft.getInstance().font.width(Component.translatable("xeno.options.buttons.apply")) + 2 * padding;
      x0 -= buttonWidth + 5;
      this.applyButton = new VButtonWidget(x0, y0, buttonWidth, 20, Component.translatable("xeno.options.buttons.apply"), button -> this.applyOptions());
      buttonWidth = Minecraft.getInstance().font.width(Component.translatable("xeno.options.buttons.undo")) + 2 * padding;
      x0 -= buttonWidth + 5;
      this.undoButton = new VButtonWidget(x0, y0, buttonWidth, 20, Component.translatable("xeno.options.buttons.undo"), button -> this.undo());
       this.buttons.add(this.applyButton);
       this.buttons.add(doneButton);
       this.buttons.add(this.undoButton);
       this.addWidget(this.applyButton);
       this.addWidget(doneButton);
       this.addWidget(this.undoButton);
       this.addWidget(this.searchField);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
      for (GuiEventListener element : this.children()) {
         if (element.mouseClicked(event, bl)) {
            this.setFocused(element);
            if (event.button() == 0) {
               this.setDragging(true);
            }

            this.updateState();
            return true;
         }
      }

      return false;
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.setDragging(false);
      this.updateState();
      return this.getChildAt(event.x(), event.y()).filter(guiEventListener -> guiEventListener.mouseReleased(event)).isPresent();
   }

   public void onClose() {
      Minecraft.getInstance().setScreen(this.parent);
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
      GuiRenderer.guiGraphics = guiGraphics;
      VRenderSystem.enableBlend();
      VOptionList currentList;
      if (this.isSearchActive && this.searchResultsPage != null) {
         currentList = this.searchResultsPage.getOptionList();
      } else {
         currentList = this.optionPages.get(this.currentListIdx).getOptionList();
      }

      currentList.updateState(mouseX, mouseY);
      currentList.renderWidget(mouseX, mouseY);

      for (VAbstractWidget widget : this.iconWidgets) {
         widget.render(mouseX, mouseY);
      }

      for (VButtonWidget button : this.buttons) {
         button.updateState(mouseX, mouseY);
         button.render(mouseX, mouseY);
      }

      this.searchField.updateState(mouseX, mouseY);
      this.searchField.render(mouseX, mouseY);
      VAbstractWidget hoveredWidget = null;

      for (VButtonWidget b : this.buttons) {
         if (b.isMouseOver(mouseX, mouseY)) {
            hoveredWidget = b;
            break;
         }
      }

      if (hoveredWidget == null) {
         hoveredWidget = currentList.getHoveredWidget(mouseX, mouseY);
      }

      if (hoveredWidget != null) {
         this.renderTooltip(hoveredWidget, this.tooltipX, this.tooltipY);
      }
   }

   private void renderTooltip(VAbstractWidget widget, int x, int y) {
      List<FormattedCharSequence> list = this.getWidgetTooltip(widget);
      if (!list.isEmpty()) {
         int lines = list.size();
         int padding = 3;
         int width = GuiRenderer.getMaxTextWidth(this.font, list);
         int height = lines * 10;
         float intensity = 0.05F;
         int color = ColorUtil.ARGB.pack(intensity, intensity, intensity, 0.6F);
         GuiRenderer.fill(x - padding, y - padding, x + width + padding, y + height + padding, color);
         color = VGuiConstants.COLOR_RED;
         GuiRenderer.renderBorder(x - padding, y - padding, x + width + padding, y + height + padding, 1, color);
         int yOffset = 0;

         for (FormattedCharSequence text : list) {
            GuiRenderer.drawString(this.font, text, x, y + yOffset, -1);
            yOffset += 10;
         }
      }
   }

   private List<FormattedCharSequence> getWidgetTooltip(VAbstractWidget widget) {
      Component tooltip = widget.getTooltip();
      PerformanceImpact impact = widget.getImpact();
      List<FormattedCharSequence> textList = new ArrayList<>();
      if (tooltip != null) {
         textList.addAll(this.font.split(tooltip, this.tooltipWidth));
      }

      if (impact != null) {
         textList.addAll(this.font.split(Component.translatable("Performance Impact: %s", new Object[]{impact.component()}), this.tooltipWidth));
      }

      return textList;
   }

   private void updateState() {
      if (!(this.applyButton == null | this.undoButton == null)) {
         boolean modified = false;

         for (OptionPage page : this.optionPages) {
            modified |= page.optionChanged();
         }

         if (modified) {
            for (OptionPage page : this.optionPages) {
               page.optionChanged();
            }
         }

         this.applyButton.active = modified;
         this.undoButton.visible = modified;
      }
   }

   private void setOptionList(int i) {
      this.currentListIdx = i;
      this.isSearchActive = false;
      this.searchField.setInput("");
      this.searchField.setFocused(false);
      this.buildPage();
      this.pageButtons.get(i).setSelected(true);
   }

   private void undo() {
      for (OptionPage page : this.optionPages) {
         page.resetToOriginalState();
         page.updateOptionStates();
      }

      this.buildPage();
   }

   private void applyOptions() {
      for (OptionPage page : List.copyOf(this.optionPages)) {
         page.applyOptionChanges();
         page.updateOptionStates();
      }

      for (ModSettingsEntry modEntry : this.modSettingsEntries) {
         modEntry.runOnApply();
      }
   }

   public boolean keyPressed(KeyEvent keyEvent) {
      if (keyEvent.hasControlDown() && keyEvent.key() == 76) {
         this.setFocused(this.searchField);
         this.searchField.setFocused(true);
         this.searchField.setSelected(true);
         return true;
      } else if (keyEvent.key() == 256 && this.isSearchActive) {
         this.isSearchActive = false;
         this.searchField.setInput("");
         this.searchField.setFocused(false);
         this.buildPage();
         this.pageButtons.get(this.currentListIdx).setSelected(true);
         return true;
      } else if (!this.searchField.focused && keyEvent.key() == 80 && keyEvent.hasShiftDown()) {
         Minecraft.getInstance().setScreen(new VideoSettingsScreen(this, Minecraft.getInstance(), Minecraft.getInstance().options));
         return false;
      } else {
         return super.keyPressed(keyEvent);
      }
   }
}
