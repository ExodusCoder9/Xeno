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
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class XenoWorldRenderManager {
	public static final XenoWorldRenderManager INSTANCE = new XenoWorldRenderManager();
	public static final int REGION_SECTIONS_XZ = 8;
	public static final int REGION_SECTIONS_Y = 8;
	private static final int VIEW_MARGIN_BLOCKS = 64;
	private static final long RELEASE_GRACE_FRAMES = 200L;
	private static final int MAX_COMPILES_PER_FRAME = 32;
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

	private XenoWorldRenderManager() {
	}

	public void onLevelSet(@Nullable ClientLevel level) {
		synchronized (this.lock) {
			this.resetState();
			this.loadedChunks.clear();
			this.level = level;
		}

		XenoRegionCompiler.LOGGER.info("Level bound: {}", level != null ? String.valueOf(level.dimension()) : "<null>");
	}

	public void onRenderPipelineReset() {
		synchronized (this.lock) {
			this.resetState();
			this.rebuildRegionsForLoadedChunks();
		}

		XenoRegionCompiler.LOGGER.info("Render pipeline reset: all regions evicted");
	}

	public void onChunkLoaded(ChunkPos pos) {
		synchronized (this.lock) {
			if (!this.loadedChunks.add(ChunkPos.pack(pos.x(), pos.z()))) {
				return;
			}

			this.totalChunkLoads++;
			this.createRegionColumn(Math.floorDiv(pos.x(), REGION_SECTIONS_XZ), Math.floorDiv(pos.z(), REGION_SECTIONS_XZ));
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
			int regionX = Math.floorDiv(pos.x(), REGION_SECTIONS_XZ);
			int regionZ = Math.floorDiv(pos.z(), REGION_SECTIONS_XZ);
			forEachRegionY(this.level, regionY -> {
				XenoRenderRegion region = this.regions.get(XenoRenderRegion.key(regionX, regionY, regionZ));
				if (region != null) {
					region.decrementLoadedChunkCount();
				}
			});
		}
	}

	public void onSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
		synchronized (this.lock) {
			this.totalDirtySections++;
			XenoRenderRegion region = this.regions.get(
				XenoRenderRegion.key(
					Math.floorDiv(sectionX, REGION_SECTIONS_XZ),
					Math.floorDiv(sectionY, REGION_SECTIONS_Y),
					Math.floorDiv(sectionZ, REGION_SECTIONS_XZ)
				)
			);
			if (region == null) {
				return;
			}

			int localIndex = region.localIndexOf(sectionX, sectionY, sectionZ);
			if (localIndex >= 0) {
				region.markSectionDirty(localIndex, playerChanged);
			}
		}
	}

	public record ResolvedSection(XenoRenderRegion region, int localIndex) {
	}

	public @Nullable ResolvedSection resolveSection(long sectionNode) {
		int sectionX = SectionPos.x(sectionNode);
		int sectionY = SectionPos.y(sectionNode);
		int sectionZ = SectionPos.z(sectionNode);
		XenoRenderRegion region;
		synchronized (this.lock) {
			region = this.regions.get(
				XenoRenderRegion.key(
					Math.floorDiv(sectionX, REGION_SECTIONS_XZ),
					Math.floorDiv(sectionY, REGION_SECTIONS_Y),
					Math.floorDiv(sectionZ, REGION_SECTIONS_XZ)
				)
			);
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

	public void snapshotVisibleRegions(List<XenoRenderRegion> out) {
		out.clear();
		synchronized (this.lock) {
			out.addAll(this.regions.values());
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

			this.scheduleCompiles(minecraft, lvl, camRegionX, camRegionZ, halfExtentRegions);
		}
	}
	
	private void scheduleCompiles(Minecraft minecraft, ClientLevel lvl, int camRegionX, int camRegionZ, int halfExtentRegions) {
		int budget = MAX_COMPILES_PER_FRAME;
		if (!this.regions.isEmpty() && budget > 0) {
			XenoRegionCompiler.INSTANCE.setCompiler(
				new XenoSectionCompiler(
					minecraft.options.ambientOcclusion().get(),
					minecraft.options.cutoutLeaves().get(),
					minecraft.getModelManager().getBlockStateModelSet(),
					minecraft.getModelManager().getFluidStateModelSet(),
					minecraft.getBlockColors()
				)
			);

			Vec3 cameraPos = minecraft.gameRenderer.mainCamera().position();
			RenderRegionCache snapshotCache = new RenderRegionCache();

			for (XenoRenderRegion region : this.regions.values()) {
				if (budget <= 0) {
					break;
				}

				if (Math.abs(region.regionX() - camRegionX) > halfExtentRegions
					|| Math.abs(region.regionZ() - camRegionZ) > halfExtentRegions
					|| !region.hasDirtySections()
					|| !hasAllNeighborChunks(lvl, region)
				) {
					continue;
				}

				boolean[] playerFlag = new boolean[1];
				int localIndex;
				while (budget > 0 && (localIndex = region.pollNextDirtySection(playerFlag)) >= 0) {
					int localX = localIndex % REGION_SECTIONS_XZ;
					int localZ = localIndex / REGION_SECTIONS_XZ % REGION_SECTIONS_XZ;
					int localY = localIndex / (REGION_SECTIONS_XZ * REGION_SECTIONS_XZ);
					int sectionX = region.minSectionX() + localX;
					int sectionY = region.minSectionY() + localY;
					int sectionZ = region.minSectionZ() + localZ;

					long sectionNode = SectionPos.asLong(sectionX, sectionY, sectionZ);
					float originX = SectionPos.sectionToBlockCoord(sectionX);
					float originY = SectionPos.sectionToBlockCoord(sectionY);
					float originZ = SectionPos.sectionToBlockCoord(sectionZ);
					region.setFadeDuration(
						localIndex,
						computeFadeDuration(minecraft, region, localIndex, sectionX, sectionY, sectionZ, playerFlag[0], cameraPos)
					);
					VertexSorting sorting = VertexSorting.byDistance(
						(float)(cameraPos.x - originX), (float)(cameraPos.y - originY), (float)(cameraPos.z - originZ)
					);

					XenoRegionCompiler.INSTANCE.submit(
						region, localIndex, snapshotCache.createRegion(lvl, sectionNode), sorting, cameraPos
					);
					this.totalCompilesSubmitted++;
					budget--;
				}
			}
		}
	}

	private static long computeFadeDuration(
		Minecraft minecraft, XenoRenderRegion region, int localIndex, int sectionX, int sectionY, int sectionZ, boolean playerChanged, Vec3 cameraPos
	) {
		if (playerChanged) {
			return 0L;
		}

		double centerX = SectionPos.sectionToBlockCoord(sectionX) + 8;
		double centerY = SectionPos.sectionToBlockCoord(sectionY) + 8;
		double centerZ = SectionPos.sectionToBlockCoord(sectionZ) + 8;
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
		for (int deltaX = -1; deltaX <= 1; deltaX++) {
			for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
				int chunkX = region.minSectionX() + deltaX;
				int chunkZ = region.minSectionZ() + deltaZ;
				if (lvl.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}

		return true;
	}

	public String getStats() {
		synchronized (this.lock) {
			return String.format(
				"R: %d live/%d created/%d released, L: %d, U: %d, D: %d, C: %d | %s",
				this.regions.size(),
				this.regionsCreated,
				this.regionsReleased,
				this.totalChunkLoads,
				this.totalChunkUnloads,
				this.totalDirtySections,
				this.totalCompilesSubmitted,
				XenoRegionCompiler.INSTANCE.getStats()
			);
		}
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
