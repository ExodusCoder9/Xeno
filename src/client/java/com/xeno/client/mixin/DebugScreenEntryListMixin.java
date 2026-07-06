package com.xeno.client.mixin;

import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(DebugScreenEntryList.class)
@SuppressWarnings("unused")
public abstract class DebugScreenEntryListMixin {
    @Shadow @Final
    private Map<Identifier, DebugScreenEntryStatus> allStatuses;

    @Shadow
    public abstract void rebuildCurrentList();

    @Inject(method = "load", at = @At("RETURN"))
    private void xeno_initDefaultStatuses(CallbackInfo ci) {
        Identifier renderer = Identifier.withDefaultNamespace("xeno_renderer");
        Identifier culling = Identifier.withDefaultNamespace("xeno_culling_stats");
        Identifier entity = Identifier.withDefaultNamespace("xeno_entity_stats");
        
        boolean changed = false;
        if (!this.allStatuses.containsKey(renderer)) {
            this.allStatuses.put(renderer, DebugScreenEntryStatus.ALWAYS_ON);
            changed = true;
        }
        if (!this.allStatuses.containsKey(culling)) {
            this.allStatuses.put(culling, DebugScreenEntryStatus.ALWAYS_ON);
            changed = true;
        }
        if (!this.allStatuses.containsKey(entity)) {
            this.allStatuses.put(entity, DebugScreenEntryStatus.ALWAYS_ON);
            changed = true;
        }
        
        if (changed) {
            this.rebuildCurrentList();
        }
    }
}
