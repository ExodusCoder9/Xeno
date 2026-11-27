package com.xeno.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.xeno.client.common.config.XenoConfig;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GlBackend.class, remap = false)
public abstract class XenoGlBackendMixin {
    @WrapOperation(
        method = "createWindow",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/sdl/SDLVideo;SDL_CreateWindow(Ljava/lang/CharSequence;IIJ)J"
        )
    )
    private long xeno$enableNoErrorContext(CharSequence title, int w, int h, long flags, Operation<Long> original) {
        if (!XenoConfig.isGlNoErrorContext() || "false".equalsIgnoreCase(System.getProperty("xeno.glNoError"))) {
            return original.call(title, w, h, flags);
        }
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_NO_ERROR, 1);
        long handle = original.call(title, w, h, flags);
        if (handle == 0L) {
            SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_NO_ERROR, 0);
            handle = original.call(title, w, h, flags);
        }
        return handle;
    }
}
