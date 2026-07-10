package com.xeno.client.renderer;

import com.xeno.client.culling.CullingResult;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public final class XenoOctree {
    private final Branch root;
    private final int[] camCenter;

    public XenoOctree(long cameraSectionNode, int renderDistance, int minY) {
        int diameter = renderDistance * 2 + 1;
        int bbSize = nextPowerOfTwo(diameter);
        int halfBlocks = renderDistance * 16;

        int camOriginX = SectionPos.sectionToBlockCoord(SectionPos.x(cameraSectionNode));
        int camOriginY = SectionPos.sectionToBlockCoord(SectionPos.y(cameraSectionNode));
        int camOriginZ = SectionPos.sectionToBlockCoord(SectionPos.z(cameraSectionNode));

        int minX = camOriginX - halfBlocks;
        int maxX = minX + bbSize * 16 - 1;
        int minYBlocks = bbSize >= 24 ? minY : camOriginY - halfBlocks;
        int maxYBlocks = minYBlocks + bbSize * 16 - 1;
        int minZ = camOriginZ - halfBlocks;
        int maxZ = minZ + bbSize * 16 - 1;

        this.camCenter = new int[]{
                camOriginX + 8,
                camOriginY + 8,
                camOriginZ + 8
        };
        this.root = new Branch(new BoundingBox(minX, minYBlocks, minZ, maxX, maxYBlocks, maxZ));
    }

    public void add(SectionRenderDispatcher.RenderSection section) {
        root.add(section, 0);
    }

    public void visitVisible(Visitor visitor, Frustum frustum, byte[] visibility, int closeDistance) {
        root.visitVisible(visitor, false, frustum, visibility, 0, closeDistance, true);
    }

    private static int nextPowerOfTwo(int v) {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    private boolean isClose(BoundingBox bb, int closeDist) {
        return camCenter[0] > bb.minX() - closeDist
                && camCenter[0] < bb.maxX() + closeDist
                && camCenter[1] > bb.minY() - closeDist
                && camCenter[1] < bb.maxY() + closeDist
                && camCenter[2] > bb.minZ() - closeDist
                && camCenter[2] < bb.maxZ() + closeDist;
    }

    public interface Visitor {
        void visit(SectionRenderDispatcher.RenderSection section, boolean fullyVisible, int depth, boolean isClose);
    }

    private interface Node {
        void visitVisible(Visitor v, boolean skip, Frustum f, byte[] vis, int depth, int close, boolean isClose);
    }

    private final class Branch implements Node {
        private final @Nullable Node[] nodes = new Node[8];
        private final BoundingBox bb;
        private final int cx, cy, cz;
        private final AxisSort axisSort;
        private final boolean camXNeg, camYNeg, camZNeg;

        Branch(BoundingBox bb) {
            this.bb = bb;
            this.cx = bb.minX() + bb.getXSpan() / 2;
            this.cy = bb.minY() + bb.getYSpan() / 2;
            this.cz = bb.minZ() + bb.getZSpan() / 2;
            int dx = camCenter[0] - cx;
            int dy = camCenter[1] - cy;
            int dz = camCenter[2] - cz;
            this.axisSort = AxisSort.pick(Math.abs(dx), Math.abs(dy), Math.abs(dz));
            this.camXNeg = dx < 0;
            this.camYNeg = dy < 0;
            this.camZNeg = dz < 0;
        }

        void add(SectionRenderDispatcher.RenderSection section, int depth) {
            long sn = section.getSectionNode();
            int sx = SectionPos.sectionToBlockCoord(SectionPos.x(sn));
            int sy = SectionPos.sectionToBlockCoord(SectionPos.y(sn));
            int sz = SectionPos.sectionToBlockCoord(SectionPos.z(sn));

            boolean xNeg = (sx - cx) < 0;
            boolean yNeg = (sy - cy) < 0;
            boolean zNeg = (sz - cz) < 0;
            int idx = axisSort.index(
                    xNeg != camXNeg,
                    yNeg != camYNeg,
                    zNeg != camZNeg
            );

            if (bb.getXSpan() == 32) {
                nodes[idx] = new Leaf(section);
            } else if (nodes[idx] instanceof Branch b) {
                b.add(section, depth + 1);
            } else if (nodes[idx] instanceof Leaf) {
                nodes[idx] = new Leaf(section);
            } else {
                BoundingBox childBB = childBB(xNeg, yNeg, zNeg);
                Branch b = new Branch(childBB);
                nodes[idx] = b;
                b.add(section, depth + 1);
            }
        }

        private BoundingBox childBB(boolean xNeg, boolean yNeg, boolean zNeg) {
            return new BoundingBox(
                    xNeg ? bb.minX() : cx,
                    yNeg ? bb.minY() : cy,
                    zNeg ? bb.minZ() : cz,
                    xNeg ? cx - 1 : bb.maxX(),
                    yNeg ? cy - 1 : bb.maxY(),
                    zNeg ? cz - 1 : bb.maxZ()
            );
        }

        @Override
        public void visitVisible(Visitor v, boolean skipFrustum, Frustum frustum, byte[] vis, int depth, int closeDist, boolean isClose) {
            boolean visible = skipFrustum;
            if (!skipFrustum) {
                int result = frustum.cubeInFrustum(bb);
                skipFrustum = (result == -2);
                visible = (result == -2 || result == -1);
            }
            if (visible) {
                isClose = isClose && XenoOctree.this.isClose(bb, closeDist);
                for (Node child : nodes) {
                    if (child != null) {
                        child.visitVisible(v, skipFrustum, frustum, vis, depth + 1, closeDist, isClose);
                    }
                }
            }
        }
    }

    private final class Leaf implements Node {
        private final SectionRenderDispatcher.RenderSection section;
        private final AABB aabb;

        Leaf(SectionRenderDispatcher.RenderSection section) {
            this.section = section;
            this.aabb = section.getBoundingBox();
        }

        @Override
        public void visitVisible(Visitor v, boolean skipFrustum, Frustum frustum, byte[] vis, int depth, int closeDist, boolean isClose) {
            boolean visible = skipFrustum;
            if (!skipFrustum) {
                visible = frustum.isVisible(aabb);
            }
            if (visible && vis[section.index] != CullingResult.OCCLUDED) {
                isClose = isClose && XenoOctree.this.isClose(
                        new BoundingBox(
                                (int) aabb.minX, (int) aabb.minY, (int) aabb.minZ,
                                (int) aabb.maxX, (int) aabb.maxY, (int) aabb.maxZ
                        ),
                        closeDist
                );
                v.visit(section, skipFrustum, depth, isClose);
            }
        }
    }

    private enum AxisSort {
        XYZ(4, 2, 1), XZY(4, 1, 2),
        YXZ(2, 4, 1), YZX(1, 4, 2),
        ZXY(2, 1, 4), ZYX(1, 2, 4);

        final int xs, ys, zs;

        AxisSort(int xs, int ys, int zs) {
            this.xs = xs;
            this.ys = ys;
            this.zs = zs;
        }

        int index(boolean xOpp, boolean yOpp, boolean zOpp) {
            int i = 0;
            if (xOpp) i += xs;
            if (yOpp) i += ys;
            if (zOpp) i += zs;
            return i;
        }

        static AxisSort pick(int ax, int ay, int az) {
            if (ax > ay && ax > az) return ay > az ? XYZ : XZY;
            if (ay > ax && ay > az) return ax > az ? YXZ : YZX;
            return ax > ay ? ZXY : ZYX;
        }
    }
}
