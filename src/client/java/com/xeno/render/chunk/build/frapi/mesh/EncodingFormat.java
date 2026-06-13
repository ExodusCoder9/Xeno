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


import com.google.common.base.Preconditions;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.client.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;

public final class EncodingFormat {
   static final int HEADER_BITS = 0;
   static final int HEADER_FACE_NORMAL = 1;
   static final int HEADER_TINT_INDEX = 2;
   static final int HEADER_TAG = 3;
   public static final int HEADER_STRIDE = 4;
   static final int VERTEX_X = 4;
   static final int VERTEX_Y = VERTEX_X + 1;
   static final int VERTEX_Z = VERTEX_Y + 1;
   static final int VERTEX_COLOR = VERTEX_Z + 1;
   static final int VERTEX_U = VERTEX_COLOR + 1;
   static final int VERTEX_V = VERTEX_U + 1;
   static final int VERTEX_LIGHTMAP = VERTEX_V + 1;
   static final int VERTEX_NORMAL = VERTEX_LIGHTMAP + 1;
   private static final int VERTEX_POSITION_BYTES = 4;
   private static final int VERTEX_COLOR_BYTES = 4;
   private static final int VERTEX_UV_BYTES = 4;
   private static final int VERTEX_LIGHTMAP_BYTES = 4;
   private static final int VERTEX_NORMAL_BYTES = 4;
   private static final int VERTEX_COLOR_SIZE = 1;
   private static final int VERTEX_UV_SIZE = 1;
   private static final int VERTEX_LIGHTMAP_SIZE = 1;
   private static final int VERTEX_NORMAL_SIZE = 1;
   private static final int VERTEX_POSITION_SIZE = 1;
   public static final int VERTEX_STRIDE = 8;
   public static final int QUAD_STRIDE = 32;
   public static final int QUAD_STRIDE_BYTES = QUAD_STRIDE * 4;
   public static final int TOTAL_STRIDE = 4 + QUAD_STRIDE;
   private static final int DIRECTION_COUNT = Direction.values().length;
   private static final int NULLABLE_DIRECTION_COUNT = DIRECTION_COUNT + 1;
   private static final ChunkSectionLayer[] CHUNK_SECTION_LAYERS = ChunkSectionLayer.values();
   private static final int CHUNK_SECTION_LAYER_COUNT = CHUNK_SECTION_LAYERS.length;
   private static final RenderType[] ITEM_RENDER_TYPES = ItemRenderType.RENDER_TYPES;
   private static final int ITEM_RENDER_TYPE_COUNT = ITEM_RENDER_TYPES.length;
   private static final TriState[] TRI_STATES = TriState.values();
   private static final int TRI_STATE_COUNT = TRI_STATES.length;
   private static final @Nullable FoilType[] NULLABLE_FOIL_TYPES = (FoilType[])ArrayUtils.add(FoilType.values(), null);
   private static final int NULLABLE_FOIL_TYPE_COUNT = NULLABLE_FOIL_TYPES.length;
   private static final ShadeMode[] SHADE_MODES = ShadeMode.values();
   private static final int SHADE_MODE_COUNT = SHADE_MODES.length;
   private static final QuadAtlas[] QUAD_ATLASES = QuadAtlas.values();
   private static final int QUAD_ATLAS_COUNT = QUAD_ATLASES.length;
   private static final int NULL_FOIL_TYPE_INDEX = NULLABLE_FOIL_TYPE_COUNT - 1;
   private static final int CULL_BIT_LENGTH = Mth.ceillog2(NULLABLE_DIRECTION_COUNT);
   private static final int LIGHT_BIT_LENGTH = Mth.ceillog2(DIRECTION_COUNT);
   private static final int NORMALS_BIT_LENGTH = 4;
   private static final int GEOMETRY_BIT_LENGTH = 3;
   private static final int QUAD_ATLAS_BIT_LENGTH = Mth.ceillog2(QUAD_ATLAS_COUNT);
   private static final int CHUNK_LAYER_BIT_LENGTH = Mth.ceillog2(CHUNK_SECTION_LAYER_COUNT);
   private static final int ITEM_RENDER_TYPE_BIT_LENGTH = Mth.ceillog2(ITEM_RENDER_TYPE_COUNT);
   private static final int EMISSIVE_BIT_LENGTH = 1;
   private static final int DIFFUSE_BIT_LENGTH = 1;
   private static final int AO_BIT_LENGTH = Mth.ceillog2(TRI_STATE_COUNT);
   private static final int FOIL_TYPE_BIT_LENGTH = Mth.ceillog2(NULLABLE_FOIL_TYPE_COUNT);
   private static final int SHADE_MODE_BIT_LENGTH = Mth.ceillog2(SHADE_MODE_COUNT);
   private static final int ANIMATED_BIT_LENGTH = 1;
   private static final int CULL_BIT_OFFSET = 0;
   private static final int LIGHT_BIT_OFFSET = 0 + CULL_BIT_LENGTH;
   private static final int NORMALS_BIT_OFFSET = LIGHT_BIT_OFFSET + LIGHT_BIT_LENGTH;
   private static final int GEOMETRY_BIT_OFFSET = NORMALS_BIT_OFFSET + 4;
   private static final int QUAD_ATLAS_BIT_OFFSET = GEOMETRY_BIT_OFFSET + 3;
   private static final int CHUNK_LAYER_BIT_OFFSET = QUAD_ATLAS_BIT_OFFSET + QUAD_ATLAS_BIT_LENGTH;
   private static final int ITEM_RENDER_TYPE_BIT_OFFSET = CHUNK_LAYER_BIT_OFFSET + CHUNK_LAYER_BIT_LENGTH;
   private static final int EMISSIVE_BIT_OFFSET = ITEM_RENDER_TYPE_BIT_OFFSET + ITEM_RENDER_TYPE_BIT_LENGTH;
   private static final int DIFFUSE_BIT_OFFSET = EMISSIVE_BIT_OFFSET + 1;
   private static final int AO_BIT_OFFSET = DIFFUSE_BIT_OFFSET + 1;
   private static final int FOIL_TYPE_BIT_OFFSET = AO_BIT_OFFSET + AO_BIT_LENGTH;
   private static final int SHADE_MODE_BIT_OFFSET = FOIL_TYPE_BIT_OFFSET + FOIL_TYPE_BIT_LENGTH;
   private static final int ANIMATED_BIT_OFFSET = SHADE_MODE_BIT_OFFSET + SHADE_MODE_BIT_LENGTH;
   private static final int TOTAL_BIT_LENGTH = ANIMATED_BIT_OFFSET + 1;
   private static final int CULL_MASK = bitMask(CULL_BIT_LENGTH, 0);
   private static final int LIGHT_MASK = bitMask(LIGHT_BIT_LENGTH, LIGHT_BIT_OFFSET);
   private static final int NORMALS_MASK = bitMask(4, NORMALS_BIT_OFFSET);
   private static final int GEOMETRY_MASK = bitMask(3, GEOMETRY_BIT_OFFSET);
   private static final int QUAD_ATLAS_MASK = bitMask(QUAD_ATLAS_BIT_LENGTH, QUAD_ATLAS_BIT_OFFSET);
   private static final int CHUNK_LAYER_MASK = bitMask(CHUNK_LAYER_BIT_LENGTH, CHUNK_LAYER_BIT_OFFSET);
   private static final int ITEM_RENDER_TYPE_MASK = bitMask(ITEM_RENDER_TYPE_BIT_LENGTH, ITEM_RENDER_TYPE_BIT_OFFSET);
   private static final int EMISSIVE_MASK = bitMask(1, EMISSIVE_BIT_OFFSET);
   private static final int DIFFUSE_MASK = bitMask(1, DIFFUSE_BIT_OFFSET);
   private static final int AO_MASK = bitMask(AO_BIT_LENGTH, AO_BIT_OFFSET);
   private static final int FOIL_TYPE_MASK = bitMask(FOIL_TYPE_BIT_LENGTH, FOIL_TYPE_BIT_OFFSET);
   private static final int SHADE_MODE_MASK = bitMask(SHADE_MODE_BIT_LENGTH, SHADE_MODE_BIT_OFFSET);
   private static final int ANIMATED_MASK = bitMask(1, ANIMATED_BIT_OFFSET);

