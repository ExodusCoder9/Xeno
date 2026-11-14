package com.xeno.fabric.client.render.frapi.mesh;

import com.xeno.fabric.client.render.frapi.quad.XenoQuadEmitter;
import com.xeno.fabric.client.render.frapi.quad.XenoQuadView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public abstract class XenoMeshView implements MeshView {
    private static final ThreadLocal<ObjectArrayList<XenoQuadView>> CURSOR_POOLS =
        ThreadLocal.withInitial(ObjectArrayList::new);

    protected int[] data;
    protected int limit;

    public int[] data() {
        return this.data;
    }

    @Override
    public int size() {
        return this.limit / XenoMeshFormat.TOTAL_STRIDE;
    }

    @Override
    public void forEach(@NonNull Consumer<? super QuadView> action) {
        ObjectArrayList<XenoQuadView> pool = CURSOR_POOLS.get();
        XenoQuadView quad = pool.isEmpty() ? new XenoQuadView() : pool.pop();
        try {
            int end = this.limit;
            for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
                quad.load(this.data, i);
                action.accept(quad);
            }
        } finally {
            quad.unload();
            pool.push(quad);
        }
    }

    @Override
    public void outputTo(@NonNull QuadEmitter emitter) {
        int end = this.limit;

        if (emitter instanceof XenoQuadEmitter xenoEmitter) {
            int[] destination = xenoEmitter.data();
            int baseIndex = xenoEmitter.baseIndex();
            for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
                System.arraycopy(this.data, i, destination, baseIndex, XenoMeshFormat.TOTAL_STRIDE);
                xenoEmitter.emit();
                destination = xenoEmitter.data();
                baseIndex = xenoEmitter.baseIndex();
            }
            return;
        }

        ObjectArrayList<XenoQuadView> pool = CURSOR_POOLS.get();
        XenoQuadView quad = pool.isEmpty() ? new XenoQuadView() : pool.pop();
        try {
            for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
                quad.load(this.data, i);
                copyTo(quad, emitter);
                emitter.emit();
            }
        } finally {
            quad.unload();
            pool.push(quad);
        }
    }

    private static void copyTo(XenoQuadView src, QuadEmitter dst) {
        dst.cullFace(src.cullFace());
        dst.nominalFace(src.nominalFace());
        dst.atlas(src.atlas());
        dst.chunkLayer(src.chunkLayer());
        dst.itemRenderType(src.itemRenderType());
        dst.itemGlintRenderType(src.itemGlintRenderType());
        dst.itemGlintSpecialRenderType(src.itemGlintSpecialRenderType());
        dst.emissive(src.emissive());
        dst.shadeDirectionOverride(src.shadeDirectionOverride());
        dst.ambientOcclusion(src.ambientOcclusion());
        dst.foilType(src.foilType());
        dst.shadeMode(src.shadeMode());
        dst.animated(src.animated());
        dst.tintIndex(src.tintIndex());
        dst.tag(src.tag());

        for (int i = 0; i < 4; i++) {
            dst.pos(i, src.x(i), src.y(i), src.z(i));
            dst.color(i, src.color(i));
            dst.uv(i, src.u(i), src.v(i));
            dst.lightmap(i, src.lightmap(i));
            if (src.hasNormal(i)) {
                dst.normal(i, src.normalX(i), src.normalY(i), src.normalZ(i));
            } else {
                dst.normal(i, Float.NaN, Float.NaN, Float.NaN);
            }
        }
    }
}
