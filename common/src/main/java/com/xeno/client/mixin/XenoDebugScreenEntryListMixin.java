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
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(DebugScreenEntryList.class)
public abstract class XenoDebugScreenEntryListMixin {
    @Final
    @Shadow
    private Map<Identifier, DebugScreenEntryStatus> allStatuses;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xeno$enableDebugEntriesOnInit(CallbackInfo ci) {
        this.xeno$applyDefaults(false);
    }

    @Inject(method = "resetToProfile", at = @At("TAIL"))
    private void xeno$enableDebugEntriesOnProfileReset(DebugScreenProfile profile, CallbackInfo ci) {
        this.xeno$applyDefaults(profile != DebugScreenProfile.PERFORMANCE);
    }

    @Unique
    private void xeno$applyDefaults(boolean full) {
        Identifier entry = full ? XenoDebugEntry.DEBUG_ENTRY_FULL : XenoDebugEntry.DEBUG_ENTRY_REDUCED;
        this.allStatuses.putIfAbsent(entry, DebugScreenEntryStatus.IN_OVERLAY);
        this.allStatuses.putIfAbsent(XenoDebugEntry.FPS_PERCENTILES, DebugScreenEntryStatus.IN_OVERLAY);
    }
}
