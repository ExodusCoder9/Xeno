package com.xeno.render.profiling;

/*
 * Original Codebase: Copyright XCollateral (VulkanMod)
 * Refactored Codebase: Copyright ExodusCoder9 (Xeno)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Refactored, Renamed and Optimized by ExodusCoder9.
 */


import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;

public class Profiler {
   private static final boolean DEBUG = false;
   private static final boolean FORCE_ACTIVE = false;
   private static final int NANOS_IN_MS = 1000000;
   private static final float CONVERSION = 1000000.0F;
   private static final float INV_CONVERSION = 1.0E-6F;
   private static final int SAMPLE_COUNT = 200;
   public static boolean ACTIVE = false;
   private static final Profiler MAIN_PROFILER = new Profiler("Main");
   private final String name;
   LongArrayList startTimes = new LongArrayList();
   ObjectArrayList<Profiler.Node> nodeStack = new ObjectArrayList();
   ObjectArrayList<Profiler.Node> nodes = new ObjectArrayList();
   ObjectArrayList<Profiler.Node> currentFrameNodes = new ObjectArrayList();
   Object2ReferenceOpenHashMap<String, Profiler.Node> nodeMap = new Object2ReferenceOpenHashMap();
   Profiler.Node mainNode;
   Profiler.Node selectedNode;
   Profiler.Node currentNode;
   Profiler.ProfilerResults profilerResults = new Profiler.ProfilerResults();

   public static Profiler getMainProfiler() {
      return MAIN_PROFILER;
   }

   public static void setActive(boolean b) {
      ACTIVE = b;
   }

   public Profiler(String s) {
      this.name = s;
      this.currentNode = this.selectedNode = this.mainNode = new Profiler.Node(s);
   }

   public void push(String s) {
      if (ACTIVE) {
         Profiler.Node node = (Profiler.Node)this.nodeMap.get(s);
         if (node == null) {
            node = new Profiler.Node(s);
            this.nodeMap.put(s, node);
            this.currentNode.addChild(node);
         }

         node.setParent(this.currentNode);
         node.children.clear();
         if (node.parent == this.selectedNode) {
            this.currentFrameNodes.add(node);
         }

         this.currentNode = node;
         this.pushNodeStack(node);
      }
   }

   private void pushNodeStack(Profiler.Node node) {
      long startTime = System.nanoTime();
      this.startTimes.push(startTime);
      this.nodeStack.push(node);
   }

   public void pop() {
      if (ACTIVE) {
         if (!this.nodeStack.isEmpty()) {
            int i = this.nodeStack.size() - 1;
            Profiler.Node node = (Profiler.Node)this.nodeStack.remove(i);
            long startTime = this.startTimes.removeLong(i);
            long deltaMs = System.nanoTime() - startTime;
            node.push(deltaMs);
            this.currentNode = this.currentNode.parent;
         }
      }
   }

   public void start() {
      if (ACTIVE) {
         if (!this.nodeStack.isEmpty()) {
            this.nodeStack.clear();
            this.startTimes.clear();
         }

         this.currentNode = this.mainNode;
         this.mainNode.children.clear();
         this.pushNodeStack(this.mainNode);
         ObjectArrayList<Profiler.Node> t = this.nodes;
         this.nodes = this.currentFrameNodes;
         this.currentFrameNodes = t;
         this.currentFrameNodes.clear();
      }
   }

   public void end() {
      if (ACTIVE) {
         this.pop();
      }
   }

   public void round() {
      this.end();
      this.start();
   }

   public Profiler.ProfilerResults getProfilerResults() {
      this.profilerResults.update(this.selectedNode, this.nodes);
      return this.profilerResults;
   }

   public static class Node {
      final String name;
      Profiler.Node parent;
      List<Profiler.Node> children = new ObjectArrayList();
      long maxDuration;
      long minDuration;
      LongArrayFIFOQueue values = new LongArrayFIFOQueue(200);
      long accumulatedDuration;
      Profiler.Result result;

      Node(String name) {
         this.name = name;
         this.result = new Profiler.Result(name);
         this.reset();
      }

      void setParent(Profiler.Node node) {
         this.parent = node;
         node.addChild(this);
      }

      void addChild(Profiler.Node node) {
         this.children.add(node);
      }

      void push(long duration) {
         if (duration < this.minDuration) {
            this.minDuration = duration;
         }

         if (duration > this.maxDuration) {
            this.maxDuration = duration;
         }

         if (this.values.size() >= 200) {
            this.accumulatedDuration = this.accumulatedDuration - this.values.dequeueLong();
         }

         this.values.enqueue(duration);
         this.accumulatedDuration += duration;
      }

      public void updateResult() {
         this.result.setValue((float)this.accumulatedDuration / this.values.size() * 1.0E-6F);
      }

      void reset() {
         this.minDuration = Long.MAX_VALUE;
         this.maxDuration = Long.MIN_VALUE;
         this.accumulatedDuration = 0L;
      }

      @Override
      public String toString() {
         return this.name;
      }
   }

   public static class ProfilerResults {
      Profiler.Result result;
      ObjectArrayList<Profiler.Result> partialResults = new ObjectArrayList();

      public ProfilerResults() {
      }

      public void update(Profiler.Node mainNode, List<Profiler.Node> nodes) {
         mainNode.updateResult();
         this.result = mainNode.result;
         this.partialResults.clear();

         for (Profiler.Node node : nodes) {
            node.updateResult();
            this.partialResults.push(node.result);
         }
      }

      public Profiler.Result getResult() {
         return this.result;
      }

      public ObjectArrayList<Profiler.Result> getPartialResults() {
         return this.partialResults;
      }
   }

   public static class Result {
      public final String name;
      public float value;

      public Result(String name) {
         this.name = name;
      }

      void setValue(float value) {
         this.value = value;
      }
   }
}