   private EncodingFormat() {
   }

   private static int bitMask(int bitLength, int bitOffset) {
      return (1 << bitLength) - 1 << bitOffset;
   }

   static @Nullable Direction cullFace(int bits) {
      return ModelHelper.faceFromIndex((bits & CULL_MASK) >>> 0);
   }

   static int cullFace(int bits, @Nullable Direction face) {
      return bits & ~CULL_MASK | ModelHelper.toFaceIndex(face) << 0;
   }

   static Direction lightFace(int bits) {
      return ModelHelper.faceFromIndex((bits & LIGHT_MASK) >>> LIGHT_BIT_OFFSET);
   }

   static int lightFace(int bits, Direction face) {
      return bits & ~LIGHT_MASK | ModelHelper.toFaceIndex(face) << LIGHT_BIT_OFFSET;
   }

   static int normalFlags(int bits) {
      return (bits & NORMALS_MASK) >>> NORMALS_BIT_OFFSET;
   }

   static int normalFlags(int bits, int normalFlags) {
      return bits & ~NORMALS_MASK | normalFlags << NORMALS_BIT_OFFSET & NORMALS_MASK;
   }

   static int geometryFlags(int bits) {
      return (bits & GEOMETRY_MASK) >>> GEOMETRY_BIT_OFFSET;
   }

