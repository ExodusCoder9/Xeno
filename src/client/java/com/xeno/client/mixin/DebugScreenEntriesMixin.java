package com.xeno.client.mixin;

import net.minecraft.client.gui.components.debug.DebugEntryNoop;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenEntries.class)
@SuppressWarnings("unused")
public class DebugScreenEntriesMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void xeno_registerEntries(CallbackInfo ci) {
        DebugScreenEntries.register("xeno_renderer", new DebugEntryNoop(true));
        DebugScreenEntries.register("xeno_culling_stats", new DebugEntryNoop(true));
        DebugScreenEntries.register("xeno_entity_stats", new DebugEntryNoop(true));
    }
}
