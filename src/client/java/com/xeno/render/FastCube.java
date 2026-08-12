package com.xeno.render;

import org.joml.Vector3f;

public interface FastCube {
    Vector3f[] getCorners();
    int[] getVertexIndices();
}
