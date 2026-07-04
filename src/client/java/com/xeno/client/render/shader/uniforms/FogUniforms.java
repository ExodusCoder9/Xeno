package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.fog.FogData;
import org.joml.Vector4f;

public class FogUniforms {
	private static final int SIZE = 48;

	public void update(FogData fog, Vector4f color) {}
	public void bind(RenderPass pass) {}
}
