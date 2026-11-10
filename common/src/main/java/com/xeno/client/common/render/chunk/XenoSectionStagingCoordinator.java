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
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.common.render.chunk;

public final class XenoSectionStagingCoordinator {
    private static volatile XenoStagingAccess ACTIVE_DISPATCHER;

    private XenoSectionStagingCoordinator() {
    }

    public static void setActiveDispatcher(XenoStagingAccess dispatcher) {
        ACTIVE_DISPATCHER = dispatcher;
    }

    public static void awaitSpace() {
        XenoStagingAccess dispatcher = ACTIVE_DISPATCHER;
        if (dispatcher != null) {
            dispatcher.xeno$awaitSpace();
        } else {
            Thread.onSpinWait();
        }
    }

    public static void signalSpaceAvailable() {
        XenoStagingAccess dispatcher = ACTIVE_DISPATCHER;
        if (dispatcher != null) {
            dispatcher.xeno$signalSpaceAvailable();
        }
    }
}
