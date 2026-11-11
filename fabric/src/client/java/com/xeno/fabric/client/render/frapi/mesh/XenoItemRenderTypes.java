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

package com.xeno.fabric.client.render.frapi.mesh;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class XenoItemRenderTypes {
    private XenoItemRenderTypes() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("XenoFrapi");
    private static final Set<RenderType> WARNED_RENDER_TYPES =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final class Holder {
        static final RenderType[] ITEM_RENDER_TYPES = new RenderType[] {
            Sheets.cutoutBlockItemSheet(),
            Sheets.translucentBlockItemSheet(),
            Sheets.cutoutItemSheet(),
            Sheets.translucentItemSheet()
        };

        static final RenderType[] ITEM_GLINT_RENDER_TYPES = new RenderType[] {
            Sheets.cutoutBlockItemGlintSheet(),
            Sheets.translucentBlockItemGlintSheet(),
            Sheets.cutoutItemGlintSheet(),
            Sheets.translucentItemGlintSheet()
        };

        static final RenderType[] ITEM_GLINT_SPECIAL_RENDER_TYPES = new RenderType[] {
            Sheets.cutoutBlockItemGlintSpecialSheet(),
            Sheets.translucentBlockItemGlintSpecialSheet(),
            Sheets.cutoutItemGlintSpecialSheet(),
            Sheets.translucentItemGlintSpecialSheet()
        };
    }

    public static int toItemRenderTypeIndex(RenderType renderType) {
        if (renderType == null) return -1;
        RenderType[] types = Holder.ITEM_RENDER_TYPES;
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(renderType)) return i;
        }
        warnUnknown(renderType, "item render type");
        return -1;
    }

    public static RenderType toItemRenderType(int index) {
        RenderType[] types = Holder.ITEM_RENDER_TYPES;
        if (index < 0 || index >= types.length) return types[0];
        return types[index];
    }

    public static int toItemGlintRenderTypeIndex(RenderType renderType) {
        if (renderType == null) return -1;
        RenderType[] types = Holder.ITEM_GLINT_RENDER_TYPES;
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(renderType)) return i;
        }
        return -1;
    }

    public static RenderType toItemGlintRenderType(int index) {
        RenderType[] types = Holder.ITEM_GLINT_RENDER_TYPES;
        if (index < 0 || index >= types.length) return types[0];
        return types[index];
    }

    public static int toItemGlintSpecialRenderTypeIndex(RenderType renderType) {
        if (renderType == null) return -1;
        RenderType[] types = Holder.ITEM_GLINT_SPECIAL_RENDER_TYPES;
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(renderType)) return i;
        }
        return -1;
    }

    public static RenderType toItemGlintSpecialRenderType(int index) {
        RenderType[] types = Holder.ITEM_GLINT_SPECIAL_RENDER_TYPES;
        if (index < 0 || index >= types.length) return types[0];
        return types[index];
    }

    private static void warnUnknown(RenderType renderType, String what) {
        if (WARNED_RENDER_TYPES.add(renderType)) {
            LOGGER.debug("Unknown {} '{}'; falling back to the block atlas", what, renderType);
        }
    }
}
