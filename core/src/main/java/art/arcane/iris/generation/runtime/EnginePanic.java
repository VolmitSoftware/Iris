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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.spi.IrisLogging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class EnginePanic {
    private static final Diagnostics GLOBAL = scoped("global");

    private EnginePanic() {
    }

    public static Diagnostics scoped(String scope) {
        return new Diagnostics(scope);
    }

    public static void add(String key, String value) {
        GLOBAL.add(key, value);
    }

    public static void saveLast() {
        GLOBAL.saveLast();
    }

    public static void lastPanic() {
        GLOBAL.lastPanic();
    }

    public static void panic() {
        GLOBAL.panic();
    }

    public static final class Diagnostics {
        private final String scope;
        private final ThreadLocal<LinkedHashMap<String, String>> current = ThreadLocal.withInitial(LinkedHashMap::new);
        private final AtomicReference<Map<String, String>> last = new AtomicReference<>(Map.of());

        private Diagnostics(String scope) {
            this.scope = scope == null || scope.isBlank() ? "unknown" : scope;
        }

        public void add(String key, String value) {
            current.get().put(String.valueOf(key), String.valueOf(value));
        }

        public void saveLast() {
            last.set(currentSnapshot());
            current.remove();
        }

        public void lastPanic() {
            log("Last Panic", last.get());
        }

        public void panic() {
            Map<String, String> currentSnapshot = currentSnapshot();
            lastPanic();
            log("Engine Panic", currentSnapshot);
            current.remove();
        }

        Map<String, String> currentSnapshot() {
            return immutableSnapshot(current.get());
        }

        Map<String, String> lastSnapshot() {
            return last.get();
        }

        private void log(String prefix, Map<String, String> snapshot) {
            for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                IrisLogging.error(prefix + " [" + scope + "] " + entry.getKey() + ": " + entry.getValue());
            }
        }

        private static Map<String, String> immutableSnapshot(Map<String, String> values) {
            if (values.isEmpty()) {
                return Map.of();
            }

            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
}
