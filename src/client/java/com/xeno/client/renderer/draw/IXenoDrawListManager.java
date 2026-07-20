package com.xeno.client.renderer.draw;

import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureManager;
import org.joml.Matrix4fc;
import java.util.List;

/**
 * Interface defining the API contract for the Xeno Draw List Manager.
 */
public interface IXenoDrawListManager {
    ChunkSectionsToRender prepareChunkRenders(
            List<SectionRenderDispatcher.RenderSection> visibleSections,
            SectionRenderDispatcher sectionRenderDispatcher,
            TextureManager textureManager,
            Matrix4fc modelViewMatrix
    );
}
