package com.xeno.client.render.chunk.culling;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class Frustum {
	private final float[] planes = new float[6 * 4];

	public Frustum() {}

	public void extract(Matrix4fc projectionView) {
		float m00 = projectionView.m00();
		float m01 = projectionView.m01();
		float m02 = projectionView.m02();
		float m03 = projectionView.m03();
		float m10 = projectionView.m10();
		float m11 = projectionView.m11();
		float m12 = projectionView.m12();
		float m13 = projectionView.m13();
		float m20 = projectionView.m20();
		float m21 = projectionView.m21();
		float m22 = projectionView.m22();
		float m23 = projectionView.m23();
		float m30 = projectionView.m30();
		float m31 = projectionView.m31();
		float m32 = projectionView.m32();
		float m33 = projectionView.m33();

		// Left
		planes[0] = m03 + m00; planes[1] = m13 + m10;
		planes[2] = m23 + m20; planes[3] = m33 + m30;
		normalize(0);
		// Right
		planes[4] = m03 - m00; planes[5] = m13 - m10;
		planes[6] = m23 - m20; planes[7] = m33 - m30;
		normalize(1);
		// Bottom
		planes[8] = m03 + m01; planes[9] = m13 + m11;
		planes[10] = m23 + m21; planes[11] = m33 + m31;
		normalize(2);
		// Top
		planes[12] = m03 - m01; planes[13] = m13 - m11;
		planes[14] = m23 - m21; planes[15] = m33 - m31;
		normalize(3);
		// Near
		planes[16] = m03 + m02; planes[17] = m13 + m12;
		planes[18] = m23 + m22; planes[19] = m33 + m32;
		normalize(4);
		// Far
		planes[20] = m03 - m02; planes[21] = m13 - m12;
		planes[22] = m23 - m22; planes[23] = m33 - m32;
		normalize(5);
	}

	private void normalize(int planeIdx) {
		int i = planeIdx * 4;
		float invLen = 1.0f / (float) Math.sqrt(planes[i] * planes[i]
			+ planes[i+1] * planes[i+1]
			+ planes[i+2] * planes[i+2]);
		planes[i] *= invLen;
		planes[i+1] *= invLen;
		planes[i+2] *= invLen;
		planes[i+3] *= invLen;
	}

	public boolean testAabb(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		for (int i = 0; i < 6; i++) {
			int idx = i * 4;
			float px = planes[idx] >= 0 ? maxX : minX;
			float py = planes[idx+1] >= 0 ? maxY : minY;
			float pz = planes[idx+2] >= 0 ? maxZ : minZ;
			if (px * planes[idx] + py * planes[idx+1] + pz * planes[idx+2] + planes[idx+3] < 0) {
				return false;
			}
		}
		return true;
	}

	public boolean testSection(int sx, int sy, int sz) {
		return testAabb(sx * 16f, sy * 16f, sz * 16f,
			sx * 16f + 16, sy * 16f + 16, sz * 16f + 16);
	}

	public boolean testSectionExpanded(int sx, int sy, int sz, float expansion) {
		return testAabb(sx * 16f - expansion, sy * 16f - expansion, sz * 16f - expansion,
			sx * 16f + 16 + expansion, sy * 16f + 16 + expansion, sz * 16f + 16 + expansion);
	}

	public Frustum offset(float distance) {
		Frustum offset = new Frustum();
		System.arraycopy(this.planes, 0, offset.planes, 0, this.planes.length);
		for (int i = 0; i < 6; i++) {
			offset.planes[i*4 + 3] = planes[i*4 + 3] + distance;
		}
		return offset;
	}
}
