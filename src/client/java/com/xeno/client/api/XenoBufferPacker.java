package com.xeno.client.api;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility helper for packing Java/CPU variables into direct byte buffers matching layout
 * rules of GLSL structures on the GPU.
 * <p>
 * Handles alignment offset math and structure padding automatically for:
 * </p>
 * <ul>
 * <li>{@code std140}: Used for Uniform Buffer Objects (UBOs). Vectors are aligned to 16-byte boundaries.
 * Array elements are padded to multiples of 16 bytes.</li>
 * <li>{@code std430}: Used for Shader Storage Buffer Objects (SSBOs). Relaxed, tighter alignment rules
 * matching normal structures closer.</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * XenoBufferPacker packer = XenoBufferPacker.std140(128);
 * packer.putFloat(time);
 * packer.putVec3(0.5F, 1.0F, 0.0F); // aligned to 16-byte boundary
 * packer.putMatrix4(projectionMatrix); // aligned to 16-byte boundary, occupying 64 bytes
 * ByteBuffer data = packer.build();
 * }</pre>
 */
public class XenoBufferPacker {
    
    /**
     * Supported layout structures.
     */
    public enum Layout {
        /** Uniform Buffer Object layout specification with strict 16-byte alignment rules. */
        STD140,
        /** Shader Storage Buffer Object layout specification with relaxed padding rules. */
        STD430
    }

    private final Layout layout;
    private ByteBuffer buffer;
    private int position = 0;

    /**
     * Constructs a new {@code XenoBufferPacker} with the specified layout.
     *
     * @param layout          the target buffer layout structure
     * @param initialCapacity the initial allocation capacity of the direct ByteBuffer
     */
    public XenoBufferPacker(Layout layout, int initialCapacity) {
        this.layout = layout;
        this.buffer = ByteBuffer.allocateDirect(initialCapacity).order(ByteOrder.nativeOrder());
    }

    /**
     * Creates a new packer configured for {@code std140} layouts.
     *
     * @param initialCapacity initial buffer capacity in bytes
     * @return a new packer instance
     */
    public static XenoBufferPacker std140(int initialCapacity) {
        return new XenoBufferPacker(Layout.STD140, initialCapacity);
    }

    /**
     * Creates a new packer configured for {@code std430} layouts.
     *
     * @param initialCapacity initial buffer capacity in bytes
     * @return a new packer instance
     */
    public static XenoBufferPacker std430(int initialCapacity) {
        return new XenoBufferPacker(Layout.STD430, initialCapacity);
    }

    /**
     * Verifies if the buffer has enough space for writing. Resizes if capacity is exceeded.
     *
     * @param neededBytes number of bytes needed
     */
    private void ensureCapacity(int neededBytes) {
        if (position + neededBytes > buffer.capacity()) {
            int newCapacity = Math.max(buffer.capacity() * 2, position + neededBytes);
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder());
            buffer.flip();
            newBuffer.put(buffer);
            buffer = newBuffer;
        }
    }

    /**
     * Aligns the write position to a multiple of {@code alignmentBytes}.
     *
     * @param alignmentBytes boundary alignment constraint (in bytes)
     */
    private void align(int alignmentBytes) {
        int remainder = position % alignmentBytes;
        if (remainder != 0) {
            position += (alignmentBytes - remainder);
        }
    }

    /**
     * Packs a float scalar (aligned to a 4-byte boundary).
     *
     * @param val the float value
     * @return this packer instance
     */
    public XenoBufferPacker putFloat(float val) {
        align(4);
        ensureCapacity(4);
        buffer.putFloat(position, val);
        position += 4;
        return this;
    }

    /**
     * Packs an integer scalar (aligned to a 4-byte boundary).
     *
     * @param val the integer value
     * @return this packer instance
     */
    public XenoBufferPacker putInt(int val) {
        align(4);
        ensureCapacity(4);
        buffer.putInt(position, val);
        position += 4;
        return this;
    }

    /**
     * Packs a 2D vector (aligned to an 8-byte boundary).
     *
     * @param x the X component
     * @param y the Y component
     * @return this packer instance
     */
    public XenoBufferPacker putVec2(float x, float y) {
        align(8);
        ensureCapacity(8);
        buffer.putFloat(position, x);
        buffer.putFloat(position + 4, y);
        position += 8;
        return this;
    }

    /**
     * Packs a 3D vector (aligned to a 16-byte boundary).
     * <p>
     * Note: In both {@code std140} and {@code std430}, 3-component vectors are treated as having
     * a base alignment of 16 bytes (equivalent to Vec4).
     * </p>
     *
     * @param x the X component
     * @param y the Y component
     * @param z the Z component
     * @return this packer instance
     */
    public XenoBufferPacker putVec3(float x, float y, float z) {
        align(16);
        ensureCapacity(12);
        buffer.putFloat(position, x);
        buffer.putFloat(position + 4, y);
        buffer.putFloat(position + 8, z);
        position += 12; // Occupies 12 bytes; alignment boundary for next element holds.
        return this;
    }

    /**
     * Packs a 3D vector (aligned to a 16-byte boundary).
     *
     * @param vec the JOML vector
     * @return this packer instance
     */
    public XenoBufferPacker putVec3(Vector3fc vec) {
        return putVec3(vec.x(), vec.y(), vec.z());
    }

    /**
     * Packs a 4D vector (aligned to a 16-byte boundary).
     *
     * @param x the X component
     * @param y the Y component
     * @param z the Z component
     * @param w the W component
     * @return this packer instance
     */
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

    /**
     * Packs a 4D vector (aligned to a 16-byte boundary).
     *
     * @param vec the JOML vector
     * @return this packer instance
     */
    public XenoBufferPacker putVec4(Vector4fc vec) {
        return putVec4(vec.x(), vec.y(), vec.z(), vec.w());
    }

    /**
     * Packs a 4x4 matrix (aligned to a 16-byte boundary, occupying 64 bytes total).
     * <p>
     * Mat4 structures are packed as an array of four 4-component vectors (column vectors).
     * </p>
     *
     * @param matrix the JOML matrix
     * @return this packer instance
     */
    public XenoBufferPacker putMatrix4(Matrix4fc matrix) {
        align(16);
        ensureCapacity(64);
        float[] arr = new float[16];
        matrix.get(arr);
        for (int i = 0; i < 16; i++) {
            buffer.putFloat(position + i * 4, arr[i]);
        }
        position += 64;
        return this;
    }

    /**
     * Resets the writing cursor to the beginning of the buffer.
     * Useful for reusing a packer instance across frames.
     *
     * @return this packer instance
     */
    public XenoBufferPacker reset() {
        position = 0;
        buffer.clear();
        return this;
    }

    /**
     * Standardizes structure size constraints (e.g. padding to multiples of 16 bytes for std140)
     * and returns the bytebuffer positioned ready for GPU upload.
     *
     * @return a direct {@link ByteBuffer} containing the packed data
     */
    public ByteBuffer build() {
        if (layout == Layout.STD140) {
            align(16);
        }
        buffer.position(0);
        buffer.limit(position);
        return buffer;
    }

    /**
     * Gets the current write position in bytes.
     *
     * @return the byte offset position
     */
    public int getPosition() {
        return position;
    }
}
