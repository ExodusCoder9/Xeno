package com.xeno.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class Inspect {
    public static void run() {
        System.out.println("=== VertexFormatElement ===");
        for (Constructor<?> c : VertexFormatElement.class.getDeclaredConstructors()) {
            System.out.println("Constructor: " + c);
        }
        for (Method m : VertexFormatElement.class.getDeclaredMethods()) {
            System.out.println("Method: " + m);
        }
        for (Field f : VertexFormatElement.class.getDeclaredFields()) {
            System.out.println("Field: " + f);
        }

        System.out.println("=== VertexFormat ===");
        for (Constructor<?> c : VertexFormat.class.getDeclaredConstructors()) {
            System.out.println("Constructor: " + c);
        }
        for (Method m : VertexFormat.class.getDeclaredMethods()) {
            System.out.println("Method: " + m);
        }
    }
}
