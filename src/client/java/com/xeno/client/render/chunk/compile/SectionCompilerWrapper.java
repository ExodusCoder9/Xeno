package com.xeno.client.render.chunk.compile;

import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;

public class SectionCompilerWrapper {
	private final SectionCompiler compiler;

	public SectionCompilerWrapper(SectionCompiler compiler) {
		this.compiler = compiler;
	}

	public SectionCompiler.Results compile(SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack buffers) {
		return compiler.compile(sectionPos, region, vertexSorting, buffers);
	}

	public SectionCompiler getCompiler() {
		return compiler;
	}
}
