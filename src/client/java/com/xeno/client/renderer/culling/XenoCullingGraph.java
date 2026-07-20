package com.xeno.client.renderer.culling;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom culling graph implementation that stores nodes corresponding to chunk sections,
 * along with their visibility and opacity states.
 */
public class XenoCullingGraph {

    public static class Node {
        public final SectionRenderDispatcher.RenderSection section;
        public final long sectionNode;
        private boolean visible;
        private boolean opaque;

        public Node(SectionRenderDispatcher.RenderSection section) {
            this.section = section;
            this.sectionNode = section.getSectionNode();
        }

        public boolean isVisible() {
            return this.visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public boolean isOpaque() {
            return this.opaque;
        }

        public void setOpaque(boolean opaque) {
            this.opaque = opaque;
        }
    }

    private final ConcurrentHashMap<Long, Node> nodes = new ConcurrentHashMap<>();

    public void clear() {
        this.nodes.clear();
    }

    public Node getOrCreateNode(SectionRenderDispatcher.RenderSection section) {
        if (section == null) return null;
        return this.nodes.computeIfAbsent(section.getSectionNode(), n -> new Node(section));
    }

    public Node getNode(long sectionNode) {
        return this.nodes.get(sectionNode);
    }

    public Collection<Node> getNodes() {
        return this.nodes.values();
    }
}
