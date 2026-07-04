package com.xeno.client.render.chunk.compile;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.SectionPos;

public class SectionCompilerWrapper {
	private final SectionCompiler compiler;

	public SectionCompilerWrapper(SectionCompiler compiler) {
		this.compiler = compiler;
	}

	public SectionCompiler getCompiler() {
		return compiler;
	}
}
