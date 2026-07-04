package com.xeno.client.mixin;

import net.minecraft.client.RotatingSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RotatingSectionStorage.class)
public class RotatingSectionStorageMixin implements RotatingSectionStorageExt {
    @Shadow @Final
    private RotatingSectionStorage.Node[] nodes;

    @Shadow @Final
    private int sectionGridSizeY;

    @Shadow @Final
    private int sectionGridSizeXZ;

    @Override
    @Unique
    public Object[] xeno$GetValues() {
        Object[] values = new Object[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                values[i] = nodes[i].value();
            }
        }
        return values;
    }

    @Override
    @Unique
    public int xeno$GetGridSizeY() {
        return this.sectionGridSizeY;
    }

    @Override
    @Unique
    public int xeno$GetGridSizeXZ() {
        return this.sectionGridSizeXZ;
    }
}
