/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.data.cache;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.volmlib.util.cache.AtomicCacheFailureHandler;

/**
 * Sends the failures of {@link AtomicCache} suppliers to Iris's logging and error reporting. Bound once at startup;
 * it needs no platform of its own because {@link IrisLogging} falls back to the process streams when none is bound.
 */
public final class AtomicCacheFailureReporting implements AtomicCacheFailureHandler {
    private AtomicCacheFailureReporting() {
    }

    public static void bind() {
        AtomicCache.setFailureHandler(new AtomicCacheFailureReporting());
    }

    @Override
    public void supplierFailed(Throwable failure) {
        IrisLogging.reportError("Atomic cache supplier failed.", failure);
    }

    @Override
    public void optionalSupplierFailed(Throwable failure) {
        // aquire retries on every call, so a supplier that stays broken is reached per sample.
        // The first statement of it carries the stack; the rest are traces of the same cause.
        if (IrisLogging.warnOnce("atomic-cache:" + failure.getClass().getName() + ":" + failure.getMessage(),
                "Atomic cache supplier failed: %s: %s", failure.getClass().getSimpleName(), failure.getMessage())) {
            IrisLogging.reportError(failure);
        }
    }
}
