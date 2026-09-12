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

package art.arcane.iris.generation.terrain;

import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.volmlib.util.json.JSONObject;

import java.util.Objects;

public record IrisDimensionRuntimeContract(
        String typeKey,
        int minHeight,
        int height,
        int logicalHeight
) {
    public IrisDimensionRuntimeContract {
        Objects.requireNonNull(typeKey, "typeKey");
        if (typeKey.isBlank()) {
            throw new IllegalArgumentException("Dimension type key cannot be blank");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Dimension height must be positive");
        }
        if (logicalHeight < 0 || logicalHeight > height) {
            throw new IllegalArgumentException("Logical height must be inside the dimension height");
        }
    }

    public static IrisDimensionRuntimeContract expected(IrisDimension dimension, String namespace) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(namespace, "namespace");
        return new IrisDimensionRuntimeContract(
                namespace + ":" + dimension.getDimensionTypeKey(),
                dimension.getMinHeight(),
                dimension.getMaxHeight() - dimension.getMinHeight(),
                dimension.getLogicalHeight());
    }

    public static void requireHotloadCompatible(
            String runtimeName,
            IrisDimension active,
            IrisDimension replacement,
            String namespace
    ) {
        IrisDimensionRuntimeContract actual = expected(active, namespace);
        IrisDimensionRuntimeContract proposed = expected(replacement, namespace);
        proposed.requireExact(runtimeName, actual);
        requireGeneratedTypeCompatible(runtimeName, active, replacement);
    }

    public int maxHeight() {
        return Math.addExact(minHeight, height);
    }

    public void requireExact(String runtimeName, IrisDimensionRuntimeContract actual) {
        Objects.requireNonNull(actual, "actual");
        if (equals(actual)) {
            return;
        }
        throw mismatch(runtimeName, actual.typeKey(), actual.minHeight(), actual.height(), actual.logicalHeight());
    }

    public void requireHeight(String runtimeName, int actualMinHeight, int actualHeight) {
        if (minHeight == actualMinHeight && height == actualHeight) {
            return;
        }
        throw mismatch(runtimeName, "unknown", actualMinHeight, actualHeight, -1);
    }

    private IrisDimensionContractException mismatch(
            String runtimeName,
            String actualTypeKey,
            int actualMinHeight,
            int actualHeight,
            int actualLogicalHeight
    ) {
        String expectedRange = minHeight + ".." + maxHeight();
        String actualRange = actualMinHeight + ".." + Math.addExact(actualMinHeight, actualHeight);
        String actualLogical = actualLogicalHeight < 0 ? "unknown" : Integer.toString(actualLogicalHeight);
        return new IrisDimensionContractException(runtimeName + " requires Iris dimension type " + typeKey
                + " with range " + expectedRange + " and logical height " + logicalHeight
                + ", but the loaded runtime uses " + actualTypeKey + " with range " + actualRange
                + " and logical height " + actualLogical
                + ". Generation was refused before any chunk writes. Restart after installing the exact Iris dimension type; terrain clipping is not allowed.");
    }

    private static void requireGeneratedTypeCompatible(
            String runtimeName,
            IrisDimension active,
            IrisDimension replacement
    ) {
        if (!Objects.equals(active.getEnvironment(), replacement.getEnvironment())) {
            throw hotloadMismatch(runtimeName, active, replacement);
        }

        JSONObject activeType = effectiveDimensionType(active);
        JSONObject replacementType = effectiveDimensionType(replacement);
        if (!replacementType.similar(activeType)) {
            throw hotloadMismatch(runtimeName, active, replacement);
        }
    }

    private static JSONObject effectiveDimensionType(IrisDimension dimension) {
        return new JSONObject(dimension.getDimensionType().toJson(DataVersion.getLatest().get()));
    }

    private static IrisDimensionContractException hotloadMismatch(
            String runtimeName,
            IrisDimension active,
            IrisDimension replacement
    ) {
        return new IrisDimensionContractException(runtimeName
                + " cannot hotload a changed dimension environment or generated dimension type. The active environment is "
                + active.getEnvironment() + " and the replacement environment is " + replacement.getEnvironment()
                + ". Close and reopen Studio after changing environment, dimensionOptions, or fullbright values that alter the generated type.");
    }
}
