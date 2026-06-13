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


import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import com.xeno.render.chunk.build.frapi.helper.GeometryHelper;
import com.xeno.render.chunk.build.frapi.helper.NormalHelper;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.model.quad.ModelQuadFlags;
import com.xeno.render.model.quad.ModelQuadView;
import com.xeno.vulkan.util.ColorUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public class QuadViewImpl implements QuadView, ModelQuadView {
   @Nullable
   protected Direction nominalFace;
   protected boolean isGeometryInvalid = true;
   protected final Vector3f faceNormal = new Vector3f();
   protected int[] data;
   protected int baseIndex = 0;
   private final Vector4f posVec = new Vector4f();
   private final Vector3f normalVec = new Vector3f();
   private final Vector3f normalVec1 = new Vector3f();
   protected QuadFacing facing;

   public QuadViewImpl() {
   }

   public final void load() {
      this.isGeometryInvalid = false;
      this.nominalFace = this.lightFace();
      NormalHelper.unpackNormal(this.packedFaceNormal(), this.faceNormal);
      this.facing = QuadFacing.fromNormal(this.faceNormal);
   }

   protected final void computeGeometry() {
      if (this.isGeometryInvalid) {
         this.isGeometryInvalid = false;
         NormalHelper.computeFaceNormal(this.faceNormal, this);
         this.data[this.baseIndex + 1] = NormalHelper.packNormal(this.faceNormal);
         Direction lightFace = GeometryHelper.lightFace(this);
         this.data[this.baseIndex + 0] = EncodingFormat.lightFace(this.data[this.baseIndex + 0], lightFace);
         this.data[this.baseIndex + 0] = EncodingFormat.geometryFlags(this.data[this.baseIndex + 0], ModelQuadFlags.getQuadFlags(this, lightFace));
         this.facing = QuadFacing.fromNormal(this.faceNormal);
      }
   }

   public final int geometryFlags() {
      this.computeGeometry();
      return EncodingFormat.geometryFlags(this.data[this.baseIndex + 0]);
   }

   public final float x(int vertexIndex) {
      return Float.intBitsToFloat(this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_X]);
   }

   public final float y(int vertexIndex) {
      return Float.intBitsToFloat(this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_Y]);
   }

   public final float z(int vertexIndex) {
      return Float.intBitsToFloat(this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_Z]);
   }

   public final float posByIndex(int vertexIndex, int coordinateIndex) {
      return Float.intBitsToFloat(this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_X + coordinateIndex]);
   }

   public final Vector3f copyPos(int vertexIndex, @Nullable Vector3f target) {
      if (target == null) {
         target = new Vector3f();
      }

      int index = this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_X;
      target.set(Float.intBitsToFloat(this.data[index]), Float.intBitsToFloat(this.data[index + 1]), Float.intBitsToFloat(this.data[index + 2]));
      return target;
   }

   public final int color(int vertexIndex) {
      return this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_COLOR];
   }

   public final float u(int vertexIndex) {
      return Float.intBitsToFloat(this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_U]);
   }

   public final float v(int vertexIndex) {
      return Float.intBitsToFloat(this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_V]);
   }

   public final Vector2f copyUv(int vertexIndex, @Nullable Vector2f target) {
      if (target == null) {
         target = new Vector2f();
      }

      int index = this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_U;
      target.set(Float.intBitsToFloat(this.data[index]), Float.intBitsToFloat(this.data[index + 1]));
      return target;
   }

   public final int lightmap(int vertexIndex) {
      return this.data[this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_LIGHTMAP];
   }

   public final int normalFlags() {
      return EncodingFormat.normalFlags(this.data[this.baseIndex + 0]);
   }

   public final boolean hasNormal(int vertexIndex) {
      return (this.normalFlags() & 1 << vertexIndex) != 0;
   }

   public final boolean hasVertexNormals() {
      return this.normalFlags() != 0;
   }

   public final boolean hasAllVertexNormals() {
      return (this.normalFlags() & 15) == 15;
   }

   protected final int normalIndex(int vertexIndex) {
      return this.baseIndex + vertexIndex * 8 + EncodingFormat.VERTEX_NORMAL;
   }

   public final float normalX(int vertexIndex) {
      return this.hasNormal(vertexIndex) ? NormalHelper.unpackNormalX(this.data[this.normalIndex(vertexIndex)]) : Float.NaN;
   }

   public final float normalY(int vertexIndex) {
      return this.hasNormal(vertexIndex) ? NormalHelper.unpackNormalY(this.data[this.normalIndex(vertexIndex)]) : Float.NaN;
   }

   public final float normalZ(int vertexIndex) {
      return this.hasNormal(vertexIndex) ? NormalHelper.unpackNormalZ(this.data[this.normalIndex(vertexIndex)]) : Float.NaN;
   }

   @Nullable
   public final Vector3f copyNormal(int vertexIndex, @Nullable Vector3f target) {
      if (this.hasNormal(vertexIndex)) {
         if (target == null) {
            target = new Vector3f();
         }

         int normal = this.data[this.normalIndex(vertexIndex)];
         NormalHelper.unpackNormal(normal, target);
         return target;
      } else {
         return null;
      }
   }

   public final int packedFaceNormal() {
      this.computeGeometry();
      return this.data[this.baseIndex + 1];
   }

   public final Vector3fc faceNormal() {
      this.computeGeometry();
      return this.faceNormal;
   }

   @Override
   public final Direction lightFace() {
      this.computeGeometry();
      return EncodingFormat.lightFace(this.data[this.baseIndex + 0]);
   }

   @Nullable
   public final Direction nominalFace() {
      return this.nominalFace;
   }

   @Nullable
   public final Direction cullFace() {
      return EncodingFormat.cullFace(this.data[this.baseIndex + 0]);
   }

   public QuadAtlas atlas() {
      return EncodingFormat.quadAtlas(this.data[this.baseIndex + 0]);
   }

   public ChunkSectionLayer chunkLayer() {
      return EncodingFormat.chunkLayer(this.data[this.baseIndex + 0]);
   }

   public RenderType itemRenderType() {
      return EncodingFormat.itemRenderType(this.data[this.baseIndex + 0]);
   }

   public boolean emissive() {
      return EncodingFormat.emissive(this.data[this.baseIndex + 0]);
   }

   public boolean diffuseShade() {
      return EncodingFormat.diffuseShade(this.data[this.baseIndex + 0]);
   }

   public TriState ambientOcclusion() {
      return EncodingFormat.ambientOcclusion(this.data[this.baseIndex + 0]);
   }

   public @Nullable FoilType foilType() {
      return EncodingFormat.foilType(this.data[this.baseIndex + 0]);
   }

   public ShadeMode shadeMode() {
      return EncodingFormat.shadeMode(this.data[this.baseIndex + 0]);
   }

   public boolean animated() {
      return false;
   }

   public final int tintIndex() {
      return this.data[this.baseIndex + 2];
   }

   public final int tag() {
      return this.data[this.baseIndex + 3];
   }

   public final void buffer(int overlayCoords, VertexConsumer vertexConsumer) {
      if (!this.hasVertexNormals()) {
         Vector3fc faceNormal = this.faceNormal();

         for (int i = 0; i < 4; i++) {
            vertexConsumer.addVertex(
               this.x(i),
               this.y(i),
               this.z(i),
               this.color(i),
               this.u(i),
               this.v(i),
               overlayCoords,
               this.lightmap(i),
               faceNormal.x(),
               faceNormal.y(),
               faceNormal.z()
            );
         }
      } else if (this.hasAllVertexNormals()) {
         Vector3f normalVec = this.normalVec;

         for (int i = 0; i < 4; i++) {
            this.copyNormal(i, normalVec);
            vertexConsumer.addVertex(
               this.x(i),
               this.y(i),
               this.z(i),
               this.color(i),
               this.u(i),
               this.v(i),
               overlayCoords,
               this.lightmap(i),
               normalVec.x(),
               normalVec.y(),
               normalVec.z()
            );
         }
      } else {
         Vector3f normalVec = this.normalVec;
         Vector3fc faceNormal = this.faceNormal();

         for (int i = 0; i < 4; i++) {
            if (this.hasNormal(i)) {
               this.copyNormal(i, normalVec);
            } else {
               normalVec.set(faceNormal);
            }

            vertexConsumer.addVertex(
               this.x(i),
               this.y(i),
               this.z(i),
               this.color(i),
               this.u(i),
               this.v(i),
               overlayCoords,
               this.lightmap(i),
               normalVec.x(),
               normalVec.y(),
               normalVec.z()
            );
         }
      }
   }

   public final void buffer(int overlayCoords, Pose pose, VertexConsumer vertexConsumer) {
      Vector4f posVec = this.posVec;
      Vector3f normalVec = this.normalVec;
      Matrix4f posMatrix = pose.pose();
      if (!this.hasVertexNormals()) {
         pose.transformNormal(this.faceNormal(), normalVec);

         for (int i = 0; i < 4; i++) {
            posVec.set(this.x(i), this.y(i), this.z(i), 1.0F);
            posVec.mul(posMatrix);
            int vertexColor = ColorUtil.RGBA.fromArgb32(this.color(i));
            vertexConsumer.addVertex(
               posVec.x(),
               posVec.y(),
               posVec.z(),
               vertexColor,
               this.u(i),
               this.v(i),
               overlayCoords,
               this.lightmap(i),
               normalVec.x(),
               normalVec.y(),
               normalVec.z()
            );
         }
      } else if (this.hasAllVertexNormals()) {
         for (int i = 0; i < 4; i++) {
            posVec.set(this.x(i), this.y(i), this.z(i), 1.0F);
            posVec.mul(posMatrix);
            this.copyNormal(i, normalVec);
            pose.transformNormal(normalVec, normalVec);
            int vertexColor = ColorUtil.RGBA.fromArgb32(this.color(i));
            vertexConsumer.addVertex(
               posVec.x(),
               posVec.y(),
               posVec.z(),
               vertexColor,
               this.u(i),
               this.v(i),
               overlayCoords,
               this.lightmap(i),
               normalVec.x(),
               normalVec.y(),
               normalVec.z()
            );
         }
      } else {
         Vector3f transformedFaceNormal = pose.transformNormal(this.faceNormal(), this.normalVec1);

         for (int i = 0; i < 4; i++) {
            posVec.set(this.x(i), this.y(i), this.z(i), 1.0F);
            posVec.mul(posMatrix);
            if (this.hasNormal(i)) {
               this.copyNormal(i, normalVec);
               pose.transformNormal(normalVec, normalVec);
            } else {
               normalVec.set(transformedFaceNormal);
            }

            int vertexColor = ColorUtil.RGBA.fromArgb32(this.color(i));
            vertexConsumer.addVertex(
               posVec.x(),
               posVec.y(),
               posVec.z(),
               vertexColor,
               this.u(i),
               this.v(i),
               overlayCoords,
               this.lightmap(i),
               normalVec.x(),
               normalVec.y(),
               normalVec.z()
            );
         }
      }
   }

   @Override
   public int getFlags() {
      return this.geometryFlags();
   }

   @Override
   public float getX(int idx) {
      return this.x(idx);
   }

   @Override
   public float getY(int idx) {
      return this.y(idx);
   }

   @Override
   public float getZ(int idx) {
      return this.z(idx);
   }

   @Override
   public int getColor(int idx) {
      return this.color(idx);
   }

   @Override
   public float getU(int idx) {
      return this.u(idx);
   }

   @Override
   public float getV(int idx) {
      return this.v(idx);
   }

   @Override
   public int getColorIndex() {
      return this.tintIndex();
   }

   @Override
   public Direction getFacingDirection() {
      return this.lightFace();
   }

   @Override
   public int getNormal() {
      return this.packedFaceNormal();
   }

   @Override
   public QuadFacing getQuadFacing() {
      return this.facing;
   }
}
