package com.xeno.client.renderer.dispatcher;

import com.xeno.client.XenoClient;
import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.renderer.memory.XenoBufferPool;
import com.xeno.client.renderer.sorting.TranslucentSorter;
import com.xeno.client.renderer.util.XenoMeshExtension;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;

/**
 * Custom Section Renderer that implements IXenoSectionRenderer.
 * It manages asynchronous mesh uploads and buffer pools using Xeno's allocator systems.
 * Implements a prioritized, rate-limited, and cancelable chunk compilation lifecycle.
 */
public class XenoSectionRenderer implements IXenoSectionRenderer {

    public static class UploadTask {
        public final SectionRenderDispatcher.RenderSection section;
        public final SectionCompiler.Results results;
        public final SectionBufferBuilderPack builders;

        public UploadTask(SectionRenderDispatcher.RenderSection section, SectionCompiler.Results results, SectionBufferBuilderPack builders) {
            this.section = section;
            this.results = results;
            this.builders = builders;
        }
    }

    public static class CompileTask implements Comparable<CompileTask> {
        public final SectionRenderDispatcher.RenderSection section;
        public final RenderSectionRegion region;
        public final double distanceSq;
        public final long version;

        public CompileTask(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region, double distanceSq, long version) {
            this.section = section;
            this.region = region;
            this.distanceSq = distanceSq;
            this.version = version;
        }

        @Override
        public int compareTo(CompileTask o) {
            return Double.compare(this.distanceSq, o.distanceSq);
        }
    }

    private final Queue<UploadTask> uploadQueue = new ConcurrentLinkedQueue<>();
    private final Queue<SectionBufferBuilderPack> packPool = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<CompileTask> compileQueue = new PriorityBlockingQueue<>();
    private final ConcurrentHashMap<Long, Long> taskVersions = new ConcurrentHashMap<>();
    private final List<Thread> workerThreads = new ArrayList<>();
    private final Consumer<SectionRenderDispatcher.RenderSection> onSectionMeshUpdate;
    private SectionCompiler compiler;
    private volatile boolean disposed = false;

