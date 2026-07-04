package com.xeno.client.render.chunk.compile;

import net.minecraft.client.renderer.chunk.VisibilitySet;
import java.util.List;

public class ChunkCompileResult {
	private final long sectionKey;
	private final Object solidMesh;
	private final Object cutoutMesh;
	private final Object translucentMesh;
	private final List<?> blockEntities;
	private final VisibilitySet visibilitySet;
	private final Object transparencyState;

	public ChunkCompileResult(
		long sectionKey,
		Object solidMesh,
		Object cutoutMesh,
		Object translucentMesh,
		List<?> blockEntities,
		VisibilitySet visibilitySet,
		Object transparencyState
	) {
		this.sectionKey = sectionKey;
		this.solidMesh = solidMesh;
		this.cutoutMesh = cutoutMesh;
		this.translucentMesh = translucentMesh;
		this.blockEntities = blockEntities;
		this.visibilitySet = visibilitySet;
		this.transparencyState = transparencyState;
	}

	public long getSectionKey() { return sectionKey; }
	public Object getSolidMesh() { return solidMesh; }
	public Object getCutoutMesh() { return cutoutMesh; }
	public Object getTranslucentMesh() { return translucentMesh; }
	public List<?> getBlockEntities() { return blockEntities; }
	public VisibilitySet getVisibilitySet() { return visibilitySet; }
	public Object getTransparencyState() { return transparencyState; }
}
