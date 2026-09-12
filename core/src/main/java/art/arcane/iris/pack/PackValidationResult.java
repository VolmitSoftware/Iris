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

package art.arcane.iris.pack;

import art.arcane.iris.pack.validation.CompatFinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PackValidationResult {
    private final String packName;
    private final List<String> blockingErrors;
    private final List<String> warnings;
    private final long validatedAtMillis;
    private final List<CompatFinding> compatFindings;
    private final String minecraftVersion;

    /** Result without version-content gating data; used where the pack was never loaded through the gate. */
    public PackValidationResult(String packName,
                                List<String> blockingErrors,
                                List<String> warnings,
                                long validatedAtMillis) {
        this(packName, blockingErrors, warnings, validatedAtMillis, List.of(), null);
    }

    public PackValidationResult(String packName,
                                List<String> blockingErrors,
                                List<String> warnings,
                                long validatedAtMillis,
                                List<CompatFinding> compatFindings,
                                String minecraftVersion) {
        this.packName = packName;
        this.blockingErrors = blockingErrors == null ? new ArrayList<>() : new ArrayList<>(blockingErrors);
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        this.validatedAtMillis = validatedAtMillis;
        this.compatFindings = compatFindings == null ? List.of() : List.copyOf(compatFindings);
        this.minecraftVersion = minecraftVersion;
    }

    public String getPackName() {
        return packName;
    }

    public boolean isLoadable() {
        return blockingErrors.isEmpty();
    }

    public List<String> getBlockingErrors() {
        return Collections.unmodifiableList(blockingErrors);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public long getValidatedAtMillis() {
        return validatedAtMillis;
    }

    /** Every version-content gating decision recorded while this pack was validated. */
    public List<CompatFinding> getCompatFindings() {
        return compatFindings;
    }

    /** The raw {@code IrisPlatform.minecraftVersion()} the gate ran against, or null when it did not run. */
    public String getMinecraftVersion() {
        return minecraftVersion;
    }
}
