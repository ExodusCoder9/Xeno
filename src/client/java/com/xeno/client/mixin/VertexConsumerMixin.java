package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;

//  Quad interception removed. Dynamic face dropping during static mesh compilation
// was the primary cause of buffer tearing and missing geometry upon camera rotation.
@Mixin(VertexConsumer.class)
public interface VertexConsumerMixin {
}