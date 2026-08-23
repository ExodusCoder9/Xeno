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

import net.minecraft.TracingExecutor;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Owned worker-thread executor for section mesh compilation.
 * <p>
 * This executor instead owns a fixed
 * pool of dedicated worker threads that block on a counting semaphore while idle and consume tasks
 * from a lock-free deque.
 *
 * <p>The worker count follows the following heuristic: {@code clamp(max(cores/3, cores-6), 1, 10)}.
 * Each task consumed from the deque is a full  runTask cycle, which in turn polls the
 * priority-ordered {@link XenoSectionTaskQueue}, so chunk priority is preserved.
 *
 * <p>A frame-aligned compile budget bounds how much CPU the chunk builders may collectively spend in
 * each ~16.7 ms window while the backlog is small. The render thread is never blocked: instead each
 * worker reserves a slice of the shared per-window budget before starting a task, and repays (or
 * overspends) it once the task finishes. The reservation tracks a decaying average of recent task
 * durations, so it adapts to how expensive section compilation actually is. Once the budget for a
 * window is spent, workers park their thread until the next window. This caps the total compile CPU
 * per frame and spreads finished meshes across frames, so the GPU upload on the render thread never
 * sees a burst and the frame rate stays flat.
 *
 * <p>This is a process-lifetime singleton; it is never shut down, and its worker threads are daemons
 * so they do not block JVM exit. Shutdown is still implemented just for the ExecutorService contract.
 */
public final class XenoChunkExecutorService extends AbstractExecutorService {
	private static final Logger LOGGER = LogManager.getLogger("XenoChunkExecutor");

	/** Length of one budget window, matched to a 60 FPS frame. */
	private static final long FRAME_NANOS = 16_700_000L;

	/**
	 * Total compile CPU the workers are allowed to collectively spend per budget window in
	 * steady state.
	 */
	private static final long COMPILE_BUDGET_NANOS = FRAME_NANOS * 3 / 4;
	/** Reservation floor and admission threshold, so a window always takes at least one task. */
	private static final long MIN_RESERVATION_NANOS = 100_000L;
	/** EWMA smoothing factor applied to measured task durations. */
	private static final double ESTIMATE_SMOOTHING = 0.2;
	private static final long UNBOUNDED_RESERVATION = Long.MAX_VALUE;
	private static final int TURBO_HIGH_WATERMARK_PER_WORKER = 3;
	private static final int TURBO_LOW_WATERMARK_PER_WORKER = 1;
	public static final TracingExecutor INSTANCE = new TracingExecutor(new XenoChunkExecutorService());
	private final ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();
	private final Semaphore semaphore = new Semaphore(0);
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final AtomicInteger terminatedThreads = new AtomicInteger();
	private final List<Thread> threads = new ArrayList<>();
	private final AtomicInteger queuedTasks = new AtomicInteger();
	private final Object budgetLock = new Object();
	private long budgetEpoch = Long.MIN_VALUE;
	private long budgetRemaining;
	private double estimatedTaskNanos = COMPILE_BUDGET_NANOS / 8.0;
	private boolean budgetTurbo;

	private XenoChunkExecutorService() {
		int count = optimalThreadCount();
		for (int i = 0; i < count; i++) {
			Thread thread = new Thread(this::workerLoop, "Xeno Chunk Render Executor #" + i);
			thread.setPriority(Math.max(0, Thread.NORM_PRIORITY - 2));
			thread.setDaemon(true);
			thread.start();
			this.threads.add(thread);
		}
	}

	static int optimalWorkerCount() {
		return optimalThreadCount();
	}

	int workerCount() {
		return this.threads.size();
	}

	private void workerLoop() {
		while (this.running.get()) {
			Runnable task = this.waitForNextJob();
			if (task == null) {
				continue;
			}

			long reserved = this.acquireBudget();
			if (reserved < 0L) {
				this.queuedTasks.incrementAndGet();
				this.tasks.addFirst(task);
				this.semaphore.release(1);
				continue;
			}

			long start = System.nanoTime();
			try {
				task.run();
			} catch (Throwable t) {
				LOGGER.error("Task on chunk builder executor threw an exception", t);
			} finally {
				if (reserved != UNBOUNDED_RESERVATION) {
					this.releaseBudget(System.nanoTime() - start, reserved);
				}
			}
		}

		this.terminatedThreads.incrementAndGet();
	}

