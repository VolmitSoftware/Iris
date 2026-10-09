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

package art.arcane.iris.util.common.scheduling;

import art.arcane.iris.spi.IrisPlatforms;

import java.util.List;

/** Runs console commands for the pack: once or for ever, after a delay, with {@code {x}}, {@code {y}} and {@code {z}} filled in. */
public final class ConsoleCommands {
    private ConsoleCommands() {
    }

    /**
     * @param commands    the commands, with an optional leading slash
     * @param delay       server ticks before the commands run
     * @param repeat      whether to run them again every {@code repeatDelay} ticks until the server stops
     * @param repeatDelay server ticks between repeats
     */
    public static void run(List<String> commands, long delay, boolean repeat, long repeatDelay, int x, int y, int z) {
        for (String command : commands) {
            String filled = (command.startsWith("/") ? command.replaceFirst("/", "") : command)
                    .replaceAll("\\Q{x}\\E", String.valueOf(x))
                    .replaceAll("\\Q{y}\\E", String.valueOf(y))
                    .replaceAll("\\Q{z}\\E", String.valueOf(z));
            int safeDelay = (int) Math.max(0, Math.min(Integer.MAX_VALUE, delay));
            if (repeat) {
                int safeRepeatDelay = (int) Math.max(1, Math.min(Integer.MAX_VALUE, repeatDelay));
                J.s(() -> J.sr(() -> IrisPlatforms.get().dispatchConsoleCommand(filled), safeRepeatDelay), safeDelay);
            } else {
                J.s(() -> IrisPlatforms.get().dispatchConsoleCommand(filled), safeDelay);
            }
        }
    }
}