   static int geometryFlags(int bits, int geometryFlags) {
      return bits & ~GEOMETRY_MASK | geometryFlags << GEOMETRY_BIT_OFFSET & GEOMETRY_MASK;
   }

   static QuadAtlas quadAtlas(int bits) {
      return QUAD_ATLASES[(bits & QUAD_ATLAS_MASK) >>> QUAD_ATLAS_BIT_OFFSET];
   }

   static int quadAtlas(int bits, QuadAtlas quadAtlas) {
      return bits & ~QUAD_ATLAS_MASK | quadAtlas.ordinal() << QUAD_ATLAS_BIT_OFFSET;
   }

   static ChunkSectionLayer chunkLayer(int bits) {
      return CHUNK_SECTION_LAYERS[(bits & CHUNK_LAYER_MASK) >>> CHUNK_LAYER_BIT_OFFSET];
   }

   static int chunkLayer(int bits, ChunkSectionLayer layer) {
      return bits & ~CHUNK_LAYER_MASK | layer.ordinal() << CHUNK_LAYER_BIT_OFFSET;
   }

   static RenderType itemRenderType(int bits) {
      return ITEM_RENDER_TYPES[(bits & ITEM_RENDER_TYPE_MASK) >>> ITEM_RENDER_TYPE_BIT_OFFSET];
   }

   static int itemRenderType(int bits, ItemRenderType renderType) {
      return bits & ~ITEM_RENDER_TYPE_MASK | renderType.ordinal() << ITEM_RENDER_TYPE_BIT_OFFSET;
   }

   static boolean emissive(int bits) {
      return (bits & EMISSIVE_MASK) != 0;
   }

   static int emissive(int bits, boolean emissive) {
      return emissive ? bits | EMISSIVE_MASK : bits & ~EMISSIVE_MASK;
   }

   static boolean diffuseShade(int bits) {
      return (bits & DIFFUSE_MASK) != 0;
   }

   static int diffuseShade(int bits, boolean shade) {
      return shade ? bits | DIFFUSE_MASK : bits & ~DIFFUSE_MASK;
   }

   static TriState ambientOcclusion(int bits) {
      return TRI_STATES[(bits & AO_MASK) >>> AO_BIT_OFFSET];
   }

   static int ambientOcclusion(int bits, TriState ao) {
      return bits & ~AO_MASK | ao.ordinal() << AO_BIT_OFFSET;
   }

   static @Nullable FoilType foilType(int bits) {
      return NULLABLE_FOIL_TYPES[(bits & FOIL_TYPE_MASK) >>> FOIL_TYPE_BIT_OFFSET];
   }

   static int foilType(int bits, @Nullable FoilType foilType) {
      int index = foilType == null ? NULL_FOIL_TYPE_INDEX : foilType.ordinal();
      return bits & ~FOIL_TYPE_MASK | index << FOIL_TYPE_BIT_OFFSET;
   }

   static ShadeMode shadeMode(int bits) {
      return SHADE_MODES[(bits & SHADE_MODE_MASK) >>> SHADE_MODE_BIT_OFFSET];
   }

   static int shadeMode(int bits, ShadeMode mode) {
      return bits & ~SHADE_MODE_MASK | mode.ordinal() << SHADE_MODE_BIT_OFFSET;
   }

   static boolean animated(int bits) {
      return (bits & ANIMATED_MASK) != 0;
   }

   static int animated(int bits, boolean animated) {
      return animated ? bits | ANIMATED_MASK : bits & ~ANIMATED_MASK;
   }

   static {
      Preconditions.checkArgument(TOTAL_BIT_LENGTH <= 32, "Indigo header encoding bit count (%s) exceeds integer bit length)", TOTAL_STRIDE);
   }
}
