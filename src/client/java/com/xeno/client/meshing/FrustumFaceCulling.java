package com.xeno.client.meshing;

public final class FrustumFaceCulling {
    private static final float REMESH_BENEFIT_THRESHOLD = 0.5f;
    private static final float MIN_ROTATION_DELTA_DEGREES = 5.0f;
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
        float totalDelta = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);

        if (totalDelta < MIN_ROTATION_DELTA_DEGREES) return false;

        float dirX = (float) (Math.cos(Math.toRadians(currentPitch)) * Math.sin(Math.toRadians(currentYaw)));
        float dirY = (float) Math.sin(Math.toRadians(currentPitch));
        float dirZ = (float) (Math.cos(Math.toRadians(currentPitch)) * Math.cos(Math.toRadians(currentYaw)));

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