	private boolean budgetGrantedUnconditionally() {
		return this.queuedTasks.get() >= this.workerCount() * TURBO_HIGH_WATERMARK_PER_WORKER;
	}

	private Runnable waitForNextJob() {
		if (!this.running.get()) {
			return null;
		}

		try {
			this.semaphore.acquire();
		} catch (InterruptedException e) {
			return null;
		}

		Runnable task = this.tasks.poll();
		if (task != null) {
			this.queuedTasks.decrementAndGet();
		}

		return task;
	}

	private long acquireBudget() {
		if (this.budgetGrantedUnconditionally()) {
			synchronized (this.budgetLock) {
				this.budgetTurbo = true;
			}

			return UNBOUNDED_RESERVATION;
		}

		long reserve = Math.clamp((long) this.estimatedTaskNanos, MIN_RESERVATION_NANOS, COMPILE_BUDGET_NANOS);
		synchronized (this.budgetLock) {
			while (true) {
				if (this.budgetTurbo && this.queuedTasks.get() < this.workerCount() * TURBO_LOW_WATERMARK_PER_WORKER) {
					this.budgetTurbo = false;
				}

				if (this.budgetTurbo) {
					return UNBOUNDED_RESERVATION;
				}

				long epoch = System.nanoTime() / FRAME_NANOS;
				if (this.budgetEpoch != epoch) {
					this.budgetEpoch = epoch;
					this.budgetRemaining = COMPILE_BUDGET_NANOS;
				}

				if (this.budgetRemaining >= MIN_RESERVATION_NANOS) {
					reserve = Math.min(reserve, this.budgetRemaining);
					this.budgetRemaining -= reserve;
					return reserve;
				}

				long nextBoundary = (epoch + 1) * FRAME_NANOS;
				long waitNanos = nextBoundary - System.nanoTime();
				if (waitNanos <= 0L) {
					continue;
				}

				try {
					this.budgetLock.wait(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					this.budgetEpoch = Long.MIN_VALUE;
					return -1L;
				}
			}
		}
	}

	private void releaseBudget(long elapsedNanos, long reservedNanos) {
		synchronized (this.budgetLock) {
			this.budgetRemaining += reservedNanos - elapsedNanos;
			if (this.budgetRemaining > COMPILE_BUDGET_NANOS) {
				this.budgetRemaining = COMPILE_BUDGET_NANOS;
			}
			this.estimatedTaskNanos = Math.clamp(this.estimatedTaskNanos * (1.0 - ESTIMATE_SMOOTHING) + elapsedNanos * ESTIMATE_SMOOTHING, MIN_RESERVATION_NANOS,
                    COMPILE_BUDGET_NANOS);
			this.budgetLock.notifyAll();
		}
	}

	@Override
	public void execute(@NonNull Runnable command) {
		if (command == null) {
			throw new NullPointerException("command");
		}
		if (!this.running.get()) {
			throw new RejectedExecutionException("Executor is shut down");
		}

		this.queuedTasks.incrementAndGet();
		this.tasks.addLast(command);
		this.semaphore.release(1);
	}

	@Override
	public void shutdown() {
		if (!this.running.compareAndSet(true, false)) {
			return;
		}

		this.semaphore.release(this.threads.size());
	}

	@Override
	public @NonNull List<Runnable> shutdownNow() {
		this.shutdown();

		List<Runnable> dropped = new ArrayList<>();
		Runnable task;
		while ((task = this.tasks.poll()) != null) {
			dropped.add(task);
		}
		return dropped;
	}

	@Override
	public boolean isShutdown() {
		return !this.running.get();
	}

	@Override
	public boolean isTerminated() {
		return this.terminatedThreads.get() >= this.threads.size();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		for (Thread thread : this.threads) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0L) {
				return this.isTerminated();
			}

			thread.join(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
			if (this.isTerminated()) {
				return true;
			}
		}
		return this.isTerminated();
	}

	private static int optimalThreadCount() {
		int cores = Runtime.getRuntime().availableProcessors();
		return Mth.clamp(Math.max(cores / 3, cores - 6), 1, 10);
	}
}
