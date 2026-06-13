/*
 * Original Codebase: Copyright XCollateral (VulkanMod)
 * Refactored Codebase: Copyright ExodusCoder9 (Xeno)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Refactored, Renamed and Optimized by ExodusCoder9.
 */
package com.xeno.mixin.profiling;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.client.telemetry.events.GameLoadTimesEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GameLoadTimesEvent.class)
public class GameLoadTimeEventM {
   @Shadow
   @Final
   private Map<TelemetryProperty<GameLoadTimesEvent.Measurement>, Stopwatch> measurements;
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   private OptionalLong bootstrapTime;

   @Overwrite
   public void send(TelemetryEventSender telemetryEventSender) {
      Map<TelemetryProperty, GameLoadTimesEvent.Measurement> measurements = new Reference2ReferenceOpenHashMap();
      synchronized (this) {
         this.measurements
            .forEach(
               (telemetryProperty, stopwatch) -> {
                  if (!stopwatch.isRunning()) {
                     long l = stopwatch.elapsed(TimeUnit.MILLISECONDS);
                     measurements.put(telemetryProperty, new GameLoadTimesEvent.Measurement((int)l));
                  } else {
                     LOGGER.warn(
                        "Measurement {} was discarded since it was still ongoing when the event {} was sent.",
                        telemetryProperty.id(),
                        TelemetryEventType.GAME_LOAD_TIMES.id()
                     );
                  }
               }
            );
         this.bootstrapTime.ifPresent(l -> measurements.put(TelemetryProperty.LOAD_TIME_BOOTSTRAP_MS, new GameLoadTimesEvent.Measurement((int)l)));
         this.measurements.clear();
      }

      StringBuilder stringBuilder = new StringBuilder("\n");

      for (TelemetryProperty property : measurements.keySet()) {
         GameLoadTimesEvent.Measurement measurement = measurements.get(property);
         stringBuilder.append("%s: %sms\n".formatted(property.id(), measurement.millis()));
      }

      LOGGER.info(stringBuilder.toString());
   }
}
