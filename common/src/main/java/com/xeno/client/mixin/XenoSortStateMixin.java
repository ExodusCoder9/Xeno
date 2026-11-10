/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.xeno.client.common.render.chunk.XenoDistanceSorting;
import com.xeno.client.common.render.chunk.XenoSortStateHolder;
import com.xeno.client.common.render.chunk.XenoTranslucentQuadSorter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mojang.blaze3d.vertex.MeshData$SortState")
public abstract class XenoSortStateMixin implements XenoSortStateHolder {
    @Final
    @Shadow
    private CompactVectorArray centroids;

    @Final
    @Shadow
    private IndexType indexType;

    @Unique
    private XenoTranslucentQuadSorter xeno$sorter;

    @Override
    public XenoTranslucentQuadSorter xeno$getQuadSorter() {
        if (this.xeno$sorter == null && this.centroids != null) {
            int quadCount = this.centroids.size();
            float[] points = new float[quadCount * 3];
            for (int i = 0; i < quadCount; i++) {
                points[i * 3 + 0] = this.centroids.getX(i);
                points[i * 3 + 1] = this.centroids.getY(i);
                points[i * 3 + 2] = this.centroids.getZ(i);
            }
            this.xeno$sorter = new XenoTranslucentQuadSorter(points, quadCount, this.indexType);
        }
        return this.xeno$sorter;
    }

    @Override
    public void xeno$setQuadSorter(XenoTranslucentQuadSorter sorter) {
        this.xeno$sorter = sorter;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void xeno$onInit(CompactVectorArray centroids, IndexType indexType, CallbackInfo ci) {
        if (centroids != null) {
            int quadCount = centroids.size();
            if (quadCount > 0) {
                float[] points = new float[quadCount * 3];
                for (int i = 0; i < quadCount; i++) {
                    points[i * 3 + 0] = centroids.getX(i);
                    points[i * 3 + 1] = centroids.getY(i);
                    points[i * 3 + 2] = centroids.getZ(i);
                }
                this.xeno$setQuadSorter(new XenoTranslucentQuadSorter(points, quadCount, indexType));
            }
        }
    }

    @Inject(method = "buildSortedIndexBuffer", at = @At("HEAD"), cancellable = true)
    private void xeno$fastBspSort(ByteBufferBuilder target, VertexSorting sorting, CallbackInfoReturnable<ByteBufferBuilder.Result> cir) {
        XenoTranslucentQuadSorter sorter = this.xeno$getQuadSorter();
        if (sorter != null) {
            float camX, camY, camZ;
            if (sorting instanceof XenoDistanceSorting(float x, float y, float z)) {
                camX = x;
                camY = y;
                camZ = z;
            } else {
                return;
            }

            ByteBufferBuilder.Result result = sorter.sortAndBuildIndexBuffer(target, camX, camY, camZ);
            if (result != null) {
                cir.setReturnValue(result);
            }
        }
    }
}
