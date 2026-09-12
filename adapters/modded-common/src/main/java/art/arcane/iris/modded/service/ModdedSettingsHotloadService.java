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

package art.arcane.iris.modded.service;

import art.arcane.iris.configuration.SettingsHotloadWatch;
import art.arcane.iris.spi.IrisPlatforms;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class ModdedSettingsHotloadService implements ModdedTickableService {
    private SettingsHotloadWatch hotloadWatch;
    private long lastPollAtNanos;

    @Override
    public void onEnable() {
        hotloadWatch = new SettingsHotloadWatch(settingsFile());
        lastPollAtNanos = 0L;
    }

    @Override
    public void onDisable() {
        SettingsHotloadWatch active = hotloadWatch;
        hotloadWatch = null;
        if (active != null) {
            active.close();
        }
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        SettingsHotloadWatch active = hotloadWatch;
        if (active == null) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastPollAtNanos < TimeUnit.MILLISECONDS.toNanos(SettingsHotloadWatch.POLL_PERIOD_MILLIS)) {
            return;
        }
        lastPollAtNanos = now;
        active.checkConfigHotload();
    }

    private static File settingsFile() {
        return IrisPlatforms.get().dataFile("iris.json");
    }
}
