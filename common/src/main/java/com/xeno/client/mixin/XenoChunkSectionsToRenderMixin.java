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

package com.xeno.client.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import com.xeno.client.common.render.XenoChunkRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ChunkSectionsToRender.class)
public abstract class XenoChunkSectionsToRenderMixin {
	/**
	 * @author ExodusCoder9
	 * @reason Route chunk draw call submission through the Xeno .
	 */
	@Overwrite
	public void renderGroup(ChunkSectionLayerGroup group, GpuSampler sampler) {
		XenoChunkRenderer.INSTANCE.renderChunks((ChunkSectionsToRender) (Object) this, group, sampler);
	}
}
