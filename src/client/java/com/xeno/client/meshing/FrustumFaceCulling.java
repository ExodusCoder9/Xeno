package com.xeno.client.meshing;

public final class FrustumFaceCulling {
    private static final float REMESH_BENEFIT_THRESHOLD = 0.5f;
    private static final float MIN_ROTATION_DELTA_DEGREES = 5.0f;
    private static final float MIN_ROTATION_DELTA_DEGREES_SQR = MIN_ROTATION_DELTA_DEGREES * MIN_ROTATION_DELTA_DEGREES;
    private static final int ESTIMATED_REMESH_COST = 2000;

    private float previousYaw;
    private float previousPitch;
    private boolean initialized;

    @SuppressWarnings("unused")
    public boolean shouldReMeshWithFaceCulling(
            SectionFaceData faceData,
            int sectionIndex,
            float currentYaw,
            float currentPitch
    ) {
        if (!initialized) {
            previousYaw = currentYaw;
            previousPitch = currentPitch;
            initialized = true;
            return false;
        }

        float deltaYaw = normalizeAngle(currentYaw - previousYaw);
        float deltaPitch = normalizeAngle(currentPitch - previousPitch);
        float sqrDelta = deltaYaw * deltaYaw + deltaPitch * deltaPitch;

        if (sqrDelta < MIN_ROTATION_DELTA_DEGREES_SQR) return false;

        float pitchRad = (float) Math.toRadians(currentPitch);
        float yawRad = (float) Math.toRadians(currentYaw);
        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);

        float dirX = cosPitch * sinYaw;
        float dirY = sinPitch;
        float dirZ = cosPitch * cosYaw;

        int facesAway = faceData.calculateFacesAway(sectionIndex, dirX, dirY, dirZ);
        int vertexSavings = facesAway * SectionFaceData.VERTICES_PER_QUAD;
        int currentVertices = faceData.getVertexCount(sectionIndex);
        int reMeshCost = ESTIMATED_REMESH_COST + currentVertices;

        float benefitRatio = (float) vertexSavings / reMeshCost;

        if (benefitRatio > REMESH_BENEFIT_THRESHOLD) {
            previousYaw = currentYaw;
            previousPitch = currentPitch;
            return true;
        }

        return false;
    }

    @SuppressWarnings("unused")
    public void reset() {
        initialized = false;
    }

    private static float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle > 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }
}
