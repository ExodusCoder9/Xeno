package com.xeno.client.renderer.culling;

/**
 * Interface implemented by SectionOcclusionGraph via Mixin to expose the Xeno Culling Manager.
 */
public interface XenoCullingManagerProvider {
    IXenoCullingManager xeno$getCullingManager();
}