    public XenoSectionRenderer(SectionCompiler compiler, Consumer<SectionRenderDispatcher.RenderSection> onSectionMeshUpdate) {
        this.compiler = compiler;
        this.onSectionMeshUpdate = onSectionMeshUpdate;

        // Clamp background compile threads to 1-3 to prevent CPU starvation
        int coreCount = Math.clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 3);
        for (int i = 0; i < coreCount; i++) {
            Thread thread = new Thread(this::workerLoop, "Xeno-ChunkCompiler-" + i);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.start();
            this.workerThreads.add(thread);
        }
    }

    private void workerLoop() {
        while (!this.disposed && !Thread.currentThread().isInterrupted()) {
            try {
                CompileTask task = this.compileQueue.take();
                long sectionNode = task.section.getSectionNode();
                Long latestVersion = this.taskVersions.get(sectionNode);

                // If version is outdated or task region is invalid, skip execution to save CPU cycles
                if (latestVersion == null || latestVersion != task.version) {
                    continue;
                }

                this.compileSectionSync(task.section, task.region);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Safely absorb worker errors to maintain thread stability
            }
        }
    }

    @Override
    public void setCompiler(SectionCompiler compiler) {
        this.compiler = compiler;
    }

    @Override
    public SectionCompiler getCompiler() {
        return this.compiler;
    }

    @Override
    public SectionRenderDispatcher.@Nullable RenderSectionBufferSlice getRenderSectionSlice(@NonNull SectionMesh sectionMesh, @NonNull ChunkSectionLayer layer) {
        if (sectionMesh instanceof XenoMeshExtension ext) {
            XenoBufferPool.Allocation vertexAlloc = ext.xeno$getVertexAllocation(layer);
            XenoBufferPool.Allocation indexAlloc = ext.xeno$getIndexAllocation(layer);
            if (vertexAlloc != null) {
                return new SectionRenderDispatcher.RenderSectionBufferSlice(
                        vertexAlloc.buffer, vertexAlloc.offset,
                        indexAlloc != null ? indexAlloc.buffer : null, indexAlloc != null ? indexAlloc.offset : 0L
                );
            }
        }
        return null;
    }

    @Override
    public void uploadTerrainBuffersToGpu() {
        XenoWorldRenderer.tickFrame();

        UploadTask task;
        long start = System.nanoTime();
        long budgetNanos = 1_500_000L; // 1.5 millisecond budget per frame

        while ((task = this.uploadQueue.poll()) != null) {
            XenoWorldRenderer.uploadToGpu(task.section, task.results);
            if (this.onSectionMeshUpdate != null) {
                this.onSectionMeshUpdate.accept(task.section);
            }
            this.releasePack(task.builders);

            // Break if we exceed our frame budget to maintain smooth frame times
            if (System.nanoTime() - start > budgetNanos) {
                break;
            }
        }
    }

    @Override
    public void clearCompileQueue() {
        this.uploadQueue.clear();
        this.compileQueue.clear();
        this.taskVersions.clear();
    }

    @Override
    public boolean isQueueEmpty() {
        return this.uploadQueue.isEmpty() && this.compileQueue.isEmpty();
    }

    @Override
    public void dispose() {
        this.disposed = true;
        for (Thread thread : this.workerThreads) {
            thread.interrupt();
        }
        this.clearCompileQueue();
        SectionBufferBuilderPack pack;
        while ((pack = this.packPool.poll()) != null) {
            pack.close();
        }
    }

    @Override
    public @NonNull String getStats() {
        return "Xeno Pipeline Active (Prioritized Executor)";
    }

    @Override
    public int getCompileQueueSize() {
        return this.compileQueue.size() + this.uploadQueue.size();
    }

    @Override
    public int getFreeBufferCount() {
        return this.packPool.size();
    }

    @Override
    public void setCameraPosition(Vec3 cameraPosition) {
        // Handled automatically via XenoClient state tracking
    }

    @Override
    public SectionBufferBuilderPack acquirePack() {
        SectionBufferBuilderPack pack = this.packPool.poll();
        if (pack == null) {
            pack = new SectionBufferBuilderPack();
        }
        return pack;
    }

    @Override
    public void releasePack(SectionBufferBuilderPack pack) {
        if (pack != null) {
            pack.discardAll();
            this.packPool.offer(pack);
        }
    }

    @Override
    public void queueUpload(SectionRenderDispatcher.RenderSection section, SectionCompiler.Results results, SectionBufferBuilderPack builders) {
        this.uploadQueue.add(new UploadTask(section, results, builders));
    }

    @Override
    public void compileSectionSync(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region) {
        if (region == null) return;
        SectionCompiler comp = this.getCompiler();
        if (comp == null) return;

        SectionPos sectionPos = SectionPos.of(section.getSectionNode());
        SectionBufferBuilderPack builders = this.acquirePack();
        builders.discardAll();

        SectionCompiler.Results results = null;
        try {
            Vec3 cameraPos = XenoClient.getCameraPos();
            float rx = 0;
            float ry = 0;
            float rz = 0;
            if (cameraPos != null) {
                BlockPos origin = section.getRenderOrigin();
                rx = (float) (cameraPos.x - origin.getX());
                ry = (float) (cameraPos.y - origin.getY());
                rz = (float) (cameraPos.z - origin.getZ());
            }
            VertexSorting vertexSorting = VertexSorting.byDistance(rx, ry, rz);

            results = comp.compile(sectionPos, region, vertexSorting, builders);
        } catch (Throwable t) {
            // Silently absorb exceptions during reload
        } finally {
            if (results != null) {
                // If compiling on the main thread, upload immediately to bypass the queue
                if (Minecraft.getInstance().isSameThread()) {
                    XenoWorldRenderer.uploadToGpu(section, results);
                    if (this.onSectionMeshUpdate != null) {
                        this.onSectionMeshUpdate.accept(section);
                    }
                    this.releasePack(builders);
                } else {
                    this.queueUpload(section, results, builders);
                }
            } else {
                this.releasePack(builders);
            }
        }
    }

    @Override
    public void compileSectionAsync(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region) {
        if (region == null) return;
        long sectionNode = section.getSectionNode();
        long version = System.nanoTime();
        this.taskVersions.put(sectionNode, version);

        BlockPos origin = section.getRenderOrigin();
        Vec3 camPos = XenoClient.getCameraPos();
        double dx = 0;
        double dy = 0;
        double dz = 0;
        if (camPos != null) {
            dx = camPos.x - origin.getX() - 8.0;
            dy = camPos.y - origin.getY() - 8.0;
            dz = camPos.z - origin.getZ() - 8.0;
        }
        double distanceSq = dx * dx + dy * dy + dz * dz;

        this.compileQueue.add(new CompileTask(section, region, distanceSq, version));
    }

    @Override
    public void resortTransparency(SectionRenderDispatcher.RenderSection section) {
        SectionMesh mesh = section.getSectionMesh();
        if (mesh instanceof CompiledSectionMesh compiled && mesh instanceof XenoMeshExtension ext) {
            XenoBufferPool.Allocation indexAlloc = ext.xeno$getIndexAllocation(ChunkSectionLayer.TRANSLUCENT);
            TranslucentSorter.resort(compiled, ext, section.getSectionNode(), section.getRenderOrigin(), indexAlloc);
        }
    }
}
