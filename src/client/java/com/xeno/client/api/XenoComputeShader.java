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
 * Represents an OpenGL Compute Shader (requires OpenGL 4.3+).
 * This allows other mods to perform GPU-accelerated calculations (e.g. physics simulations,
 * particle behaviors, or advanced culling routines) within the Xeno framework.
 */
public class XenoComputeShader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Xeno-API");
    
    private final String name;
    private int programId = 0;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();
    private final boolean isSupported;

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
     * @param computeSource The Compute Shader GLSL source code.
     * @return True if compilation was successful.
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

    public void bind() {
        if (programId != 0 && isSupported) {
            GL20.glUseProgram(programId);
        }
    }

    public void unbind() {
        if (isSupported) {
            GL20.glUseProgram(0);
        }
    }

    /**
     * Binds a GPU Buffer (Shader Storage Buffer Object, or SSBO) to a compute shader binding index.
     * @param bufferId The OpenGL Buffer ID (obtained from GpuBuffer or glGenBuffers)
     * @param bindingIndex The layout binding index defined in the compute shader
     */
    public void bindBuffer(int bufferId, int bindingIndex) {
        if (isSupported) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingIndex, bufferId);
        }
    }

    /**
     * Dispatches the compute shader execution.
     * @param numGroupsX Thread groups in X dimension
     * @param numGroupsY Thread groups in Y dimension
     * @param numGroupsZ Thread groups in Z dimension
     */
    public void dispatch(int numGroupsX, int numGroupsY, int numGroupsZ) {
        if (programId != 0 && isSupported) {
            GL43.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
        }
    }

    /**
     * Inserts a memory barrier to sync shader writes with subsequent reads.
     * Typically GL_SHADER_STORAGE_BARRIER_BIT or GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT.
     */
    public void memoryBarrier(int barriers) {
        if (isSupported) {
            GL43.glMemoryBarrier(barriers);
        }
    }

    public int getUniformLocation(String name) {
        if (programId == 0 || !isSupported) return -1;
        return uniformLocationCache.computeIfAbsent(name, k -> GL20.glGetUniformLocation(programId, k));
    }

    public void setUniform(String name, int val) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform1i(loc, val);
    }

    public void setUniform(String name, float val) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform1f(loc, val);
    }

    public void setUniform(String name, float x, float y, float z) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform3f(loc, x, y, z);
    }

    public void delete() {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
            programId = 0;
            uniformLocationCache.clear();
        }
    }

    public boolean isSupported() {
        return isSupported;
    }

    public int getProgramId() {
        return programId;
    }

    public String getName() {
        return name;
    }
}
