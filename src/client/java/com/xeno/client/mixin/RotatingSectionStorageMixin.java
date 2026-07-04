package com.xeno.client.mixin;

import net.minecraft.client.RotatingSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;

@Mixin(RotatingSectionStorage.class)
public class RotatingSectionStorageMixin implements RotatingSectionStorageExt {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow @Final
    private Object[] nodes;

    @Shadow @Final
    private int sectionGridSizeY;

    @Shadow @Final
    private int sectionGridSizeXZ;

    @Unique
    private static Method nodeValueMethod;

    static {
        try {
            Class<?> nodeClass = Class.forName("net.minecraft.client.RotatingSectionStorage$Node");
            nodeValueMethod = nodeClass.getMethod("value");
            nodeValueMethod.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize RotatingSectionStorage reflection", e);
        }
    }

    @Override
    @Unique
    public Object[] xeno$GetValues() {
        Object[] values = new Object[nodes.length];
        if (nodeValueMethod != null) {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] != null) {
                    try {
                        values[i] = nodeValueMethod.invoke(nodes[i]);
                    } catch (Exception e) {
                        values[i] = null;
                    }
                }
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
