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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.fabric.client.render.frapi.mesh;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class XenoItemRenderTypes {
    private XenoItemRenderTypes() {}

    public static final RenderType[] ITEM_RENDER_TYPES = new RenderType[] {
        Sheets.cutoutBlockItemSheet(),
        Sheets.translucentBlockItemSheet(),
        Sheets.cutoutItemSheet(),
        Sheets.translucentItemSheet()
    };

    public static final RenderType[] ITEM_GLINT_RENDER_TYPES = new RenderType[] {
        Sheets.cutoutBlockItemGlintSheet(),
        Sheets.translucentBlockItemGlintSheet(),
        Sheets.cutoutItemGlintSheet(),
        Sheets.translucentItemGlintSheet()
    };

    public static final RenderType[] ITEM_GLINT_SPECIAL_RENDER_TYPES = new RenderType[] {
        Sheets.cutoutBlockItemGlintSpecialSheet(),
        Sheets.translucentBlockItemGlintSpecialSheet(),
        Sheets.cutoutItemGlintSpecialSheet(),
        Sheets.translucentItemGlintSpecialSheet()
    };

    public static int toItemRenderTypeIndex(RenderType renderType) {
        if (renderType == null) return 0;
        for (int i = 0; i < ITEM_RENDER_TYPES.length; i++) {
            if (ITEM_RENDER_TYPES[i].equals(renderType)) return i;
        }
        return 0;
    }

    public static RenderType toItemRenderType(int index) {
        if (index < 0 || index >= ITEM_RENDER_TYPES.length) return ITEM_RENDER_TYPES[0];
        return ITEM_RENDER_TYPES[index];
    }

    public static int toItemGlintRenderTypeIndex(RenderType renderType) {
        if (renderType == null) return 0;
        for (int i = 0; i < ITEM_GLINT_RENDER_TYPES.length; i++) {
            if (ITEM_GLINT_RENDER_TYPES[i].equals(renderType)) return i;
        }
        return 0;
    }

    public static RenderType toItemGlintRenderType(int index) {
        if (index < 0 || index >= ITEM_GLINT_RENDER_TYPES.length) return ITEM_GLINT_RENDER_TYPES[0];
        return ITEM_GLINT_RENDER_TYPES[index];
    }

    public static int toItemGlintSpecialRenderTypeIndex(RenderType renderType) {
        if (renderType == null) return 0;
        for (int i = 0; i < ITEM_GLINT_SPECIAL_RENDER_TYPES.length; i++) {
            if (ITEM_GLINT_SPECIAL_RENDER_TYPES[i].equals(renderType)) return i;
        }
        return 0;
    }

    public static RenderType toItemGlintSpecialRenderType(int index) {
        if (index < 0 || index >= ITEM_GLINT_SPECIAL_RENDER_TYPES.length) return ITEM_GLINT_SPECIAL_RENDER_TYPES[0];
        return ITEM_GLINT_SPECIAL_RENDER_TYPES[index];
    }

    public static QuadAtlas getAtlasForRenderType(RenderType renderType) {
        if (renderType == Sheets.cutoutItemSheet() || renderType == Sheets.translucentItemSheet()
            || renderType == Sheets.cutoutItemGlintSheet() || renderType == Sheets.translucentItemGlintSheet()
            || renderType == Sheets.cutoutItemGlintSpecialSheet() || renderType == Sheets.translucentItemGlintSpecialSheet()) {
            return QuadAtlas.ITEM;
        }
        return QuadAtlas.BLOCK;
    }
}
