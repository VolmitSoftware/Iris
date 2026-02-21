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

package art.arcane.iris.engine.platform;

public final class ChunkReplacementOptions {
    private final String runId;
    private final boolean fullMode;
    private final boolean diagnostics;

    private ChunkReplacementOptions(String runId, boolean fullMode, boolean diagnostics) {
        this.runId = runId == null ? "unknown" : runId;
        this.fullMode = fullMode;
        this.diagnostics = diagnostics;
    }

    public static ChunkReplacementOptions terrain(String runId, boolean diagnostics) {
        return new ChunkReplacementOptions(runId, false, diagnostics);
    }

    public static ChunkReplacementOptions full(String runId, boolean diagnostics) {
        return new ChunkReplacementOptions(runId, true, diagnostics);
    }

    public String runId() {
        return runId;
    }

    public boolean isFullMode() {
        return fullMode;
    }

    public boolean diagnostics() {
        return diagnostics;
    }
}
