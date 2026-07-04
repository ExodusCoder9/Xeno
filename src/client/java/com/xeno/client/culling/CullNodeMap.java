package com.xeno.client.culling;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public class CullNodeMap {
    private final CullNode[] nodes;

    public CullNodeMap(int size) {
        this.nodes = new CullNode[size];
    }

    public void put(SectionRenderDispatcher.RenderSection renderSection, CullNode node) {
        if (renderSection != null && renderSection.index >= 0 && renderSection.index < this.nodes.length) {
            this.nodes[renderSection.index] = node;
        }
    }

    public CullNode get(SectionRenderDispatcher.RenderSection renderSection) {
        if (renderSection == null) return null;
        int index = renderSection.index;
        return index >= 0 && index < this.nodes.length ? this.nodes[index] : null;
    }
}
