package com.xeno.client.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.components.debug.DebugEntryNoop;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;

@Mixin(DebugScreenEntries.class)
public class DebugScreenEntriesMixin {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void xeno_registerEntries(CallbackInfo ci) {
        try {
            Method register = DebugScreenEntries.class.getDeclaredMethod("register", String.class, DebugScreenEntry.class);
            register.setAccessible(true);

            register.invoke(null, "xeno_renderer", new DebugEntryNoop(true));
            register.invoke(null, "xeno_culling_stats", new DebugEntryNoop(true));
            register.invoke(null, "xeno_entity_stats", new DebugEntryNoop(true));
        } catch (Exception e) {
            LOGGER.error("Failed to register Xeno debug entries", e);
        }
    }
}