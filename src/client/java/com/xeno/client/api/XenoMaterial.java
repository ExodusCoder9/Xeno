package com.xeno.client.api;

import java.util.function.Consumer;

/**
 * Defines a custom rendering material configuration.
 * <p>
 * A material combines a {@link XenoShader} with specific pipeline states (backface culling,
 * alpha blending, depth tests, and depth buffer writing) and a uniform binder callback that
 * executes right before rendering chunk geometry.
 * </p>
 */
public class XenoMaterial {
    private final String name;
    private XenoShader shader;
    private boolean doubleSided = false;
    private boolean blend = false;
    private boolean depthTest = true;
    private boolean writeDepth = true;
    private Consumer<XenoShader> uniformBinder;

    /**
     * Constructs a new {@code XenoMaterial}.
     *
     * @param name   the unique material name identifier
     * @param shader the {@link XenoShader} to bind when this material is active
     */
    public XenoMaterial(String name, XenoShader shader) {
        this.name = name;
        this.shader = shader;
    }

    /**
     * Gets the unique name of this material.
     *
     * @return the name string
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the current shader program assigned to this material.
     *
     * @return the {@link XenoShader}
     */
    public XenoShader getShader() {
        return shader;
    }

    /**
     * Assigns a different shader program to this material.
     *
     * @param shader the new {@link XenoShader}
     * @return this material instance (for builder pattern chaining)
     */
    public XenoMaterial setShader(XenoShader shader) {
        this.shader = shader;
        return this;
    }

    /**
     * Checks if this material renders double-sided (with backface culling disabled).
     *
     * @return {@code true} if double-sided; {@code false} if culling is active
     */
    public boolean isDoubleSided() {
        return doubleSided;
    }

    /**
     * Sets whether this material should disable backface culling (render both sides of quads).
     *
     * @param doubleSided {@code true} to disable culling; {@code false} to enable culling
     * @return this material instance (for builder pattern chaining)
     */
    public XenoMaterial setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        return this;
    }

    /**
     * Checks if alpha blending is active for this material.
     *
     * @return {@code true} if blending is enabled
     */
    public boolean isBlend() {
        return blend;
    }

    /**
     * Sets whether alpha blending should be active during rendering.
     *
     * @param blend {@code true} to enable blend; {@code false} to disable
     * @return this material instance (for builder pattern chaining)
     */
    public XenoMaterial setBlend(boolean blend) {
        this.blend = blend;
        return this;
    }

    /**
     * Checks if depth testing is performed when drawing this material.
     *
     * @return {@code true} if depth test is active
     */
    public boolean isDepthTest() {
        return depthTest;
    }

    /**
     * Configures whether depth testing should be active when rendering.
     *
     * @param depthTest {@code true} to perform depth tests; {@code false} to bypass
     * @return this material instance (for builder pattern chaining)
     */
    public XenoMaterial setDepthTest(boolean depthTest) {
        this.depthTest = depthTest;
        return this;
    }

    /**
     * Checks if this material writes its fragments' depth to the depth buffer.
     *
     * @return {@code true} if depth writes are enabled
     */
    public boolean isWriteDepth() {
        return writeDepth;
    }

    /**
     * Sets whether rendering should write depth information to the depth buffer.
     *
     * @param writeDepth {@code true} to write depth; {@code false} to lock depth buffer
     * @return this material instance (for builder pattern chaining)
     */
    public XenoMaterial setWriteDepth(boolean writeDepth) {
        this.writeDepth = writeDepth;
        return this;
    }

    /**
     * Registers a callback to bind dynamic uniforms (e.g. system time, wind vector, camera info)
     * right before rendering.
     *
     * @param uniformBinder a consumer that receives the active {@link XenoShader} and updates its uniforms
     * @return this material instance (for builder pattern chaining)
     */
    public XenoMaterial setUniformBinder(Consumer<XenoShader> uniformBinder) {
        this.uniformBinder = uniformBinder;
        return this;
    }

    /**
     * Invokes the uniform binder callback if registered, updating uniforms on the active shader.
     */
    public void applyUniforms() {
        if (uniformBinder != null && shader != null) {
            uniformBinder.accept(shader);
        }
    }
}
