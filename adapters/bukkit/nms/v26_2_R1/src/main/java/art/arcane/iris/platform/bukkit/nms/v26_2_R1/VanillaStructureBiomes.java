package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.nativegen.NativeStructureGenerationKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class VanillaStructureBiomes {
    private VanillaStructureBiomes() {
    }

    static Set<String> possibleBiomeKeys(BiomeSource source) {
        if (source == null) {
            throw new IllegalStateException("Minecraft chunk generator has no biome source");
        }
        Set<String> keys = new LinkedHashSet<>();
        Set<Holder<Biome>> possibleBiomes = source instanceof CustomBiomeSource customBiomeSource
                ? customBiomeSource.possibleStructureBiomes()
                : source.possibleBiomes();
        for (Holder<Biome> holder : possibleBiomes) {
            Optional<ResourceKey<Biome>> key = holder.unwrapKey();
            if (key.isPresent()) {
                keys.add(key.get().identifier().toString());
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("Minecraft biome source exposes no registered possible biomes");
        }
        return keys;
    }

    static Set<String> structureBiomeKeys(RegistryAccess access, String structureKey) {
        if (access == null) {
            throw new IllegalStateException("Minecraft registry access is unavailable");
        }
        if (structureKey == null || structureKey.isBlank()) {
            throw new IllegalArgumentException("Registered structure key must not be blank");
        }
        Set<String> keys = new LinkedHashSet<>();
        Registry<Structure> registry = access.lookupOrThrow(Registries.STRUCTURE);
        Structure structure = registry.getValue(Identifier.parse(structureKey));
        if (structure == null) {
            throw new IllegalArgumentException("Registered structure does not exist: " + structureKey);
        }
        boolean hasFilterEntries = false;
        for (Holder<Biome> holder : structure.biomes()) {
            hasFilterEntries = true;
            Optional<ResourceKey<Biome>> key = holder.unwrapKey();
            if (key.isPresent()) {
                keys.add(key.get().identifier().toString());
            }
        }
        // An empty biome filter is legal datapack content (opt-in structures whose tags only
        // reference absent modded biomes); the structure is unreachable, not an error state.
        if (keys.isEmpty() && hasFilterEntries) {
            throw new IllegalStateException("Registered structure '" + structureKey
                    + "' has biome filter entries but none resolve to registered biome keys");
        }
        return keys;
    }

    static Set<String> reachableStructureKeys(ServerLevel level, BiomeSource source) {
        if (level == null) {
            throw new IllegalStateException("Minecraft server level is unavailable");
        }
        Set<String> possible = possibleBiomeKeys(source);
        return NativeStructureGenerationKeys.reachable(level, possible);
    }
}
