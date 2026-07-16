package com.xeno.client.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The central manager and registry for the Xeno Rendering and Shader API (XRA).
 * <p>
 * Other mods should interact with this class to register custom shaders, compute shaders,
 * material states, block material associations, frame-graph builders, and meshing hooks.
 * </p>
 * <p>
 * All internal registries utilize thread-safe collections (like {@link ConcurrentHashMap} and {@link CopyOnWriteArrayList})
 * to support concurrent reads and writes from the client rendering thread and background compilation threads.
 * </p>
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

    private XenoRenderAPI() {
        // Prevent instantiation of utility class
    }

    /**
     * Registers a custom vertex/fragment shader container.
     *
     * @param shader the {@link XenoShader} instance to register
     */
    public static void registerShader(XenoShader shader) {
        SHADERS.put(shader.getName(), shader);
    }

    /**
     * Retrieves a registered shader by its name.
     *
     * @param name the unique shader name identifier
     * @return the {@link XenoShader} instance, or {@code null} if not found
     */
    public static XenoShader getShader(String name) {
        return SHADERS.get(name);
    }

    /**
     * Registers a custom compute shader container (OpenGL 4.3+).
     *
     * @param shader the {@link XenoComputeShader} instance to register
     */
    public static void registerComputeShader(XenoComputeShader shader) {
        COMPUTE_SHADERS.put(shader.getName(), shader);
    }

    /**
     * Retrieves a registered compute shader by its name.
     *
     * @param name the unique compute shader name identifier
     * @return the {@link XenoComputeShader} instance, or {@code null} if not found
     */
    public static XenoComputeShader getComputeShader(String name) {
        return COMPUTE_SHADERS.get(name);
    }

    /**
     * Registers a custom material profile.
     *
     * @param material the {@link XenoMaterial} instance to register
     */
    public static void registerMaterial(XenoMaterial material) {
        MATERIALS.put(material.getName(), material);
    }

    /**
     * Retrieves a registered material profile by its name.
     *
     * @param name the unique material name identifier
     * @return the {@link XenoMaterial} instance, or {@code null} if not found
     */
    public static XenoMaterial getMaterial(String name) {
        return MATERIALS.get(name);
    }

    /**
     * Binds a registered material to a specific BlockState.
     * Used by the section compiler to apply custom shaders/states to blocks during meshing.
     *
     * @param state    the specific {@link BlockState}
     * @param material the {@link XenoMaterial} to associate with the state
     */
    public static void registerBlockMaterial(BlockState state, XenoMaterial material) {
        BLOCK_MATERIALS.put(state, material);
    }

    /**
     * Binds a registered material to all possible BlockStates of a block by its identifier string.
     *
     * @param blockId  the resource identifier string (e.g. {@code "minecraft:gold_block"} or {@code "my_mod:crystal"})
     * @param material the {@link XenoMaterial} to associate with the block states
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

    /**
     * Gets the custom material profile bound to a BlockState.
     *
     * @param state the {@link BlockState}
     * @return the associated {@link XenoMaterial}, or {@code null} if none is bound
     */
    public static XenoMaterial getBlockMaterial(BlockState state) {
        return BLOCK_MATERIALS.get(state);
    }

    /**
     * Registers a legacy immediate-mode render pass at a designated location in the render loop.
     *
     * @param position the execution {@link XenoRenderPass.Position} stage
     * @param pass     the rendering callback implementation
     */
    public static void registerRenderPass(XenoRenderPass.Position position, XenoRenderPass pass) {
        RENDER_PASSES.get(position).add(pass);
    }

    /**
     * Retrieves the list of all registered immediate-mode render passes for a designated position.
     *
     * @param position the execution stage
     * @return a thread-safe list of {@link XenoRenderPass}es
     */
    public static List<XenoRenderPass> getRenderPasses(XenoRenderPass.Position position) {
        return RENDER_PASSES.get(position);
    }

    /**
     * Registers an async chunk section meshing hook.
     *
     * @param hook the meshing hook callback
     */
    public static void registerMeshingHook(XenoMeshingHook hook) {
        MESHING_HOOKS.add(hook);
    }

    /**
     * Retrieves the list of all registered chunk section meshing hooks.
     *
     * @return a thread-safe list of {@link XenoMeshingHook}s
     */
    public static List<XenoMeshingHook> getMeshingHooks() {
        return MESHING_HOOKS;
    }

    /**
     * Registers a custom frame graph pass builder.
     * Allows mods to declare inputs/outputs and append operations directly into Mojang's Blaze3D frame graph.
     *
     * @param builder the pass builder callback
     */
    public static void registerFramePassBuilder(XenoFramePassBuilder builder) {
        FRAME_PASS_BUILDERS.add(builder);
    }

    /**
     * Retrieves the list of all registered frame graph pass builders.
     *
     * @return a thread-safe list of {@link XenoFramePassBuilder}s
     */
    public static List<XenoFramePassBuilder> getFramePassBuilders() {
        return FRAME_PASS_BUILDERS;
    }
}
