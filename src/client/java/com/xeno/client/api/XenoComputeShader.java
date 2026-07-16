package com.xeno.client.api;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates an OpenGL Compute Shader, representing a pipeline designed to run arbitrary mathematical
 * operations on the GPU (General Purpose GPU compute, or GPGPU).
 * <p>
 * This class abstracts buffer bindings (Shader Storage Buffer Objects / SSBOs), execution dispatches,
 * uniform uploads, and memory barrier synchronization.
 * </p>
 * <p>
 * <strong>Important Platform Restriction:</strong> Compute Shaders require OpenGL 4.3 or higher.
 * Systems like macOS only support OpenGL up to version 4.1 Core Profile, which lacks compute shader capabilities.
 * This class includes runtime checks via {@link #isSupported()} to prevent crashes on incompatible platforms.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * XenoComputeShader compute = new XenoComputeShader("particles");
 * if (compute.isSupported() && compute.compile(shaderSource)) {
 *     compute.bind();
 *     compute.bindBuffer(myBufferId, 0); // bind SSBO to slot 0
 *     compute.dispatch(64, 1, 1);        // dispatch thread groups
 *     compute.memoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
 *     compute.unbind();
 * }
 * }</pre>
 */
public class XenoComputeShader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Xeno-API");
    
    private final String name;
    private int programId = 0;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();
    private final boolean isSupported;

    /**
     * Constructs a new {@code XenoComputeShader} container.
     * Checks client OpenGL capabilities to set the support flag.
     *
     * @param name a unique name identifier for this shader (used for logging and debugging)
     */
    public XenoComputeShader(String name) {
        this.name = name;
        // macOS does not support OpenGL 4.3 (caps at 4.1). Check capabilities.
        this.isSupported = GL.getCapabilities().OpenGL43;
        if (!isSupported) {
            LOGGER.warn("Compute Shaders are not supported on this platform (OpenGL 4.3 is required). '{}' will be disabled.", name);
        }
    }

    /**
     * Compiles and links the compute shader.
     * Does nothing and returns {@code false} if compute shaders are not supported on the platform.
     *
     * @param computeSource the raw GLSL source code for the compute shader
     * @return {@code true} if compiling and linking succeeded; {@code false} otherwise
     */
    public boolean compile(String computeSource) {
        if (!isSupported) {
            return false;
        }

        if (programId != 0) {
            delete();
        }

        int computeShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(computeShader, computeSource);
        GL20.glCompileShader(computeShader);

        int compiled = GL20.glGetShaderi(computeShader, GL20.GL_COMPILE_STATUS);
        if (compiled == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(computeShader, 1024);
            LOGGER.error("Failed to compile Compute shader for '{}':\n{}", name, log);
            GL20.glDeleteShader(computeShader);
            return false;
        }

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, computeShader);
        GL20.glLinkProgram(programId);

        int linked = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS);
        if (linked == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(programId, 1024);
            LOGGER.error("Failed to link compute shader program '{}':\n{}", name, log);
            GL20.glDeleteProgram(programId);
            programId = 0;
            return false;
        }

        GL20.glDeleteShader(computeShader);
        LOGGER.info("Compute Shader '{}' compiled and linked successfully with Program ID: {}", name, programId);
        uniformLocationCache.clear();
        return true;
    }

    /**
     * Installs this compute shader program as part of the current OpenGL pipeline.
     */
    public void bind() {
        if (programId != 0 && isSupported) {
            GL20.glUseProgram(programId);
        }
    }

    /**
     * Unbinds the current compute shader program.
     */
    public void unbind() {
        if (isSupported) {
            GL20.glUseProgram(0);
        }
    }

    /**
     * Binds a GPU Buffer (Shader Storage Buffer Object, or SSBO) to a compute shader binding index.
     *
     * @param bufferId     the raw OpenGL Buffer ID (obtained from GpuBuffer or glGenBuffers)
     * @param bindingIndex the layout binding index defined in the GLSL compute shader
     */
    public void bindBuffer(int bufferId, int bindingIndex) {
        if (isSupported) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingIndex, bufferId);
        }
    }

    /**
     * Dispatches the compute shader execution, specifying the number of work groups.
     *
     * @param numGroupsX number of work groups in the X dimension
     * @param numGroupsY number of work groups in the Y dimension
     * @param numGroupsZ number of work groups in the Z dimension
     */
    public void dispatch(int numGroupsX, int numGroupsY, int numGroupsZ) {
        if (programId != 0 && isSupported) {
            GL43.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
        }
    }

    /**
     * Inserts a memory barrier to synchronize buffer reads and writes.
     * Ensures that subsequent draw calls or shader reads can safely read variables updated by this compute shader.
     *
     * @param barriers the bitfield of barriers to insert (e.g. {@link GL43#GL_SHADER_STORAGE_BARRIER_BIT} or {@link GL43#GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT})
     */
    public void memoryBarrier(int barriers) {
        if (isSupported) {
            GL43.glMemoryBarrier(barriers);
        }
    }

    /**
     * Retrieves the location of a uniform variable by name. Locations are cached after lookup.
     *
     * @param name the case-sensitive name of the uniform defined in the compute shader
     * @return the uniform location ID, or {@code -1} if not found
     */
    public int getUniformLocation(String name) {
        if (programId == 0 || !isSupported) return -1;
        return uniformLocationCache.computeIfAbsent(name, k -> GL20.glGetUniformLocation(programId, k));
    }

    /**
     * Uploads an integer uniform.
     *
     * @param name the uniform name
     * @param val  the integer value
     */
    public void setUniform(String name, int val) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform1i(loc, val);
    }

    /**
     * Uploads a float uniform.
     *
     * @param name the uniform name
     * @param val  the float value
     */
    public void setUniform(String name, float val) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform1f(loc, val);
    }

    /**
     * Uploads a 3D float vector uniform.
     *
     * @param name the uniform name
     * @param x    the X component
     * @param y    the Y component
     * @param z    the Z component
     */
    public void setUniform(String name, float x, float y, float z) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform3f(loc, x, y, z);
    }

    /**
     * Deletes the compiled compute program from GPU memory and invalidates this container.
     */
    public void delete() {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
            programId = 0;
            uniformLocationCache.clear();
        }
    }

    /**
     * Checks if compute shaders are supported on this client system.
     *
     * @return {@code true} if OpenGL 4.3+ is available; {@code false} otherwise
     */
    public boolean isSupported() {
        return isSupported;
    }

    /**
     * Gets the raw OpenGL program ID handle.
     *
     * @return the program ID
     */
    public int getProgramId() {
        return programId;
    }

    /**
     * Gets the unique name of this compute container.
     *
     * @return the name string
     */
    public String getName() {
        return name;
    }
}
