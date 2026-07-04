package com.xeno.client.mixin;

import net.minecraft.client.RotatingSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.lang.reflect.Method;

@Mixin(RotatingSectionStorage.class)
public class RotatingSectionStorageMixin implements RotatingSectionStorageExt {
    @Shadow
    private Object[] nodes;

    @Shadow
    private int sectionGridSizeY;

    @Shadow
    private int sectionGridSizeXZ;

    @Unique
    private static Method nodeValueMethod;

    static {
        try {
            Class<?> nodeClass = Class.forName("net.minecraft.client.RotatingSectionStorage$Node");
            nodeValueMethod = nodeClass.getMethod("value");
            nodeValueMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    @Unique
    public Object[] xenoGetValues() {
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
    public int xenoGetGridSizeY() {
        return this.sectionGridSizeY;
    }

    @Override
    @Unique
    public int xenoGetGridSizeXZ() {
        return this.sectionGridSizeXZ;
    }
}
