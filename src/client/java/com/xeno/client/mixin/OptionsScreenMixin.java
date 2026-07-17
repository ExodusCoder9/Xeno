package com.xeno.client.mixin;

import com.xeno.client.gui.XenoVideoSettingsScreen;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin {

    @Unique
    private static final Component XENO_VIDEO = Component.translatable("options.video");

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/options/OptionsScreen;openScreenButton(Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;)Lnet/minecraft/client/gui/components/Button;"
            )
    )
    private Button xeno$replaceVideoSettings(OptionsScreen instance, Component message, Supplier<Screen> screenToScreen) {
        if (XENO_VIDEO.equals(message)) {
            Minecraft mc = Minecraft.getInstance();
            return Button.builder(message, _ -> mc.gui.setScreen(
                    new XenoVideoSettingsScreen((Screen) (Object) this, mc.options)
            )).build();
        }
        return Button.builder(message, _ -> Minecraft.getInstance().gui.setScreen(screenToScreen.get())).build();
    }
}
