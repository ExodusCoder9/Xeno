package com.xeno.client.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The entry point for the Xeno Rendering and Shader API.
 * Other mods can use this to register custom rendering logic, shaders, compute shaders, and materials.
 */
public final class XenoRenderAPI {
    private static final Map<String, XenoShader> SHADERS = new ConcurrentHashMap<>();
    private static final Map<String, XenoComputeShader> COMPUTE_SHADERS = new ConcurrentHashMap<>();
    private static final Map<String, XenoMaterial> MATERIALS = new ConcurrentHashMap<>();
    private static final Map<BlockState, XenoMaterial> BLOCK_MATERIALS = new ConcurrentHashMap<>();
    private static final Map<XenoRenderPass.Position, List<XenoRenderPass>> RENDER_PASSES = new ConcurrentHashMap<>();
    private static final List<XenoMeshingHook> MESHING_HOOKS = new CopyOnWriteArrayList<>();
    private static final List<XenoFramePassBuilder> FRAME_PASS_BUILDERS = new CopyOnWriteArrayList<>();

    static {
        for (XenoRenderPass.Position pos : XenoRenderPass.Position.values()) {
            RENDER_PASSES.put(pos, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Registers a custom vertex/fragment shader.
     */
    public static void registerShader(XenoShader shader) {
        SHADERS.put(shader.getName(), shader);
    }

    public static XenoShader getShader(String name) {
        return SHADERS.get(name);
    }

    /**
     * Registers a custom compute shader (OpenGL 4.3+).
     */
    public static void registerComputeShader(XenoComputeShader shader) {
        COMPUTE_SHADERS.put(shader.getName(), shader);
    }

    public static XenoComputeShader getComputeShader(String name) {
        return COMPUTE_SHADERS.get(name);
    }

    /**
     * Registers a custom material.
     */
    public static void registerMaterial(XenoMaterial material) {
        MATERIALS.put(material.getName(), material);
    }

    public static XenoMaterial getMaterial(String name) {
        return MATERIALS.get(name);
    }

    /**
     * Binds a registered material to a specific BlockState.
     */
    public static void registerBlockMaterial(BlockState state, XenoMaterial material) {
        BLOCK_MATERIALS.put(state, material);
    }

    /**
     * Binds a registered material to all states of a block by its Identifier string (e.g. "minecraft:gold_block").
     */
    public static void registerBlockMaterial(String blockId, XenoMaterial material) {
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier != null) {
            BuiltInRegistries.BLOCK.getOptional(identifier).ifPresent(block -> {
                block.getStateDefinition().getPossibleStates().forEach(state -> {
                    registerBlockMaterial(state, material);
                });
            });
        }
    }

    public static XenoMaterial getBlockMaterial(BlockState state) {
        return BLOCK_MATERIALS.get(state);
    }

    /**
     * Registers a custom render pass at a specific point in the rendering pipeline.
     */
    public static void registerRenderPass(XenoRenderPass.Position position, XenoRenderPass pass) {
        RENDER_PASSES.get(position).add(pass);
    }

    public static List<XenoRenderPass> getRenderPasses(XenoRenderPass.Position position) {
        return RENDER_PASSES.get(position);
    }

    /**
     * Registers a hook called during chunk meshing for custom block state processing.
     */
    public static void registerMeshingHook(XenoMeshingHook hook) {
        MESHING_HOOKS.add(hook);
    }

    public static List<XenoMeshingHook> getMeshingHooks() {
        return MESHING_HOOKS;
    }

    /**
     * Registers a custom frame graph pass builder.
     */
    public static void registerFramePassBuilder(XenoFramePassBuilder builder) {
        FRAME_PASS_BUILDERS.add(builder);
    }

    public static List<XenoFramePassBuilder> getFramePassBuilders() {
        return FRAME_PASS_BUILDERS;
    }
}
