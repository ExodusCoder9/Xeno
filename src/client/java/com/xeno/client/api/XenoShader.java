package com.xeno.client.api;

import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a compiled OpenGL Shader program (Vertex & Fragment) for Xeno.
 * Other mods can use this to create custom visual effects and materials.
 */
public class XenoShader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Xeno-API");
    
    private final String name;
    private int programId = 0;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();

    public XenoShader(String name) {
        this.name = name;
    }

    /**
     * Compiles and links the shader.
     * @param vertexSource The Vertex Shader GLSL source code.
     * @param fragmentSource The Fragment Shader GLSL source code.
     * @return True if compilation was successful.
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

    public void bind() {
        if (programId != 0) {
            GL20.glUseProgram(programId);
        }
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int getUniformLocation(String name) {
        if (programId == 0) return -1;
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

    public void setUniform(String name, float x, float y, float z, float w) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniform4f(loc, x, y, z, w);
    }

    public void setUniformMatrix4(String name, float[] matrix) {
        int loc = getUniformLocation(name);
        if (loc != -1) GL20.glUniformMatrix4fv(loc, false, matrix);
    }

    public void delete() {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
            programId = 0;
            uniformLocationCache.clear();
        }
    }

    public int getProgramId() {
        return programId;
    }

    public String getName() {
        return name;
    }
}
