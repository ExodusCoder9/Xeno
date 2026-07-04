package com.xeno.client.mixin;

import com.xeno.client.culling.XenoOcclusionGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Shadow @Final
    private Minecraft minecraft;

    @Shadow @Final
    private LevelRenderer levelRenderer;

    @Shadow
    private ClientLevel level;

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void xeno_checkOcclusionCulling(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (this.level == null) return;

        // 1. Let vanilla handle passenger/player checks or basic culling rules
        if (this.minecraft.player != null && entity.hasIndirectPassenger(this.minecraft.player)) {
            return;
        }

        // 2. Fast frustum and distance check using entity renderer dispatcher
        if (!this.levelRenderer.entityRenderDispatcher().shouldRender(entity, frustum, camX, camY, camZ)) {
            cir.setReturnValue(false);
            return;
        }

        // 3. Occlusion culling check using the bounding box of the entity
        AABB box = entity.getBoundingBox();
        int minSecX = SectionPos.blockToSectionCoord(box.minX);
        int minSecY = SectionPos.blockToSectionCoord(box.minY);
        int minSecZ = SectionPos.blockToSectionCoord(box.minZ);
        int maxSecX = SectionPos.blockToSectionCoord(box.maxX);
        int maxSecY = SectionPos.blockToSectionCoord(box.maxY);
        int maxSecZ = SectionPos.blockToSectionCoord(box.maxZ);

        ViewArea viewArea = this.levelRenderer.viewArea();
        if (viewArea == null) return;

        boolean anySectionVisible = false;
        boolean hasTestedAnySection = false;

        for (int x = minSecX; x <= maxSecX; x++) {
            for (int y = minSecY; y <= maxSecY; y++) {
                for (int z = minSecZ; z <= maxSecZ; z++) {
                    long nodeLong = SectionPos.asLong(x, y, z);
                    SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) viewArea).invokeGetRenderSection(nodeLong);
                    if (section != null) {
                        hasTestedAnySection = true;
                        XenoOcclusionGraph xenoGraph = (XenoOcclusionGraph) this.levelRenderer.sectionOcclusionGraph();
                        if (xenoGraph.xeno$isSectionVisible(section.index)) {
                            anySectionVisible = true;
                            break;
                        }
                    }
                }
                if (anySectionVisible) break;
            }
            if (anySectionVisible) break;
        }

        // If we found sections intersecting the entity, but none are visible, cull it!
        if (hasTestedAnySection && !anySectionVisible) {
            cir.setReturnValue(false);
        }
    }
}
