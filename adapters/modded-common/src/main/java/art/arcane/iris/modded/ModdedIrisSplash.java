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

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.diagnostics.splash.IrisSplashComposer;
import art.arcane.iris.diagnostics.splash.IrisSplashRenderer;
import art.arcane.iris.spi.IrisLogging;

import java.io.File;

public final class ModdedIrisSplash {

    private ModdedIrisSplash() {
    }

    public static void print(ModdedLoader loader) {
        printPacks(loader);
        if (isLogoEnabled()) {
            printLogo(loader);
        }
    }

    private static void printPacks(ModdedLoader loader) {
        File packFolder = IrisPlatforms.get().packsFolderNoCreate();
        for (String line : IrisSplashComposer.composePackLines(packFolder, IrisLogging::reportError)) {
            IrisLogging.info(line);
        }
    }

    private static void printLogo(ModdedLoader loader) {
        String serverLine = loader.platformName() + " / Minecraft " + loader.minecraftVersion();
        String[] splash = IrisSplashRenderer.renderPlain();
        String[] info = IrisSplashComposer.composeInfo(loader.modVersion(), serverLine, IrisSplashComposer.InfoStyle.PLAIN);
        IrisLogging.info(IrisSplashComposer.compose(splash, info));
    }

    private static boolean isLogoEnabled() {
        try {
            return IrisSettings.get().getGeneral().isSplashLogoStartup();
        } catch (Throwable error) {
            IrisLogging.warn("Iris splash setting could not be read: " + error.getClass().getSimpleName());
            return true;
        }
    }
}
