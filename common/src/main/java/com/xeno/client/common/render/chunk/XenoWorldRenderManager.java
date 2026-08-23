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

package com.xeno.client.common.render.chunk;

import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class XenoWorldRenderManager {
	public static final XenoWorldRenderManager INSTANCE = new XenoWorldRenderManager();
	public static final int REGION_SECTIONS_XZ = 8;
	public static final int REGION_SECTIONS_Y = 8;
	private static final int VIEW_MARGIN_BLOCKS = 64;
	private static final long RELEASE_GRACE_FRAMES = 200L;
	// The executor self-limits its CPU per frame window; this only bounds the submit queue.
	private static final int MAX_COMPILES_PER_FRAME = 256;
	private static final int MAX_PEEKED_CANDIDATES_PER_REGION = 512;
	public static final int REGION_SIZE_BLOCKS = REGION_SECTIONS_XZ * 16;
	private final Object lock = new Object();
	private final Long2ObjectOpenHashMap<XenoRenderRegion> regions = new Long2ObjectOpenHashMap<>();
	private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
	private @Nullable ClientLevel level;
	private long frameIndex;
	private int cameraSectionX;
	private int cameraSectionZ;
	private long totalChunkLoads;
	private long totalChunkUnloads;
	private long totalDirtySections;
	private long totalCompilesSubmitted;
	private long regionsCreated;
	private long regionsReleased;
	private boolean cachedAmbientOcclusion;
	private boolean cachedCutoutLeaves;
	private @Nullable BlockStateModelSet cachedBlockModelSet;
	private @Nullable FluidStateModelSet cachedFluidModelSet;
	private @Nullable BlockColors cachedBlockColors;
	private @Nullable SectionCompiler cachedCompiler;
	private final List<XenoRenderRegion> dirtyRegionScratch = new ArrayList<>();
	private final List<int[]> peekedScratch = new ArrayList<>();
	private final RenderRegionCache snapshotCache = new RenderRegionCache();

	private XenoWorldRenderManager() {
	}

	public void onLevelSet(@Nullable ClientLevel level) {
		synchronized (this.lock) {
			this.resetState();
			this.loadedChunks.clear();
			this.level = level;
			this.cachedCompiler = null;
		}

		XenoRegionCompiler.LOGGER.info("Level bound: {}", level != null ? String.valueOf(level.dimension()) : "<null>");
	}

	public void onRenderPipelineReset() {
		synchronized (this.lock) {
			this.resetState();
			this.rebuildRegionsForLoadedChunks();
			for (long packed : this.loadedChunks) {
				int chunkX = ChunkPos.getX(packed);
				int chunkZ = ChunkPos.getZ(packed);
				this.markArrivalRangeDirty(chunkX, chunkZ);
			}
		}

		XenoRegionCompiler.LOGGER.info("Render pipeline reset: all regions evicted");
	}

	public void onChunkLoaded(ChunkPos pos) {
		synchronized (this.lock) {
			boolean fresh = this.loadedChunks.add(ChunkPos.pack(pos.x(), pos.z()));
			if (fresh) {
				this.totalChunkLoads++;
				this.createRegionColumn(Math.floorDiv(pos.x(), REGION_SECTIONS_XZ), Math.floorDiv(pos.z(), REGION_SECTIONS_XZ));
			}

			// Always mark on every redelivery, re-sent chunks may have been evicted from view
			// radius changes or hold stale compiled state, and their sections must recompile even
			// though they were already tracked.
			this.markArrivalRangeDirty(pos.x(), pos.z());
		}
	}

	private void markArrivalRangeDirty(int chunkX, int chunkZ) {
		ClientLevel lvl = this.level;
		if (lvl == null) {
			return;
		}

		int minSectionY = lvl.getMinSectionY();
		int maxSectionY = lvl.getMaxSectionY();
		for (int deltaX = -1; deltaX <= 1; deltaX++) {
			for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
				int x = chunkX + deltaX;
				int z = chunkZ + deltaZ;
				if ((deltaX != 0 || deltaZ != 0) && !this.loadedChunks.contains(ChunkPos.pack(x, z))) {
					continue;
				}

				for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
					this.markSectionLocked(x, sectionY, z, false);
				}
			}
		}
	}

	private void createRegionColumn(int regionX, int regionZ) {
		forEachRegionY(this.level, regionY -> {
			long key = XenoRenderRegion.key(regionX, regionY, regionZ);
			XenoRenderRegion region = this.regions.get(key);
			if (region == null) {
				region = new XenoRenderRegion(regionX, regionY, regionZ);
				this.regions.put(key, region);
				this.regionsCreated++;
			}

			region.incrementLoadedChunkCount();
		});
	}

	private void rebuildRegionsForLoadedChunks() {
		LongIterator iterator = this.loadedChunks.iterator();
		while (iterator.hasNext()) {
			long packed = iterator.nextLong();
			this.createRegionColumn(Math.floorDiv(ChunkPos.getX(packed), REGION_SECTIONS_XZ), Math.floorDiv(ChunkPos.getZ(packed), REGION_SECTIONS_XZ));
		}
	}

	public void onChunkUnloaded(ChunkPos pos) {
		synchronized (this.lock) {
			if (!this.loadedChunks.remove(ChunkPos.pack(pos.x(), pos.z()))) {
				return;
			}

			this.totalChunkUnloads++;
			this.decrementRegionColumn(Math.floorDiv(pos.x(), REGION_SECTIONS_XZ), Math.floorDiv(pos.z(), REGION_SECTIONS_XZ));
		}
	}

	public void reconcileWithChunkSource() {
		ClientLevel lvl = this.level;
		Minecraft minecraft = Minecraft.getInstance();
		if (lvl == null || minecraft.level != lvl) {
			return;
		}

		synchronized (this.lock) {
			LongIterator iterator = this.loadedChunks.iterator();
			while (iterator.hasNext()) {
				long packed = iterator.nextLong();
				int chunkX = ChunkPos.getX(packed);
				int chunkZ = ChunkPos.getZ(packed);
				if (lvl.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
					iterator.remove();
					this.totalChunkUnloads++;
					this.decrementRegionColumn(Math.floorDiv(chunkX, REGION_SECTIONS_XZ), Math.floorDiv(chunkZ, REGION_SECTIONS_XZ));
				}
			}
		}
	}

	private void decrementRegionColumn(int regionX, int regionZ) {
		forEachRegionY(this.level, regionY -> {
			XenoRenderRegion region = this.regions.get(XenoRenderRegion.key(regionX, regionY, regionZ));
			if (region != null) {
				region.decrementLoadedChunkCount();
			}
		});
	}

	public void onSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
		synchronized (this.lock) {
			this.totalDirtySections++;
			XenoRenderRegion region = this.regionForLocked(sectionX, sectionY, sectionZ);
			if (region == null) {
				return;
			}

			int localIndex = region.localIndexOf(sectionX, sectionY, sectionZ);
			if (localIndex >= 0) {
				region.markSectionDirty(localIndex, playerChanged);
			}
		}
	}

	private void markSectionLocked(int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
		XenoRenderRegion region = this.regionForLocked(sectionX, sectionY, sectionZ);
		if (region == null) {
			return;
		}

		int localIndex = region.localIndexOf(sectionX, sectionY, sectionZ);
		if (localIndex >= 0) {
			region.markSectionDirty(localIndex, playerChanged);
		}
	}

	private @Nullable XenoRenderRegion regionForLocked(int sectionX, int sectionY, int sectionZ) {
		XenoRenderRegion region = this.regions.get(
			XenoRenderRegion.key(
				Math.floorDiv(sectionX, REGION_SECTIONS_XZ),
				Math.floorDiv(sectionY, REGION_SECTIONS_Y),
				Math.floorDiv(sectionZ, REGION_SECTIONS_XZ)
			)
		);
		return region != null && region.alive.get() ? region : null;
	}

	public record ResolvedSection(XenoRenderRegion region, int localIndex) {
	}

	public @Nullable ResolvedSection resolveSection(long sectionNode) {
		int sectionX = SectionPos.x(sectionNode);
		int sectionY = SectionPos.y(sectionNode);
		int sectionZ = SectionPos.z(sectionNode);
		XenoRenderRegion region;
		synchronized (this.lock) {
			region = this.regionForLocked(sectionX, sectionY, sectionZ);
		}

		if (region == null) {
			return null;
		}

		int localIndex = region.localIndexOf(sectionX, sectionY, sectionZ);
		return localIndex >= 0 ? new ResolvedSection(region, localIndex) : null;
	}

	public void onCameraSectionChanged(SectionPos cameraSection) {
		synchronized (this.lock) {
			this.cameraSectionX = cameraSection.x();
			this.cameraSectionZ = cameraSection.z();
		}
	}

	public void endExtractFrame() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel lvl = this.level;
		if (lvl == null || minecraft.options == null || minecraft.level != lvl) {
			return;
		}

		synchronized (this.lock) {
			this.frameIndex++;
			int camRegionX = Math.floorDiv(this.cameraSectionX, REGION_SECTIONS_XZ);
			int camRegionZ = Math.floorDiv(this.cameraSectionZ, REGION_SECTIONS_XZ);
			int halfExtentRegions = Math.floorDiv(
				minecraft.options.getEffectiveRenderDistance() * 16 + VIEW_MARGIN_BLOCKS, REGION_SIZE_BLOCKS
			);
			int hysteresisExtent = halfExtentRegions + 1;

			ObjectIterator<Long2ObjectMap.Entry<XenoRenderRegion>> iterator = this.regions.long2ObjectEntrySet().fastIterator();
			while (iterator.hasNext()) {
				Long2ObjectMap.Entry<XenoRenderRegion> entry = iterator.next();
				XenoRenderRegion region = entry.getValue();
				int deltaX = Math.abs(region.regionX() - camRegionX);
				int deltaZ = Math.abs(region.regionZ() - camRegionZ);
				if (deltaX <= halfExtentRegions && deltaZ <= halfExtentRegions) {
					region.markSeen(this.frameIndex);
				} else if (
					(deltaX > hysteresisExtent || deltaZ > hysteresisExtent)
						&& region.isUnused()
						&& this.frameIndex - region.lastSeenFrame() > RELEASE_GRACE_FRAMES
				) {
					XenoRegionCompiler.INSTANCE.releaseRegion(region);
					iterator.remove();
					this.regionsReleased++;
				}
			}

			this.scheduleCompiles(minecraft, lvl, camRegionX, camRegionZ);
		}
	}

	private void scheduleCompiles(Minecraft minecraft, ClientLevel lvl, int camRegionX, int camRegionZ) {
		List<XenoRenderRegion> dirty = this.dirtyRegionScratch;
		dirty.clear();
		for (XenoRenderRegion region : this.regions.values()) {
			if (region.hasDirtySections()) {
				dirty.add(region);
			}
		}

		if (dirty.isEmpty()) {
			return;
		}

		Vec3 cameraPos = minecraft.gameRenderer.mainCamera().position();
		float camCenterX = SectionPos.sectionToBlockCoord(camRegionX) + REGION_SIZE_BLOCKS / 2.0F;
		float camCenterZ = SectionPos.sectionToBlockCoord(camRegionZ) + REGION_SIZE_BLOCKS / 2.0F;
		dirty.sort((a, b) -> Float.compare(regionDistanceSquared(a, camCenterX, camCenterZ), regionDistanceSquared(b, camCenterX, camCenterZ)));

		int budget = MAX_COMPILES_PER_FRAME;
		XenoRegionCompiler.INSTANCE.setCompiler(this.acquireCompiler(minecraft));
		for (XenoRenderRegion region : dirty) {
			if (budget <= 0) {
				break;
			}
			if (!hasAllNeighborChunks(lvl, region)) {
				continue;
			}

			List<int[]> peeked = this.peekedScratch;
			peeked.clear();
			region.peekDirtySections(peeked, MAX_PEEKED_CANDIDATES_PER_REGION);
			for (int[] entry : peeked) {
				if (budget <= 0) {
					break;
				}

				int localIndex = entry[0];
				boolean playerChanged = entry[1] != 0;
				if (!region.markPending(localIndex)) {
					continue;
				}

				int localX = localIndex % REGION_SECTIONS_XZ;
				int localZ = localIndex / REGION_SECTIONS_XZ % REGION_SECTIONS_XZ;
				int localY = localIndex / (REGION_SECTIONS_XZ * REGION_SECTIONS_XZ);
				int sectionX = region.minSectionX() + localX;
				int sectionY = region.minSectionY() + localY;
				int sectionZ = region.minSectionZ() + localZ;
				long sectionNode = SectionPos.asLong(sectionX, sectionY, sectionZ);
				RenderSectionRegion snapshot = this.createSnapshot(lvl, sectionNode);
				if (snapshot == null) {
					region.clearPending(localIndex);
					continue;
				}

				float originX = SectionPos.sectionToBlockCoord(sectionX);
				float originY = SectionPos.sectionToBlockCoord(sectionY);
				float originZ = SectionPos.sectionToBlockCoord(sectionZ);
				region.setFadeDuration(
					localIndex,
					computeFadeDuration(minecraft, region, localIndex, originX, originY, originZ, playerChanged, cameraPos)
				);
				VertexSorting sorting = VertexSorting.byDistance(
					(float)(cameraPos.x - originX), (float)(cameraPos.y - originY), (float)(cameraPos.z - originZ)
				);

				XenoRegionCompiler.INSTANCE.submit(region, localIndex, snapshot, sorting, cameraPos);
				this.totalCompilesSubmitted++;
				budget--;
			}
		}
	}

	private static float regionDistanceSquared(XenoRenderRegion region, float centerX, float centerZ) {
		float deltaX = SectionPos.sectionToBlockCoord(region.minSectionX()) + REGION_SIZE_BLOCKS / 2.0F - centerX;
		float deltaZ = SectionPos.sectionToBlockCoord(region.minSectionZ()) + REGION_SIZE_BLOCKS / 2.0F - centerZ;
		return deltaX * deltaX + deltaZ * deltaZ;
	}

	private @Nullable RenderSectionRegion createSnapshot(ClientLevel lvl, long sectionNode) {
		try {
			return this.snapshotCache.createRegion(lvl, sectionNode);
		} catch (Throwable t) {
			XenoRegionCompiler.LOGGER.debug("Snapshot failed for section {}", sectionNode, t);
			return null;
		}
	}

	private static long computeFadeDuration(
		Minecraft minecraft, XenoRenderRegion region, int localIndex, float originX, float originY, float originZ,
		boolean playerChanged, Vec3 cameraPos
	) {
		if (playerChanged) {
			return 0L;
		}

		double centerX = originX + 8.0;
		double centerY = originY + 8.0;
		double centerZ = originZ + 8.0;
		double distX = centerX - cameraPos.x;
		double distY = centerY - cameraPos.y;
		double distZ = centerZ - cameraPos.z;
		boolean nearby = distX * distX + distY * distY + distZ * distZ < 768.0;
		SectionMesh previousMesh = region.meshSlot(localIndex).get();
		boolean previouslyEmpty = !(previousMesh instanceof CompiledSectionMesh compiled && compiled.hasRenderableLayers());
		return !nearby && !previouslyEmpty
			? (long)Math.floor(minecraft.options.chunkSectionFadeInTime().get() * 1000.0)
			: 0L;
	}

	private static boolean hasAllNeighborChunks(ClientLevel lvl, XenoRenderRegion region) {
		int minChunkX = region.minSectionX() - 1;
		int minChunkZ = region.minSectionZ() - 1;
		int maxChunkX = region.minSectionX() + XenoWorldRenderManager.REGION_SECTIONS_XZ;
		int maxChunkZ = region.minSectionZ() + XenoWorldRenderManager.REGION_SECTIONS_XZ;
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (lvl.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}

		return true;
	}

	private @NonNull SectionCompiler acquireCompiler(Minecraft minecraft) {
		boolean ambientOcclusion = minecraft.options.ambientOcclusion().get();
		boolean cutoutLeaves = minecraft.options.cutoutLeaves().get();
		BlockStateModelSet blockModelSet = minecraft.getModelManager().getBlockStateModelSet();
		FluidStateModelSet fluidModelSet = minecraft.getModelManager().getFluidStateModelSet();
		BlockColors blockColors = minecraft.getBlockColors();
		if (this.cachedCompiler == null
			|| this.cachedAmbientOcclusion != ambientOcclusion
			|| this.cachedCutoutLeaves != cutoutLeaves
			|| this.cachedBlockModelSet != blockModelSet
			|| this.cachedFluidModelSet != fluidModelSet
			|| this.cachedBlockColors != blockColors
		) {
			this.cachedAmbientOcclusion = ambientOcclusion;
			this.cachedCutoutLeaves = cutoutLeaves;
			this.cachedBlockModelSet = blockModelSet;
			this.cachedFluidModelSet = fluidModelSet;
			this.cachedBlockColors = blockColors;
			this.cachedCompiler = new XenoSectionCompiler(ambientOcclusion, cutoutLeaves, blockModelSet, fluidModelSet, blockColors);
		}

		return this.cachedCompiler;
	}

	private interface RegionYConsumer {
		void accept(int regionY);
	}

	private static void forEachRegionY(@Nullable ClientLevel lvl, RegionYConsumer consumer) {
		if (lvl == null) {
			return;
		}

		int minRegionY = Math.floorDiv(lvl.getMinSectionY(), REGION_SECTIONS_Y);
		int maxRegionY = Math.floorDiv(lvl.getMaxSectionY(), REGION_SECTIONS_Y);
		for (int regionY = minRegionY; regionY <= maxRegionY; regionY++) {
			consumer.accept(regionY);
		}
	}

	private void resetState() {
		for (XenoRenderRegion region : this.regions.values()) {
			XenoRegionCompiler.INSTANCE.releaseRegion(region);
		}

		this.regions.clear();
		this.cameraSectionX = 0;
		this.cameraSectionZ = 0;
		this.totalDirtySections = 0L;
	}
}
