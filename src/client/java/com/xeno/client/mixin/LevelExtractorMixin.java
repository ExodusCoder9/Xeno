package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private LevelRenderer levelRenderer;
    @Shadow private ClientLevel level;

    @Inject(
        method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/LevelRenderer;)V",
        at = @At("RETURN")
    )
    private void xenoStoreExtractor(Minecraft minecraft, LevelRenderState levelRenderState, LevelRenderer levelRenderer, CallbackInfo ci) {
        XenoClient.setLevelExtractor((LevelExtractor) (Object) this);
    }

    /**
     * @author ExodusCoder9
     * @reason Optimize entity culling to be allocation-free (eliminating BlockPos allocation storm) and check all sections intersecting the bounding box to prevent pop-in glitches.
     */
    @Overwrite
    public boolean isEntityVisible(final Entity entity, final Frustum frustum, final double camX, final double camY, final double camZ) {
        if (this.level == null) {
            return false;
        }

        if (!this.levelRenderer.entityRenderDispatcher().shouldRender(entity, frustum, camX, camY, camZ)
                && (this.minecraft.player == null || !entity.hasIndirectPassenger(this.minecraft.player))) {
            return false;
        }

        AABB aabb = entity.getBoundingBox();
        int minSecX = SectionPos.blockToSectionCoord(aabb.minX);
        int minSecY = SectionPos.blockToSectionCoord(aabb.minY);
        int minSecZ = SectionPos.blockToSectionCoord(aabb.minZ);
        int maxSecX = SectionPos.blockToSectionCoord(aabb.maxX);
        int maxSecY = SectionPos.blockToSectionCoord(aabb.maxY);
        int maxSecZ = SectionPos.blockToSectionCoord(aabb.maxZ);

        ViewArea area = this.levelRenderer.viewArea();
        if (area == null) {
            return false;
        }

        long now = Util.getMillis();

        for (int secY = minSecY; secY <= maxSecY; secY++) {
            if (this.level.isOutsideBuildHeight(SectionPos.sectionToBlockCoord(secY))) {
                return true;
            }
            for (int secX = minSecX; secX <= maxSecX; secX++) {
                for (int secZ = minSecZ; secZ <= maxSecZ; secZ++) {
                    long sectionNode = SectionPos.asLong(secX, secY, secZ);
                    SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) area).invokeGetRenderSection(sectionNode);
                    if (section != null && section.getSectionMesh() != CompiledSectionMesh.UNCOMPILED 
                            && section.getVisibility(now) >= 0.3F) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
