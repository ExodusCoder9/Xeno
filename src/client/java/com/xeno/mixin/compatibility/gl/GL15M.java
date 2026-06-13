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
package com.xeno.mixin.compatibility.gl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import com.xeno.gl.VkGlBuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.NativeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GL15.class)
public class GL15M {
   @Overwrite(remap = false)
   @NativeType("void")
   public static int glGenBuffers() {
      return VkGlBuffer.glGenBuffers();
   }

   @Overwrite(remap = false)
   public static void glBindBuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int buffer) {
      VkGlBuffer.glBindBuffer(target, buffer);
   }

   @Overwrite(remap = false)
   public static void glBufferData(@NativeType("GLenum") int target, @NativeType("void const *") ByteBuffer data, @NativeType("GLenum") int usage) {
      VkGlBuffer.glBufferData(target, data, usage);
   }

   @Overwrite(remap = false)
   public static void glBufferData(int i, long l, int j) {
      VkGlBuffer.glBufferData(i, l, j);
   }

   @Overwrite(remap = false)
   @NativeType("void *")
   public static ByteBuffer glMapBuffer(@NativeType("GLenum") int target, @NativeType("GLenum") int access) {
      return VkGlBuffer.glMapBuffer(target, access);
   }

   @Overwrite(remap = false)
   @NativeType("void *")
   @Nullable
   public static ByteBuffer glMapBuffer(@NativeType("GLenum") int target, @NativeType("GLenum") int access, long length, @Nullable ByteBuffer old_buffer) {
      return VkGlBuffer.glMapBuffer(target, access);
   }

   @Overwrite(remap = false)
   @NativeType("GLboolean")
   public static boolean glUnmapBuffer(@NativeType("GLenum") int target) {
      return VkGlBuffer.glUnmapBuffer(target);
   }

   @Overwrite(remap = false)
   public static void glDeleteBuffers(int i) {
      VkGlBuffer.glDeleteBuffers(i);
   }

   @Overwrite(remap = false)
   public static void glDeleteBuffers(@NativeType("GLuint const *") IntBuffer buffers) {
      VkGlBuffer.glDeleteBuffers(buffers);
   }
}
