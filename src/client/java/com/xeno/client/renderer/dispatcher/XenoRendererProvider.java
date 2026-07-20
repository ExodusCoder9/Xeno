package com.xeno.client.renderer.dispatcher;

/**
 * Interface implemented by SectionRenderDispatcher via Mixin to expose our custom renderer.
 */
public interface XenoRendererProvider {
    IXenoSectionRenderer xeno$getRenderer();
    void xeno$setRenderer(IXenoSectionRenderer renderer);
}
