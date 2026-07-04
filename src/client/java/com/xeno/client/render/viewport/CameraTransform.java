package com.xeno.client.render.viewport;

import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;

public class CameraTransform {
	private Vec3 position = Vec3.ZERO;
	private Matrix4f viewMatrix = new Matrix4f();
	private Matrix4f projectionMatrix = new Matrix4f();

	public void set(Vec3 pos, Matrix4f view, Matrix4f projection) {
		this.position = pos;
		this.viewMatrix.set(view);
		this.projectionMatrix.set(projection);
	}

	public Vec3 getPosition() { return position; }
	public Matrix4f getViewMatrix() { return viewMatrix; }
	public Matrix4f getProjectionMatrix() { return projectionMatrix; }
}
