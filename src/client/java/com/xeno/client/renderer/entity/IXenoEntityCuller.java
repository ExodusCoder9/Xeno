package com.xeno.client.renderer.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;

/**
 * Interface defining the API contract for the Asynchronous Path-Traced Entity Culler.
 */
public interface IXenoEntityCuller {
    boolean isEntityVisible(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            ClientLevel level,
            LevelRenderer levelRenderer,
            Minecraft minecraft
    );
    void tickFrame();
    void reset();
}
