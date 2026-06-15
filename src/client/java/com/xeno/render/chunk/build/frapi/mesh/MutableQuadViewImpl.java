package com.xeno.render.chunk.build.frapi.mesh;

/*
 * Original Codebase: Copyright XCollateral (VulkanMod)
 * Refactored Codebase: Copyright ExodusCoder9 (Xeno)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Refactored, Renamed and Optimized by ExodusCoder9.
 */


import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Objects;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import com.xeno.render.chunk.build.frapi.helper.NormalHelper;
import org.jspecify.annotations.Nullable;

public abstract class MutableQuadViewImpl extends QuadViewImpl implements QuadEmitter {
   private static final QuadTransform NO_TRANSFORM = var0x -> true;
   private static final int[] DEFAULT_QUAD_DATA = new int[EncodingFormat.TOTAL_STRIDE];
   private QuadTransform activeTransform = NO_TRANSFORM;
   private final ObjectArrayList<QuadTransform> transformStack = new ObjectArrayList();
   private final QuadTransform stackTransform = q -> {
      int i = this.transformStack.size() - 1;

      while (i >= 0) {
         if (!((QuadTransform)this.transformStack.get(i--)).transform(q)) {
            return false;
         }
      }

      return true;
   };

   public MutableQuadViewImpl() {
   }

   public final MutableQuadViewImpl pos(int vertexIndex, float x, float y, float z) {
      int index = this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_X;
      this.data[index] = Float.floatToRawIntBits(x);
      this.data[index + 1] = Float.floatToRawIntBits(y);
      this.data[index + 2] = Float.floatToRawIntBits(z);
      this.isGeometryInvalid = true;
      return this;
   }

