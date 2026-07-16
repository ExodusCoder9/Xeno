package com.xeno.client.api;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for packing CPU data into Direct ByteBuffers according to OpenGL / Vulkan
 * layout rules (std140 and std430).
 * Handles alignment and padding automatically.
 */
public class XenoBufferPacker {
    public enum Layout {
        /** Standard layout for Uniform Buffers (UBOs). Strict 16-byte alignment rules. */
        STD140,
        /** Standard layout for Shader Storage Buffers (SSBOs). Relaxed alignment rules. */
        STD430
    }

    private final Layout layout;
    private ByteBuffer buffer;
    private int position = 0;

    public XenoBufferPacker(Layout layout, int initialCapacity) {
        this.layout = layout;
        this.buffer = ByteBuffer.allocateDirect(initialCapacity).order(ByteOrder.nativeOrder());
    }

    public static XenoBufferPacker std140(int initialCapacity) {
        return new XenoBufferPacker(Layout.STD140, initialCapacity);
    }

    public static XenoBufferPacker std430(int initialCapacity) {
        return new XenoBufferPacker(Layout.STD430, initialCapacity);
    }

    private void ensureCapacity(int neededBytes) {
        if (position + neededBytes > buffer.capacity()) {
            int newCapacity = Math.max(buffer.capacity() * 2, position + neededBytes);
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder());
            buffer.flip();
            newBuffer.put(buffer);
            buffer = newBuffer;
        }
    }

    private void align(int alignmentBytes) {
        int remainder = position % alignmentBytes;
        if (remainder != 0) {
            position += (alignmentBytes - remainder);
        }
    }

    public XenoBufferPacker putFloat(float val) {
        align(4);
        ensureCapacity(4);
        buffer.putFloat(position, val);
        position += 4;
        return this;
    }

    public XenoBufferPacker putInt(int val) {
        align(4);
        ensureCapacity(4);
        buffer.putInt(position, val);
        position += 4;
        return this;
    }

    public XenoBufferPacker putVec2(float x, float y) {
        align(8);
        ensureCapacity(8);
        buffer.putFloat(position, x);
        buffer.putFloat(position + 4, y);
        position += 8;
        return this;
    }

    public XenoBufferPacker putVec3(float x, float y, float z) {
        // std140 and std430 both align Vec3 to 16 bytes (size of Vec4)
        align(16);
        ensureCapacity(12);
        buffer.putFloat(position, x);
        buffer.putFloat(position + 4, y);
        buffer.putFloat(position + 8, z);
        position += 12; // size is 12 bytes, but next element will align based on its type
        return this;
    }

    public XenoBufferPacker putVec3(Vector3fc vec) {
        return putVec3(vec.x(), vec.y(), vec.z());
    }

    public XenoBufferPacker putVec4(float x, float y, float z, float w) {
        align(16);
        ensureCapacity(16);
        buffer.putFloat(position, x);
        buffer.putFloat(position + 4, y);
        buffer.putFloat(position + 8, z);
        buffer.putFloat(position + 12, w);
        position += 16;
        return this;
    }

    public XenoBufferPacker putVec4(Vector4fc vec) {
        return putVec4(vec.x(), vec.y(), vec.z(), vec.w());
    }

    /**
     * Packs a 4x4 column-major matrix.
     * In std140 and std430, a mat4 is treated as an array of 4 column vectors (aligned to 16 bytes each).
     */
    public XenoBufferPacker putMatrix4(Matrix4fc matrix) {
        align(16);
        ensureCapacity(64);
        float[] arr = new float[16];
        matrix.get(arr);
        for (int i = 0; i < 16; i++) {
            // column-major layout output
            buffer.putFloat(position + i * 4, arr[i]);
        }
        position += 64;
        return this;
    }

    /**
     * Resets the write pointer to the beginning of the buffer.
     */
    public XenoBufferPacker reset() {
        position = 0;
        buffer.clear();
        return this;
    }

    /**
     * Returns the direct ByteBuffer containing the packed data ready for GPU upload.
     */
    public ByteBuffer build() {
        // In std140, total struct size is padded to a multiple of 16 bytes if it contains arrays/structs
        if (layout == Layout.STD140) {
            align(16);
        }
        buffer.position(0);
        buffer.limit(position);
        return buffer;
    }

    public int getPosition() {
        return position;
    }
}
