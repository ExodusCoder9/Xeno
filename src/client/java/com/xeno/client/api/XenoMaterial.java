package com.xeno.client.api;

import java.util.function.Consumer;

/**
 * Defines a custom rendering material. A material binds a XenoShader, configures GL state,
 * and supplies custom uniforms right before rendering chunk geometry.
 */
public class XenoMaterial {
    private final String name;
    private XenoShader shader;
    private boolean doubleSided = false;
    private boolean blend = false;
    private boolean depthTest = true;
    private boolean writeDepth = true;
    private Consumer<XenoShader> uniformBinder;

    public XenoMaterial(String name, XenoShader shader) {
        this.name = name;
        this.shader = shader;
    }

    public String getName() {
        return name;
    }

    public XenoShader getShader() {
        return shader;
    }

    public XenoMaterial setShader(XenoShader shader) {
        this.shader = shader;
        return this;
    }

    public boolean isDoubleSided() {
        return doubleSided;
    }

    public XenoMaterial setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        return this;
    }

    public boolean isBlend() {
        return blend;
    }

    public XenoMaterial setBlend(boolean blend) {
        this.blend = blend;
        return this;
    }

    public boolean isDepthTest() {
        return depthTest;
    }

    public XenoMaterial setDepthTest(boolean depthTest) {
        this.depthTest = depthTest;
        return this;
    }

    public boolean isWriteDepth() {
        return writeDepth;
    }

    public XenoMaterial setWriteDepth(boolean writeDepth) {
        this.writeDepth = writeDepth;
        return this;
    }

    /**
     * Sets a callback that binds custom uniforms (e.g. game time, wind vectors)
     * right before rendering.
     */
    public XenoMaterial setUniformBinder(Consumer<XenoShader> uniformBinder) {
        this.uniformBinder = uniformBinder;
        return this;
    }

    public void applyUniforms() {
        if (uniformBinder != null && shader != null) {
            uniformBinder.accept(shader);
        }
    }
}
