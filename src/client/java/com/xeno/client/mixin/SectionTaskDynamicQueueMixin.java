package com.xeno.client.mixin;

import com.xeno.client.culling.XenoTaskQueue;
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionTaskDynamicQueue.class)
@SuppressWarnings({"SpellCheckingInspection", "unused"})
public class SectionTaskDynamicQueueMixin implements XenoTaskQueue {
    @Unique
    private volatile double xeno_lookX = 0.0;
    @Unique
    private volatile double xeno_lookY = 0.0;
    @Unique
    private volatile double xeno_lookZ = -1.0;

    @Override
    @Unique
    public void xeno_updateCameraLook(double lx, double ly, double lz) {
        this.xeno_lookX = lx;
        this.xeno_lookY = ly;
        this.xeno_lookZ = lz;
    }

    @Redirect(
        method = "poll",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D"
        )
    )
    private double xeno_redirectDistToCenterSqr(BlockPos blockPos, Position position) {
        Vec3 cameraPos = (Vec3) position;
        double distSqr = blockPos.distToCenterSqr(cameraPos);
        // Do not skew prioritization for very close sections (within 32 blocks)
        if (distSqr < 1024.0) {
            return distSqr;
        }

        // Compute direction vector to the center of the 16x16x16 section
        double dx = blockPos.getX() + 8.0 - cameraPos.x;
        double dy = blockPos.getY() + 8.0 - cameraPos.y;
        double dz = blockPos.getZ() + 8.0 - cameraPos.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (length > 0.0) {
            double cosTheta = (this.xeno_lookX * dx + this.xeno_lookY * dy + this.xeno_lookZ * dz) / length;
            // CosTheta = 1.0 (directly in front) -> weight = 0.2 (highest priority)
            // CosTheta = -1.0 (directly behind) -> weight = 1.8 (lowest priority)
            double weight = 1.0 - cosTheta * 0.8;
            return distSqr * weight;
        }
        return distSqr;
    }
}
