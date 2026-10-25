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

package art.arcane.iris.studio.map;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Asks the operating system whether its user prefers dark or light apps, through the same settings desktop apps
 * follow: the freedesktop portal or GNOME's setting on Linux, the apps theme on Windows, the appearance on macOS.
 */
final class SystemAppearance {
    private static final long COMMAND_TIMEOUT_MILLIS = 2000L;
    private static final String PORTAL_DARK = "uint32 1";
    private static final String PORTAL_LIGHT = "uint32 2";

    private SystemAppearance() {
    }

    /**
     * Empty when the system has no preference or could not be asked.
     */
    static Optional<Boolean> prefersDark() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return windowsPrefersDark();
        }
        if (os.contains("mac")) {
            return macPrefersDark();
        }
        return linuxPrefersDark();
    }

    private static Optional<Boolean> linuxPrefersDark() {
        Optional<String> portal = output(List.of("gdbus", "call", "--session", "--dest", "org.freedesktop.portal.Desktop",
                "--object-path", "/org/freedesktop/portal/desktop", "--method", "org.freedesktop.portal.Settings.ReadOne",
                "org.freedesktop.appearance", "color-scheme"));
        if (portal.filter(answer -> answer.contains(PORTAL_DARK)).isPresent()) {
            return Optional.of(true);
        }
        if (portal.filter(answer -> answer.contains(PORTAL_LIGHT)).isPresent()) {
            return Optional.of(false);
        }
        Optional<String> gnome = output(List.of("gsettings", "get", "org.gnome.desktop.interface", "color-scheme"));
        if (gnome.filter(scheme -> scheme.contains("prefer-dark")).isPresent()) {
            return Optional.of(true);
        }
        if (gnome.filter(scheme -> scheme.contains("prefer-light")).isPresent()) {
            return Optional.of(false);
        }
        return output(List.of("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"))
                .map(theme -> theme.toLowerCase(Locale.ROOT).contains("dark"));
    }

    private static Optional<Boolean> windowsPrefersDark() {
        return output(List.of("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"))
                .filter(answer -> answer.contains("AppsUseLightTheme"))
                .map(answer -> answer.trim().endsWith("0x0"));
    }

    // The setting only exists while macOS is dark, so a failed read means light.
    private static Optional<Boolean> macPrefersDark() {
        return Optional.of(output(List.of("defaults", "read", "-g", "AppleInterfaceStyle"))
                .filter(style -> style.trim().equalsIgnoreCase("dark"))
                .isPresent());
    }

    private static Optional<String> output(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            try (InputStream stream = process.getInputStream()) {
                return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException missingCommand) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
