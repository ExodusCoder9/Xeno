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

package com.xeno.client.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class XenoConfig {
    private static final Path CONFIG_PATH = Path.of("config", "xeno.properties");

    private static volatile boolean glNoErrorContext = true;
    private static volatile boolean animationCullingEnabled = true;

    static {
        load();
    }

    private XenoConfig() {
    }

    public static boolean isGlNoErrorContext() {
        return glNoErrorContext;
    }

    public static void setGlNoErrorContext(boolean enabled) {
        glNoErrorContext = enabled;
        save();
    }

    public static boolean isAnimationCullingEnabled() {
        return animationCullingEnabled;
    }

    public static void setAnimationCullingEnabled(boolean enabled) {
        animationCullingEnabled = enabled;
        save();
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            glNoErrorContext = Boolean.parseBoolean(props.getProperty("glNoErrorContext", "true"));
            animationCullingEnabled = Boolean.parseBoolean(props.getProperty("animationCullingEnabled", "true"));
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("glNoErrorContext", Boolean.toString(glNoErrorContext));
        props.setProperty("animationCullingEnabled", Boolean.toString(animationCullingEnabled));
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "Xeno configuration");
            }
        } catch (IOException ignored) {
        }
    }
}
