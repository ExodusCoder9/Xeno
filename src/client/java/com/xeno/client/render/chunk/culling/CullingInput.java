package com.xeno.client.render.chunk.culling;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class CullingInput {
	private final Vec3 cameraPos;
	private final Matrix4f projection;
	private final Matrix4f view;
	private final float fov;
	private final long frameIndex;

	public CullingInput(Vec3 cameraPos, Matrix4f projection, Matrix4f view, float fov, long frameIndex) {
		this.cameraPos = cameraPos;
		this.projection = projection;
		this.view = view;
		this.fov = fov;
		this.frameIndex = frameIndex;
	}

	public Vec3 getCameraPos() { return cameraPos; }
	public Matrix4f getProjectionMatrix() { return projection; }
	public Matrix4f getViewMatrix() { return view; }
	public float getFov() { return fov; }
	public long getFrameIndex() { return frameIndex; }
}
