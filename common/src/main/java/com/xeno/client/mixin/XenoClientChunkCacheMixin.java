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

import com.xeno.client.common.render.chunk.XenoWorldRenderManager;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
public abstract class XenoClientChunkCacheMixin {
	@Inject(
		method = "replaceWithPacketData",
		at = @At("RETURN")
	)
	private void xeno$onChunkLoaded(int chunkX, int chunkZ, FriendlyByteBuf readBuffer, Map<Heightmap.Types, long[]> heightmaps, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities, CallbackInfoReturnable<LevelChunk> cir) {
		if (cir.getReturnValue() != null) {
			XenoWorldRenderManager.INSTANCE.onChunkLoaded(new ChunkPos(chunkX, chunkZ));
		}
	}

	@Inject(
		method = "drop",
		at = @At("RETURN")
	)
	private void xeno$onChunkDropped(ChunkPos pos, CallbackInfo ci) {
		ClientChunkCache self = (ClientChunkCache)(Object)this;
		if (self.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false) == null) {
			XenoWorldRenderManager.INSTANCE.onChunkUnloaded(pos);
		}
	}

	@Inject(
		method = "updateViewRadius",
		at = @At("TAIL")
	)
	private void xeno$onViewRadiusChanged(int viewRange, CallbackInfo ci) {
		XenoWorldRenderManager.INSTANCE.reconcileWithChunkSource();
	}
}
