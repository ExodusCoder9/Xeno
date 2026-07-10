package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@org.jspecify.annotations.NullMarked
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {

    @Inject(method = "compile", at = @At("HEAD"))
    private void xenoBeforeCompile(
            SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        XenoClient.xenoSetCurrentSectionNode(sectionPos.asLong());
        int[] counts = XenoClient.xenoPerDirCounts.get();
        java.util.Arrays.fill(counts, 0);
        XenoClient.xenoTotalVertices.get()[0] = 0;

        net.minecraft.world.phys.Vec3 camPos = XenoClient.getCameraPos();
        boolean shouldCull = false;
        float[] cullDir = XenoClient.xenoCullDir.get();
        if (camPos != null) {
            double dx = sectionPos.center().getX() - camPos.x;
            double dy = sectionPos.center().getY() - camPos.y;
            double dz = sectionPos.center().getZ() - camPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 60.0 * 60.0) {
                shouldCull = true;
                float yaw = XenoClient.getCameraYaw();
                float pitch = XenoClient.getCameraPitch();
                cullDir[0] = (float) (Math.cos(Math.toRadians(pitch)) * Math.sin(Math.toRadians(yaw)));
                cullDir[1] = (float) Math.sin(Math.toRadians(pitch));
                cullDir[2] = (float) (Math.cos(Math.toRadians(pitch)) * Math.cos(Math.toRadians(yaw)));
            }
        }
        XenoClient.xenoShouldCull.set(shouldCull);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void xenoAfterCompile(
            SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        long node = sectionPos.asLong();
        net.minecraft.client.renderer.ViewArea area = XenoClient.getViewArea();
        if (area != null) {
            net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection section = ((com.xeno.client.mixin.ViewAreaAccessor) area).invokeGetRenderSection(node);
            if (section != null) {
                XenoClient.getSectionFaceData().record(
                    section.index,
                    XenoClient.xenoPerDirCounts.get(),
                    XenoClient.xenoTotalVertices.get()[0]
                );
            }
        }
        XenoClient.xenoClearCurrentSectionNode();
    }

    @Redirect(
        method = "compile",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/FluidRenderer;tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"
        )
    )
    private void xenoRedirectTesselateFluid(
            FluidRenderer fluidRenderer,
            BlockAndTintGetter level, BlockPos pos,
            FluidRenderer.Output output, BlockState blockState, FluidState fluidState
    ) {
        FluidRenderer.Output wrappedOutput = layer -> {
            VertexConsumer originalConsumer = output.getBuilder(layer);
            return new VertexConsumer() {
                @Override
                public VertexConsumer addVertex(float x, float y, float z) {
                    originalConsumer.addVertex(x, y, z);
                    return this;
                }

                @Override
                public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
                    XenoClient.xenoTotalVertices.get()[0]++;
                    originalConsumer.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
                }

                @Override
                public VertexConsumer setLineWidth(float width) {
                    originalConsumer.setLineWidth(width);
                    return this;
                }

                @Override
                public VertexConsumer setNormal(float x, float y, float z) {
                    originalConsumer.setNormal(x, y, z);
                    return this;
                }

                @Override
                public VertexConsumer setColor(int r, int g, int b, int a) {
                    originalConsumer.setColor(r, g, b, a);
                    return this;
                }

                @Override
                public VertexConsumer setColor(int color) {
                    originalConsumer.setColor(color);
                    return this;
                }

                @Override
                public VertexConsumer setUv(float u, float v) {
                    originalConsumer.setUv(u, v);
                    return this;
                }

                @Override
                public VertexConsumer setUv1(int u, int v) {
                    originalConsumer.setUv1(u, v);
                    return this;
                }

                @Override
                public VertexConsumer setUv2(int u, int v) {
                    originalConsumer.setUv2(u, v);
                    return this;
                }
            };
        };

        fluidRenderer.tesselate(level, pos, wrappedOutput, blockState, fluidState);
    }
}
