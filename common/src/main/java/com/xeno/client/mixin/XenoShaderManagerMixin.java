/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.mixin;

import com.xeno.client.common.render.chunk.XenoSectionCompiler;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShaderManager.class)
public abstract class XenoShaderManagerMixin {
    @Redirect(
        method = "loadConfigs(Lnet/minecraft/server/packs/resources/ResourceManager;)Lnet/minecraft/client/renderer/ShaderManager$Configs;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Lnet/minecraft/server/packs/resources/ResourceManager$Selector;)Ljava/util/Map;"
        )
    )
    private static Map<Identifier, Resource> xeno$injectXenoShaders(
        ResourceManager manager, String directory, ResourceManager.Selector selector
    ) {
        Map<Identifier, Resource> files = manager.listResources(directory, selector);
        Identifier terrainVsh = Identifier.fromNamespaceAndPath("xeno", "shaders/core/terrain.vsh");
        if (!files.containsKey(terrainVsh)) {
            Map<Identifier, Resource> mutableFiles = new HashMap<>(files);
            PackResources defaultPack = manager.getResource(Identifier.withDefaultNamespace("shaders/core/terrain.vsh"))
                .map(Resource::source)
                .orElse(null);
            mutableFiles.put(terrainVsh, new Resource(defaultPack, () -> {
                InputStream is = XenoSectionCompiler.class.getResourceAsStream("/assets/xeno/shaders/core/terrain.vsh");
                if (is == null) {
                    throw new FileNotFoundException("Could not find xeno terrain shader in classpath: /assets/xeno/shaders/core/terrain.vsh");
                }
                return is;
            }));
            return mutableFiles;
        }
        return files;
    }
}
