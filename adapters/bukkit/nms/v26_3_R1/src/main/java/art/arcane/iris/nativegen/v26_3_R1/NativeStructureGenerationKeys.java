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

package art.arcane.iris.nativegen.v26_3_R1;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class NativeStructureGenerationKeys {
    private static final Method FREQUENCY_METHOD = resolveFrequencyMethod();

    private NativeStructureGenerationKeys() {
    }

    public static Set<String> reachable(ServerLevel level, Set<String> possibleBiomeKeys) {
        ServerLevel activeLevel = Objects.requireNonNull(level, "Native structure reachability requires a level");
        Set<String> possibleBiomes = Objects.requireNonNull(
                possibleBiomeKeys, "Native structure reachability requires possible biome keys");
        if (!activeLevel.getServer().getWorldGenSettings().options().generateStructures()) {
            return Collections.emptySet();
        }

        Set<String> keys = new LinkedHashSet<>();
        List<Holder<StructureSet>> structureSets =
                activeLevel.getChunkSource().getGeneratorState().possibleStructureSets();
        for (Holder<StructureSet> structureSetHolder : structureSets) {
            StructureSet structureSet = structureSetHolder.value();
            if (!isEnabledPlacement(structureSet.placement())) {
                continue;
            }
            for (StructureSet.StructureSelectionEntry entry : structureSet.structures()) {
                if (entry.weight() <= 0 || !hasPossibleBiome(entry.structure().value(), possibleBiomes)) {
                    continue;
                }
                Optional<ResourceKey<Structure>> key = entry.structure().unwrapKey();
                key.ifPresent(resourceKey -> keys.add(resourceKey.identifier().toString()));
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    static boolean hasPossibleBiome(Structure structure, Set<String> possibleBiomeKeys) {
        for (Holder<Biome> holder : structure.biomes()) {
            Optional<ResourceKey<Biome>> key = holder.unwrapKey();
            if (key.isPresent() && possibleBiomeKeys.contains(key.get().identifier().toString())) {
                return true;
            }
        }
        return false;
    }

    static boolean isEnabledPlacement(StructurePlacement placement) {
        return placementFrequency(placement) > 0.0F;
    }

    private static float placementFrequency(StructurePlacement placement) {
        StructurePlacement activePlacement = Objects.requireNonNull(
                placement, "Native structure placement frequency requires a placement");
        if (!(activePlacement instanceof AbstractSpreadingStructurePlacement)) {
            return 1F;
        }
        try {
            return ((Float) FREQUENCY_METHOD.invoke(activePlacement)).floatValue();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "Unable to read native structure placement frequency from "
                            + activePlacement.getClass().getName(), error);
        }
    }

    private static Method resolveFrequencyMethod() {
        Method frequencyMethod = null;
        for (Method method : AbstractSpreadingStructurePlacement.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0
                    || method.getReturnType() != float.class) {
                continue;
            }
            if (frequencyMethod != null) {
                throw new IllegalStateException(
                        "Native structure placement exposes multiple zero-argument float accessors");
            }
            frequencyMethod = method;
        }
        if (frequencyMethod == null || !frequencyMethod.trySetAccessible()) {
            throw new IllegalStateException(
                    "Native structure placement frequency accessor is unavailable");
        }
        return frequencyMethod;
    }
}
