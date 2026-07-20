package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.renderer.XenoSectionRenderDispatcher;
import com.xeno.client.renderer.util.XenoViewArea;
import com.xeno.client.renderer.util.XenoRendererExtension;
import net.minecraft.client.Options;
import net.minecraft.client.Camera;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.LeavesBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LevelTargetBundle;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import com.xeno.client.api.XenoRenderAPI;
import com.xeno.client.api.XenoFramePassBuilder;
import com.xeno.client.api.XenoRenderPass;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements XenoRendererExtension {
    @Shadow @Final private RenderBuffers renderBuffers;
    @Shadow @Final private CloudRenderer cloudRenderer;
    @Shadow @Final private SubmitNodeStorage submitNodeStorage;
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private SectionOcclusionGraph sectionOcclusionGraph;
    @Shadow @Final private LevelTargetBundle targets;
    @Shadow private @org.jspecify.annotations.Nullable ViewArea viewArea;
    @Shadow private @org.jspecify.annotations.Nullable SectionRenderDispatcher sectionRenderDispatcher;

    @Shadow
    public abstract void clearVisibleSections();

    @Unique
    private XenoWorldRenderer xenoWorldRenderer;

    @Unique
    private ModelManager xenoModelManager;

    @Override
    public XenoWorldRenderer xeno$getWorldRenderer() {
        return this.xenoWorldRenderer;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xenoInit(
            EntityRenderDispatcher entityRenderDispatcher,
            BlockEntityRenderDispatcher blockEntityRenderDispatcher,
            ModelManager modelManager,
            TextureManager textureManager,
            AtlasManager atlasManager,
            ShaderManager shaderManager,
            GameRenderer gameRenderer,
            int width,
            int height,
            CallbackInfo ci
    ) {
        this.xenoWorldRenderer = new XenoWorldRenderer();
        this.xenoModelManager = modelManager;
    }

    /**
     * THE CORE NEUTRALIZATION: intercept invalidateCompiledGeometry at HEAD,
     * cancel vanilla, install custom Xeno pipeline components.
     */
    @Inject(method = "invalidateCompiledGeometry", at = @At("HEAD"), cancellable = true)
    private void xenoNeutralizeAndReplace(
            ClientLevel level,
            Options options,
            Camera camera,
            BlockColors blockColors,
            CallbackInfo ci
    ) {
        ci.cancel();

        this.cloudRenderer.markForRebuild();
        LeavesBlock.setCutoutLeaves((Boolean) options.cutoutLeaves().get());

        if (this.xenoWorldRenderer != null) {
            this.xenoWorldRenderer.reload();
        }

        // Initialize our custom SectionCompiler
        SectionCompiler sectionCompiler = new SectionCompiler(
                options.ambientOcclusion().get(),
                (Boolean) options.cutoutLeaves().get(),
                this.xenoModelManager.getBlockStateModelSet(),
                this.xenoModelManager.getFluidStateModelSet(),
                blockColors
        );

        this.sectionRenderDispatcher = new XenoSectionRenderDispatcher(
                Util.backgroundExecutor(),
                this.renderBuffers,
                sectionCompiler,
                this.sectionOcclusionGraph::schedulePropagationFrom
        );

        int viewDistance = options.getEffectiveRenderDistance();
        this.viewArea = new XenoViewArea(
                this.sectionRenderDispatcher,
                level,
                viewDistance,
                this.sectionOcclusionGraph
        );

        this.sectionOcclusionGraph.waitAndReset(this.viewArea);
        this.clearVisibleSections();
    }

    @Inject(method = "addAlwaysOnTopPass", at = @At("HEAD"))
    private void xenoInjectCustomFramePasses(
            FrameGraphBuilder frame,
            FeatureRenderDispatcher.PreparedFrame featureFrame,
            GpuBufferSlice fog,
            CallbackInfo ci
    ) {
        // Run all registered XenoFramePassBuilders (Vulkan / generic compatible)
        for (XenoFramePassBuilder builder : XenoRenderAPI.getFramePassBuilders()) {
            builder.buildPasses(frame, this.targets, this.levelRenderState);
        }

        // Run simpler legacy XenoRenderPasses by generating frame graph passes for them automatically
        for (XenoRenderPass.Position position : XenoRenderPass.Position.values()) {
            java.util.List<XenoRenderPass> passes = XenoRenderAPI.getRenderPasses(position);
            if (passes != null && !passes.isEmpty()) {
                FramePass pass = frame.addPass("xeno_" + position.name().toLowerCase());
                
                // Let the pass read and write to the main target to fit in the frame graph
                this.targets.main = pass.readsAndWrites(this.targets.main);
                
                pass.executes(() -> {
                    for (XenoRenderPass rp : passes) {
                        rp.render(position, this.levelRenderState);
                    }
                });
            }
        }
    }
}