   public final MutableQuadViewImpl translate(float x, float y, float z) {
      // Unrolled: always exactly 4 vertices
      int i0 = this.baseIndex + 0 * 8 + EncodingFormat.VERTEX_X;
      this.data[i0]     = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i0])     + x);
      this.data[i0 + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i0 + 1]) + y);
      this.data[i0 + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i0 + 2]) + z);
      int i1 = this.baseIndex + 1 * 8 + EncodingFormat.VERTEX_X;
      this.data[i1]     = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i1])     + x);
      this.data[i1 + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i1 + 1]) + y);
      this.data[i1 + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i1 + 2]) + z);
      int i2 = this.baseIndex + 2 * 8 + EncodingFormat.VERTEX_X;
      this.data[i2]     = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i2])     + x);
      this.data[i2 + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i2 + 1]) + y);
      this.data[i2 + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i2 + 2]) + z);
      int i3 = this.baseIndex + 3 * 8 + EncodingFormat.VERTEX_X;
      this.data[i3]     = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i3])     + x);
      this.data[i3 + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i3 + 1]) + y);
      this.data[i3 + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(this.data[i3 + 2]) + z);
      return this;
   }

   public final MutableQuadViewImpl color(int vertexIndex, int color) {
      this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_COLOR] = color;
      return this;
   }

   public final MutableQuadViewImpl uv(int vertexIndex, float u, float v) {
      int i = this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_U;
      this.data[i] = Float.floatToRawIntBits(u);
      this.data[i + 1] = Float.floatToRawIntBits(v);
      return this;
   }

   public final MutableQuadViewImpl lightmap(int vertexIndex, int lightmap) {
      this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_LIGHTMAP] = lightmap;
      return this;
   }

   protected final void normalFlags(int flags) {
      this.data[this.baseIndex + 0] = EncodingFormat.normalFlags(this.data[this.baseIndex + 0], flags);
   }

   public final MutableQuadViewImpl normal(int vertexIndex, float x, float y, float z) {
      this.normalFlags(this.normalFlags() | 1 << vertexIndex);
      this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_NORMAL] = NormalHelper.packNormal(x, y, z);
      return this;
   }

   public final void populateMissingNormals() {
      int normalFlags = this.normalFlags();
      if (normalFlags != 15) {
         int packedFaceNormal = this.packedFaceNormal();

         for (int v = 0; v < 4; v++) {
            if ((normalFlags & 1 << v) == 0) {
               this.data[this.baseIndex + v * 8 + EncodingFormat.VERTEX_NORMAL] = packedFaceNormal;
            }
         }

         this.normalFlags(15);
      }
   }

   public final MutableQuadViewImpl nominalFace(@Nullable Direction face) {
      this.nominalFace = face;
      return this;
   }

   public final MutableQuadViewImpl cullFace(@Nullable Direction face) {
      this.data[this.baseIndex + 0] = EncodingFormat.cullFace(this.data[this.baseIndex + 0], face);
      this.nominalFace(face);
      return this;
   }

   public MutableQuadViewImpl chunkLayer(ChunkSectionLayer layer) {
      this.data[this.baseIndex + 0] = EncodingFormat.chunkLayer(this.data[this.baseIndex + 0], layer);
      return this;
   }

   public MutableQuadViewImpl itemRenderType(RenderType renderType) {
      ItemRenderType enumValue = ItemRenderType.RENDER_TYPE_2_ENUM.get(renderType);
      if (enumValue != null) {
         this.data[this.baseIndex + 0] = EncodingFormat.itemRenderType(this.data[this.baseIndex + 0], enumValue);
      }

      return this;
   }

   public MutableQuadViewImpl emissive(boolean emissive) {
      this.data[this.baseIndex + 0] = EncodingFormat.emissive(this.data[this.baseIndex + 0], emissive);
      return this;
   }

   public MutableQuadViewImpl diffuseShade(boolean shade) {
      this.data[this.baseIndex + 0] = EncodingFormat.diffuseShade(this.data[this.baseIndex + 0], shade);
      return this;
   }

   public MutableQuadViewImpl ambientOcclusion(TriState ao) {
      Objects.requireNonNull(ao, "ambient occlusion TriState may not be null");
      this.data[this.baseIndex + 0] = EncodingFormat.ambientOcclusion(this.data[this.baseIndex + 0], ao);
      return this;
   }

   public MutableQuadViewImpl foilType(@Nullable FoilType foilType) {
      this.data[this.baseIndex + 0] = EncodingFormat.foilType(this.data[this.baseIndex + 0], foilType);
      return this;
   }

   public MutableQuadViewImpl shadeMode(ShadeMode mode) {
      Objects.requireNonNull(mode, "ShadeMode may not be null");
      this.data[this.baseIndex + 0] = EncodingFormat.shadeMode(this.data[this.baseIndex + 0], mode);
      return this;
   }

   public MutableQuadViewImpl animated(boolean animated) {
      this.data[this.baseIndex + 0] = EncodingFormat.animated(this.data[this.baseIndex + 0], animated);
      return this;
   }

   public MutableQuadViewImpl atlas(QuadAtlas quadAtlas) {
      this.data[this.baseIndex + 0] = EncodingFormat.quadAtlas(this.data[this.baseIndex + 0], quadAtlas);
      return this;
   }

   public final MutableQuadViewImpl tintIndex(int tintIndex) {
      this.data[this.baseIndex + 2] = tintIndex;
      return this;
   }

   public final MutableQuadViewImpl tag(int tag) {
      this.data[this.baseIndex + 3] = tag;
      return this;
   }

   public final MutableQuadViewImpl copyFrom(QuadView quad) {
      QuadViewImpl q = (QuadViewImpl)quad;
      System.arraycopy(q.data, q.baseIndex, this.data, this.baseIndex, EncodingFormat.TOTAL_STRIDE);
      this.nominalFace = q.nominalFace;
      this.isGeometryInvalid = q.isGeometryInvalid;
      if (!this.isGeometryInvalid) {
         this.faceNormal.set(q.faceNormal);
      }

      return this;
   }

   public final MutableQuadViewImpl fromBakedQuad(BakedQuad quad) {
      MaterialInfo materialInfo = quad.materialInfo();
      this.pos(0, quad.position0());
      this.pos(1, quad.position1());
      this.pos(2, quad.position2());
      this.pos(3, quad.position3());
      this.color(-1, -1, -1, -1);
      long packedUV0 = quad.packedUV0();
      long packedUV1 = quad.packedUV1();
      long packedUV2 = quad.packedUV2();
      long packedUV3 = quad.packedUV3();
      this.uv(0, UVPair.unpackU(packedUV0), UVPair.unpackV(packedUV0));
      this.uv(1, UVPair.unpackU(packedUV1), UVPair.unpackV(packedUV1));
      this.uv(2, UVPair.unpackU(packedUV2), UVPair.unpackV(packedUV2));
      this.uv(3, UVPair.unpackU(packedUV3), UVPair.unpackV(packedUV3));
      int lightEmission = materialInfo.lightEmission();
      int lightmap = LightCoordsUtil.pack(lightEmission, lightEmission);
      this.lightmap(lightmap, lightmap, lightmap, lightmap);
      this.normalFlags(0);
      this.nominalFace(quad.direction());
      QuadAtlas atlas = QuadAtlas.ofLocation(materialInfo.sprite().atlasLocation());
      if (atlas == null) {
         atlas = QuadAtlas.BLOCK;
      }

      this.atlas(atlas);
      this.animated(materialInfo.sprite().contents().isAnimated());
      this.chunkLayer(materialInfo.layer());
      this.itemRenderType(materialInfo.itemRenderType());
      this.tintIndex(materialInfo.tintIndex());
      this.diffuseShade(materialInfo.shade());
      this.emissive(lightEmission == 15);
      return this;
   }

   public final MutableQuadViewImpl clear() {
      System.arraycopy(DEFAULT_QUAD_DATA, 0, this.data, this.baseIndex, EncodingFormat.TOTAL_STRIDE);
      this.isGeometryInvalid = true;
      this.nominalFace = null;
      return this;
   }

   public void pushTransform(QuadTransform transform) {
      if (transform == null) {
         throw new NullPointerException("QuadTransform cannot be null!");
      }

      this.transformStack.push(transform);
      if (this.transformStack.size() == 1) {
         this.activeTransform = transform;
      } else if (this.transformStack.size() == 2) {
         this.activeTransform = this.stackTransform;
      }
   }

   public void popTransform() {
      this.transformStack.pop();
      if (this.transformStack.isEmpty()) {
         this.activeTransform = NO_TRANSFORM;
      } else if (this.transformStack.size() == 1) {
         this.activeTransform = (QuadTransform)this.transformStack.getFirst();
      }
   }

   protected abstract void emitDirectly();

   public final void transformAndEmit() {
      if (this.activeTransform.transform(this)) {
         this.emitDirectly();
      }
   }

   public final MutableQuadViewImpl emit() {
      this.transformAndEmit();
      this.clear();
      return this;
   }

   static {
      MutableQuadViewImpl quad = new MutableQuadViewImpl() {
         @Override
         protected void emitDirectly() {
         }
      };
      quad.data = DEFAULT_QUAD_DATA;
      quad.color(-1, -1, -1, -1);
      quad.cullFace(null);
      quad.chunkLayer(ChunkSectionLayer.CUTOUT);
      quad.itemRenderType(ItemRenderType.DEFAULT.renderType);
      quad.diffuseShade(true);
      quad.ambientOcclusion(TriState.DEFAULT);
      quad.foilType(null);
      quad.tintIndex(-1);
   }
}
