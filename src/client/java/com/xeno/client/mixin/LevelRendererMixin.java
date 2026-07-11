package com.xeno.client.mixin;

import com.xeno.client.culling.XenoVisibility;
import com.xeno.client.renderer.XenoWorldRenderer;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SectionUpdateRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow @Final private SectionOcclusionGraph sectionOcclusionGraph;
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private OptionsRenderState optionsRenderState;
    @Shadow private @Nullable ViewArea viewArea;

    @Unique
    private XenoWorldRenderer xenoWorldRenderer;

    /**
     * Initialize XenoWorldRenderer alongside LevelRenderer.
     *
     * @author ExodusCoder9
     * @reason Capture references and instantiate the Xeno optimization hub
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void xenoOnInit(
            EntityRenderDispatcher entityRenderDispatcher,
            BlockEntityRenderDispatcher blockEntityRenderDispatcher,
            ModelManager modelManager,
            TextureManager textureManager,
            AtlasManager atlasManager,
            net.minecraft.client.renderer.ShaderManager shaderManager,
            net.minecraft.client.renderer.GameRenderer gameRenderer,
            int width,
            int height,
            CallbackInfo ci
    ) {
        this.xenoWorldRenderer = new XenoWorldRenderer();
        XenoWorldRenderer.setInstance(this.xenoWorldRenderer);
    }

    /**
     * Replace compileSections to skip OCCLUDED sections before any task allocation.
     * <p>
     * Vanilla iterates ALL sectionUpdateRenderStates and creates CompileTask + copies
     * RenderSectionRegion for each, even for occluded sections. Our mixin in
     * SectionRenderDispatcher cancels compileAsync() at HEAD, but by then the
     * CompileTask and region copy are already allocated.
     * <p>
     * This override skips the entire compileAsync() call for sections that the
     * Xeno culling thread has marked as SKIP, eliminating the wasted allocation.
     *
     * @author ExodusCoder9
     * @reason Skip occluded sections before task allocation
     */
    @Overwrite
    private void compileSections(final CameraRenderState camera) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("populateSectionsToCompile");
        BlockPos cameraPosition = camera.blockPos;
        long fadeDuration = (long) Mth.floor(this.optionsRenderState.chunkSectionFadeInTime * 1000.0);

        int skipped = 0;
        int processed = 0;

        for (SectionUpdateRenderState state : this.levelRenderState.sectionUpdateRenderStates) {
            long sectionNode = state.sectionNode();
            BlockPos center = SectionPos.of(sectionNode).center();
            double distSqr = center.distSqr(cameraPosition);
            boolean isNearby = distSqr < 768.0;
            boolean rebuildSync = false;

            if (this.optionsRenderState.prioritizeChunkUpdates == PrioritizeChunkUpdates.NEARBY) {
                rebuildSync = isNearby || state.playerChanged();
            } else if (this.optionsRenderState.prioritizeChunkUpdates == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
                rebuildSync = state.playerChanged();
            }

            SectionRenderDispatcher.RenderSection section = ((com.xeno.client.mixin.ViewAreaAccessor) this.viewArea).invokeGetRenderSection(sectionNode);

            if (!isNearby && !section.wasPreviouslyEmpty()) {
                section.setFadeDuration(fadeDuration);
            } else {
                section.setFadeDuration(0L);
            }
            section.setWasPreviouslyEmpty(false);

            // Xeno: skip occluded sections before any task allocation
            if (XenoVisibility.hasVisibilityData() && XenoVisibility.isOccluded(sectionNode)) {
                skipped++;
                continue;
            }

            if (rebuildSync) {
                profiler.push("compileSectionSynchronously");
                section.compileSync(state.region());
                profiler.pop();
            } else {
                section.compileAsync(state.region());
            }
            processed++;
        }

        if (this.xenoWorldRenderer != null) {
            this.xenoWorldRenderer.onCompileSectionsFrame(skipped, processed);
        }

        profiler.popPush("scheduleTranslucentResort");
        this.xenoScheduleTranslucentResort(camera.pos);
        profiler.pop();
    }

    @Shadow
    private void scheduleTranslucentSectionResort(final Vec3 cameraPos) {
    }

    @Unique
    private void xenoScheduleTranslucentResort(Vec3 cameraPos) {
        this.scheduleTranslucentSectionResort(cameraPos);
    }
}
