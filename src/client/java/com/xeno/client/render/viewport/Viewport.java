package com.xeno.client.render.viewport;

import com.xeno.client.render.chunk.culling.Frustum;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class Viewport {
	private final Frustum frustum = new Frustum();
	private Matrix4f projectionMatrix = new Matrix4f();
	private Matrix4f viewMatrix = new Matrix4f();

	public void build(Matrix4fc projection, Matrix4fc view) {
		this.projectionMatrix.set(projection);
		this.viewMatrix.set(view);
		Matrix4f pv = new Matrix4f(projection);
		pv.mul(view);
		frustum.extract(pv);
	}

	public Frustum getFrustum() { return frustum; }
	public Matrix4f getProjectionMatrix() { return projectionMatrix; }
	public Matrix4f getViewMatrix() { return viewMatrix; }
}
