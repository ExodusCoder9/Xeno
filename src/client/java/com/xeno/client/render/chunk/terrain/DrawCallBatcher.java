package com.xeno.client.render.chunk.terrain;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class DrawCallBatcher {
	private final ObjectArrayList<DrawCommand> solidCommands = new ObjectArrayList<>();
	private final ObjectArrayList<DrawCommand> cutoutCommands = new ObjectArrayList<>();
	private final ObjectArrayList<DrawCommand> translucentCommands = new ObjectArrayList<>();
	private final int maxBatchSize;

	public DrawCallBatcher(int maxBatchSize) { this.maxBatchSize = maxBatchSize; }

	public void clear() {
		solidCommands.clear(); cutoutCommands.clear(); translucentCommands.clear();
	}

	public void addCommand(Layer layer, int firstIndex, int indexCount, int baseVertex) {
		var cmd = new DrawCommand(firstIndex, indexCount, baseVertex);
		switch (layer) {
			case SOLID -> solidCommands.add(cmd);
			case CUTOUT -> cutoutCommands.add(cmd);
			case TRANSLUCENT -> translucentCommands.add(cmd);
		}
	}

	public record DrawCommand(int firstIndex, int indexCount, int baseVertex) {}

	public enum Layer {
		SOLID, CUTOUT, TRANSLUCENT
	}
}
