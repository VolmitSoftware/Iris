/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded;

import art.arcane.iris.modded.service.ModdedService;
import art.arcane.iris.modded.service.ModdedTickableService;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModdedServiceManager {

    private final Map<Class<? extends ModdedService>, ModdedService> services = new LinkedHashMap<>();
    private boolean enabled = false;

    public synchronized <T extends ModdedService> T register(Class<T> type, T service) {
        if (services.containsKey(type)) {
            return type.cast(services.get(type));
        }
        services.put(type, service);
        if (enabled) {
            try {
                service.onEnable();
            } catch (Throwable failure) {
                services.remove(type);
                disableAfterFailure(service, failure);
                throw serviceFailure(service, failure);
            }
        }
        return service;
    }

    public synchronized <T extends ModdedService> T service(Class<T> type) {
        ModdedService service = services.get(type);
        return service == null ? null : type.cast(service);
    }

    public synchronized void enableAll() {
        if (enabled) {
            return;
        }
        enabled = true;
        ModdedService[] ordered = services.values().toArray(new ModdedService[0]);
        for (int i = 0; i < ordered.length; i++) {
            ModdedService service = ordered[i];
            try {
                service.onEnable();
            } catch (Throwable failure) {
                enabled = false;
                for (int rollbackIndex = i; rollbackIndex >= 0; rollbackIndex--) {
                    disableAfterFailure(ordered[rollbackIndex], failure);
                }
                throw serviceFailure(service, failure);
            }
        }
    }

    public synchronized void tick(NativeModdedServer server) {
        if (!enabled) {
            return;
        }
        for (ModdedService service : services.values()) {
            if (service instanceof ModdedTickableService tickable) {
                tickService(tickable, server);
            }
        }
    }

    /**
     * Shutdown path: every service gets a disable attempt and nothing is rethrown. A throw here would abort the
     * loader's remaining stop handlers, so failures are logged and the manager still ends up disabled.
     */
    public synchronized void disableAll() {
        if (!enabled) {
            return;
        }
        Throwable failure = null;
        int failed = 0;
        ModdedService[] ordered = services.values().toArray(new ModdedService[0]);
        for (int i = ordered.length - 1; i >= 0; i--) {
            ModdedService service = ordered[i];
            try {
                service.onDisable();
            } catch (Throwable serviceFailure) {
                failed++;
                ModdedIrisLog.error("Iris service onDisable failed for {}", service.getClass().getName(), serviceFailure);
                if (failure == null) {
                    failure = serviceFailure;
                } else if (serviceFailure != failure) {
                    failure.addSuppressed(serviceFailure);
                }
            }
        }
        enabled = false;
        if (failure != null) {
            ModdedIrisLog.error("Iris disabled all services with {} failure(s)", failed, failure);
        }
    }

    synchronized void rollback(Throwable failure) {
        if (enabled) {
            enabled = false;
            ModdedService[] ordered = services.values().toArray(new ModdedService[0]);
            for (int i = ordered.length - 1; i >= 0; i--) {
                disableAfterFailure(ordered[i], failure);
            }
        }
        services.clear();
    }

    private void tickService(ModdedTickableService service, NativeModdedServer server) {
        try {
            service.onServerTick(server);
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris service tick failed for {}", service.getClass().getName(), error);
        }
    }

    private void disableAfterFailure(ModdedService service, Throwable failure) {
        try {
            service.onDisable();
        } catch (Throwable cleanupError) {
            if (cleanupError != failure) {
                failure.addSuppressed(cleanupError);
            }
            ModdedIrisLog.error("Iris service rollback failed for {}", service.getClass().getName(), cleanupError);
        }
    }

    private RuntimeException serviceFailure(ModdedService service, Throwable failure) {
        ModdedIrisLog.error("Iris service onEnable failed for {}", service.getClass().getName(), failure);
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (failure instanceof Error fatalError) {
            throw fatalError;
        }
        return new IllegalStateException("Iris service failed to enable: " + service.getClass().getName(), failure);
    }

}
