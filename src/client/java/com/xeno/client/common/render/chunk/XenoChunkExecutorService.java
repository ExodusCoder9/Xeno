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
import java.util.concurrent.locks.LockSupport;

/**
 * Owned worker-thread executor that replaces {@code Util.backgroundExecutor()}
 * for section mesh compilation.
 * <p>
 *This executor instead owns a fixed
 * pool of dedicated worker threads that block on a counting semaphore while idle and consume tasks
 * from a lock-free deque.
 *
 * <p>The worker count follows the following heuristic: {@code clamp(max(cores/3, cores-6), 1, 10)}.
 * Each task consumed from the deque is a full {@code runTask()} cycle, which in turn polls the
 * priority-ordered {@link XenoSectionTaskQueue}, so chunk priority is preserved.
 *
 * <p>A per-frame compile-time budget bounds how much CPU the chunk builders may collectively spend in each ~16.7 ms window: measured
 * compile durations are accumulated into a shared window that resets every epoch, and workers park
 * for the remainder of the epoch once the budget is exhausted. This protects the render thread from
 * compile floods and spreads finished meshes across frames so the GPU upload never sees a burst.
 *
 * <p>This is a process-lifetime singleton; it is never shut down, and its worker threads are daemons
 * so they do not block JVM exit. {@code shutdown}/{@code shutdownNow} are still implemented
 * faithfully for the {@code ExecutorService} contract.
 */
public final class XenoChunkExecutorService extends AbstractExecutorService {
	private static final Logger LOGGER = LogManager.getLogger("XenoChunkExecutor");

	/** Length of one pacing window. */
	private static final long FRAME_NS = 16_666_667L;

	/** Maximum total compile time allowed per pacing window across all workers. */
	private static final long MAX_COMPILE_TIME_PER_FRAME_NS = 10_000_000L;

	/** Saturation cap for the per-window accumulator */
	private static final long BUDGET_SATURATION_NS = 4_000_000_000L;

	public static final TracingExecutor INSTANCE = new TracingExecutor(new XenoChunkExecutorService());

	private final ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();
	private final Semaphore semaphore = new Semaphore(0);
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final AtomicInteger terminatedThreads = new AtomicInteger();
	private final List<Thread> threads = new ArrayList<>();

	private final Object budgetLock = new Object();
	private long budgetEpoch;
	private long budgetSpent;

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

	private void workerLoop() {
		while (this.running.get()) {
			Runnable task = this.waitForNextJob();
			if (task == null) {
				continue;
			}

			this.parkIfBudgetExhausted();

			long start = System.nanoTime();
			try {
				task.run();
			} catch (Throwable t) {
				LOGGER.error("Task on chunk builder executor threw an exception", t);
			}
			this.paceCompileTime(System.nanoTime() - start);
		}

		this.terminatedThreads.incrementAndGet();
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

		return this.tasks.poll();
	}

	/**
	 * Records how long a job took and, once the current window's budget is exhausted, parks the
	 * worker until the next window starts so the chunk builders never use the CPU for more
	 * than the budgeted share of a frame.
	 */
	private void paceCompileTime(long compileTime) {
		if (this.recordCompileTime(compileTime)) {
			this.parkUntilNextEpoch();
		}
	}

	private void parkIfBudgetExhausted() {
		if (this.isBudgetExhausted()) {
			this.parkUntilNextEpoch();
		}
	}

	private void parkUntilNextEpoch() {
		long now = System.nanoTime();
		long remaining = (now / FRAME_NS + 1L) * FRAME_NS - now;
		if (remaining > 0L) {
			LockSupport.parkNanos(remaining);
		}
	}

	/**
	 * Accumulates a measured compile duration into the current window, resetting the accumulator
	 * when the window rolls over eventually. Returns whether the window budget is now exhausted. The lock is
	 * held for only a few nanoseconds per job, so contention between workers is negligible.
	 */
	private boolean recordCompileTime(long compileTime) {
		synchronized (this.budgetLock) {
			long epoch = System.nanoTime() / FRAME_NS;
			if (epoch != this.budgetEpoch) {
				this.budgetEpoch = epoch;
				this.budgetSpent = 0L;
			}
			this.budgetSpent = Math.min(this.budgetSpent + compileTime, BUDGET_SATURATION_NS);
			return this.budgetSpent >= MAX_COMPILE_TIME_PER_FRAME_NS;
		}
	}

	private boolean isBudgetExhausted() {
		synchronized (this.budgetLock) {
			long epoch = System.nanoTime() / FRAME_NS;
			return epoch == this.budgetEpoch && this.budgetSpent >= MAX_COMPILE_TIME_PER_FRAME_NS;
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
		int maxThreads = Runtime.getRuntime().availableProcessors();
		return Mth.clamp(Math.max(maxThreads / 3, maxThreads - 6), 1, 10);
	}
}
