package com.xeno.client.api;

import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates a compiled OpenGL Shader Program consisting of a vertex shader and a fragment shader.
 * <p>
 * This class provides helper methods to compile individual shader stages from source code,
 * link them into an active program ID, bind/unbind the program, and upload various uniform
 * variables (ints, floats, vectors, and matrices) to the GPU.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * XenoShader customShader = new XenoShader("my_shader");
 * if (customShader.compile(vertexShaderSrc, fragmentShaderSrc)) {
 *     customShader.bind();
 *     customShader.setUniform("u_Time", time);
 * }
 * }</pre>
 *
 * @see XenoRenderAPI
 */
public class XenoShader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Xeno-API");
    
    private final String name;
    private int programId = 0;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();

    /**
     * Constructs a new {@code XenoShader} container.
     *
     * @param name a unique name identifier for this shader (used for logging and debugging)
     */
    public XenoShader(String name) {
        this.name = name;
    }

    /**
     * Compiles the vertex and fragment GLSL source codes and links them into an OpenGL program.
     * <p>
     * If this shader container already has a compiled program, it is deleted from GPU memory first.
     * If compilation or linking fails, errors are outputted to logs and this method returns {@code false}.
     * </p>
     *
     * @param vertexSource   the raw GLSL source code for the vertex shader
     * @param fragmentSource the raw GLSL source code for the fragment shader
     * @return {@code true} if compiling and linking succeeded; {@code false} otherwise
     */
    public boolean compile(String vertexSource, String fragmentSource) {
        if (programId != 0) {
            delete();
        }

        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return false;

        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            GL20.glDeleteShader(vertexShader);
            return false;
        }

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexShader);
        GL20.glAttachShader(programId, fragmentShader);
        GL20.glLinkProgram(programId);

        // Check link status
        int linked = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS);
        if (linked == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(programId, 1024);
            LOGGER.error("Failed to link shader program '{}':\n{}", name, log);
            GL20.glDeleteProgram(programId);
            programId = 0;
            return false;
        }

        // Clean up individual shaders as they are linked into the program now
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        LOGGER.info("Shader '{}' compiled and linked successfully with Program ID: {}", name, programId);
        uniformLocationCache.clear();
        return true;
    }

    /**
     * Compiles an individual shader stage (Vertex or Fragment).
     *
     * @param type   the OpenGL shader type (e.g. {@link GL20#GL_VERTEX_SHADER} or {@link GL20#GL_FRAGMENT_SHADER})
     * @param source the GLSL source code string
     * @return the compiled shader object ID, or {@code 0} if compilation failed
     */
    private int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        int compiled = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (compiled == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 1024);
            String shaderType = (type == GL20.GL_VERTEX_SHADER) ? "Vertex" : "Fragment";
            LOGGER.error("Failed to compile {} shader for '{}':\n{}", shaderType, name, log);
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    /**
     * Installs this shader program as part of the current OpenGL rendering state.
     * Does nothing if the shader has not been compiled successfully.
     */
    public void bind() {
        if (programId != 0) {
            GL20.glUseProgram(programId);
        }
    }

    /**
     * Uninstalls the current shader program by binding program ID {@code 0}.
     */
    public void unbind() {
        GL20.glUseProgram(0);
    }

    /**
     * Retrieves the location of a uniform variable by name. Locations are cached after lookup.
     *
     * @param name the case-sensitive name of the uniform defined in the GLSL shader
     * @return the uniform location ID, or {@code -1} if the uniform was not found or is inactive
     */
    public int getUniformLocation(String name) {
        if (programId == 0) return -1;
        return uniformLocationCache.computeIfAbsent(name, k -> GL20.glGetUniformLocation(programId, k));
    }

    /**
     * Uploads an integer uniform value.
     *
     * @param name the uniform name
     * @param val  the integer value
     */
    public void setUniform(String name, int val) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform1i(loc, val);
    }

    /**
     * Uploads a float uniform value.
     *
     * @param name the uniform name
     * @param val  the float value
     */
    public void setUniform(String name, float val) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform1f(loc, val);
    }

    /**
     * Uploads a 3D float vector uniform value.
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
     * Uploads a 4D float vector uniform value.
     *
     * @param name the uniform name
     * @param x    the X component
     * @param y    the Y component
     * @param z    the Z component
     * @param w    the W component
     */
    public void setUniform(String name, float x, float y, float z, float w) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform4f(loc, x, y, z, w);
    }

    /**
     * Uploads a 4x4 matrix uniform value.
     *
     * @param name   the uniform name
     * @param matrix a 16-element float array representing a 4x4 column-major matrix
     */
    public void setUniformMatrix4(String name, float[] matrix) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniformMatrix4fv(loc, false, matrix);
    }

    /**
     * Deletes the compiled shader program from GPU memory and invalidates this container.
     */
    public void delete() {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
            programId = 0;
            uniformLocationCache.clear();
        }
    }

    /**
     * Gets the raw OpenGL program ID handle.
     *
     * @return the OpenGL program ID, or {@code 0} if not compiled
     */
    public int getProgramId() {
        return programId;
    }

    /**
     * Gets the unique name of this shader container.
     *
     * @return the name string
     */
    public String getName() {
        return name;
    }
}
