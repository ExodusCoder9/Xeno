package com.xeno.client.render.chunk.culling;

import com.xeno.client.render.chunk.storage.SectionStorage;
import com.xeno.client.render.chunk.storage.XenoSection;
import com.xeno.util.XenoThreadUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class CullingThread extends Thread {
	private final LinkedBlockingQueue<CullingInput> inputQueue = new LinkedBlockingQueue<>(1);
	private final AtomicReference<CullingOutput> outputRef = new AtomicReference<>();
	private final ConcurrentLinkedQueue<Object> meshUpdates = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<long[]> emptySectionChanges = new ConcurrentLinkedQueue<>();
	private final OcclusionGraph occlusionGraph = new OcclusionGraph();
	private final SectionTree sectionTree = new SectionTree();
	private final EntityCuller entityCuller = new EntityCuller();
	private final Frustum frustum = new Frustum();
	private SectionStorage sectionStorage;
	private volatile boolean running = true;

	public CullingThread() {
		super(XenoThreadUtil.createThreadFactory("Xeno-Cull", false).newThread(() -> {}).getName());
	}

	public void setSectionStorage(SectionStorage storage) {
		this.sectionStorage = storage;
	}

	public void offerInput(CullingInput input) {
		inputQueue.offer(input);
	}

	public CullingOutput getOutput() {
		return outputRef.getAndSet(null);
	}

	public void notifyMeshUpdate(Object update) {
		meshUpdates.offer(update);
	}

	@Override
	public void run() {
		while (running) {
			try {
				CullingInput input = inputQueue.take();
				long startTime = System.nanoTime();

				drainMeshUpdates();
				drainEmptySectionChanges();

				var sectionMap = sectionStorage.getSnapshot();

				occlusionGraph.invalidateIfNeeded(
					new OcclusionGraph.Vec3(input.getCameraPos().x, input.getCameraPos().y, input.getCameraPos().z),
					input.getFov()
				);

				occlusionGraph.update(sectionMap,
					new OcclusionGraph.Vec3(input.getCameraPos().x, input.getCameraPos().y, input.getCameraPos().z),
					input.getFov());

				sectionTree.rebuild(sectionMap);

				ObjectArrayList<XenoSection> visible = new ObjectArrayList<>();
				ObjectArrayList<XenoSection> nearby = new ObjectArrayList<>();
				sectionTree.traverse(frustum, visible, nearby);

				entityCuller.update(
					new OcclusionGraph.Vec3(input.getCameraPos().x, input.getCameraPos().y, input.getCameraPos().z),
					frustum, sectionMap);

				double cullTimeMs = (System.nanoTime() - startTime) / 1_000_000.0;

				CullingOutput output = new CullingOutput(
					visible, nearby,
					entityCuller.getVisibilityMap(),
					input.getFrameIndex(),
					cullTimeMs
				);
				outputRef.set(output);

			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private void drainMeshUpdates() {
		Object update;
		while ((update = meshUpdates.poll()) != null) {
			// Process mesh update
		}
	}

	private void drainEmptySectionChanges() {
		long[] changes;
		while ((changes = emptySectionChanges.poll()) != null) {
			// Process empty section changes
		}
	}

	public void shutdown() {
		running = false;
		interrupt();
	}
}
