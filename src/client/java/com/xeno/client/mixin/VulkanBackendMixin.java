package com.xeno.client.mixin;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
import java.util.Set;

@Mixin(value = VulkanBackend.class, remap = false)
public class VulkanBackendMixin {
    @Inject(
        method = "createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;",
        at = @At("HEAD")
    )
    private static void inject_createDevice(
        Collection<String> extensions,
        VulkanPhysicalDevice physicalDevice,
        Set<VulkanFeature> features,
        CallbackInfoReturnable<org.lwjgl.vulkan.VkDevice> cir
    ) {
        // Explicitly request drawIndirectFirstInstance Vulkan feature
        features.add(new VulkanFeature(
            VulkanBackend.VK10_FEATURES_STRUCT,
            "drawIndirectFirstInstance",
            org.lwjgl.vulkan.VkPhysicalDeviceFeatures.DRAWINDIRECTFIRSTINSTANCE
        ));
    }
}
