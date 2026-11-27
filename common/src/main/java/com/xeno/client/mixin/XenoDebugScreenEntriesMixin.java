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

package com.xeno.client.mixin;

import com.xeno.client.common.debug.XenoDebugEntry;
import com.xeno.client.common.debug.XenoFpsPercentilesEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(DebugScreenEntries.class)
public abstract class XenoDebugScreenEntriesMixin {
    @Shadow
    @Final
    private static Map<Identifier, DebugScreenEntry> ENTRIES_BY_ID;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void xeno$registerDebugEntries(CallbackInfo ci) {
        ENTRIES_BY_ID.put(XenoDebugEntry.DEBUG_ENTRY_FULL, new XenoDebugEntry(true));
        ENTRIES_BY_ID.put(XenoDebugEntry.DEBUG_ENTRY_REDUCED, new XenoDebugEntry(false));
        ENTRIES_BY_ID.put(XenoDebugEntry.FPS_PERCENTILES, new XenoFpsPercentilesEntry());
    }
}
