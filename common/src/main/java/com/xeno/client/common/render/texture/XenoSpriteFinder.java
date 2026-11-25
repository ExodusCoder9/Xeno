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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.common.render.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class XenoSpriteFinder {
    private static final int MAX_DEPTH = 6;
    private static final int SPLIT_THRESHOLD = 8;

    private final Node root;
    private final TextureAtlasSprite missingSprite;

    public XenoSpriteFinder(List<TextureAtlasSprite> sprites, TextureAtlasSprite missingSprite) {
        this.missingSprite = missingSprite;
        this.root = new Node(0.0F, 0.0F, 1.0F, 1.0F, 0);
        for (int i = 0; i < sprites.size(); i++) {
            this.root.insert(sprites.get(i));
        }
    }

    public TextureAtlasSprite find(float u, float v) {
        TextureAtlasSprite result = this.root.query(u, v);
        return result != null ? result : this.missingSprite;
    }

    public TextureAtlasSprite find(float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        float cu = (u0 + u1 + u2 + u3) * 0.25F;
        float cv = (v0 + v1 + v2 + v3) * 0.25F;
        return this.find(cu, cv);
    }

    private static boolean contains(TextureAtlasSprite sprite, float u, float v) {
        return u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1();
    }

    private static final class Node {
        final float minU;
        final float minV;
        final float maxU;
        final float maxV;
        final float midU;
        final float midV;
        final int depth;

        final List<TextureAtlasSprite> bucket = new ArrayList<>();
        Node @Nullable [] children;

        Node(float minU, float minV, float maxU, float maxV, int depth) {
            this.minU = minU;
            this.minV = minV;
            this.maxU = maxU;
            this.maxV = maxV;
            this.midU = (minU + maxU) * 0.5F;
            this.midV = (minV + maxV) * 0.5F;
            this.depth = depth;
        }

        void insert(TextureAtlasSprite sprite) {
            if (this.children != null) {
                int childIdx = childIndexFor(sprite);
                if (childIdx >= 0) {
                    this.children[childIdx].insert(sprite);
                    return;
                }
                this.bucket.add(sprite);
                return;
            }

            this.bucket.add(sprite);
            if (this.depth < MAX_DEPTH && this.bucket.size() > SPLIT_THRESHOLD) {
                split();
            }
        }

        private void split() {
            this.children = new Node[] {
                new Node(this.minU, this.minV, this.midU, this.midV, this.depth + 1),
                new Node(this.midU, this.minV, this.maxU, this.midV, this.depth + 1),
                new Node(this.minU, this.midV, this.midU, this.maxV, this.depth + 1),
                new Node(this.midU, this.midV, this.maxU, this.maxV, this.depth + 1)
            };

            List<TextureAtlasSprite> retained = new ArrayList<>();
            for (int i = 0; i < this.bucket.size(); i++) {
                TextureAtlasSprite s = this.bucket.get(i);
                int idx = childIndexFor(s);
                if (idx >= 0) {
                    this.children[idx].insert(s);
                } else {
                    retained.add(s);
                }
            }
            this.bucket.clear();
            this.bucket.addAll(retained);
        }

        private int childIndexFor(TextureAtlasSprite s) {
            boolean left = s.getU1() <= this.midU;
            boolean right = s.getU0() >= this.midU;
            boolean top = s.getV1() <= this.midV;
            boolean bottom = s.getV0() >= this.midV;

            if (left && top) return 0;
            if (right && top) return 1;
            if (left && bottom) return 2;
            if (right && bottom) return 3;
            return -1;
        }

        @Nullable TextureAtlasSprite query(float u, float v) {
            if (this.children != null) {
                int idx = (u >= this.midU ? 1 : 0) | (v >= this.midV ? 2 : 0);
                TextureAtlasSprite found = this.children[idx].query(u, v);
                if (found != null) {
                    return found;
                }
            }
            for (int i = 0; i < this.bucket.size(); i++) {
                TextureAtlasSprite s = this.bucket.get(i);
                if (contains(s, u, v)) {
                    return s;
                }
            }
            return null;
        }
    }
}
