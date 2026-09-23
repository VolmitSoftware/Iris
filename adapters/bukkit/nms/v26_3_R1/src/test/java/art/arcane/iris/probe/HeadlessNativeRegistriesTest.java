package art.arcane.iris.probe;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.Test;


import static org.junit.Assert.assertTrue;

public final class HeadlessNativeRegistriesTest {
    @Test
    public void nativeStructureBiomeTagsUseTheLoadedBiomeHolders() throws Exception {
        RegistryAccess registries = HeadlessNativeTestRegistries.get();
        Structure swampHut = registries.lookupOrThrow(Registries.STRUCTURE)
                .getValue(Identifier.parse("minecraft:swamp_hut"));
        Holder<Biome> swamp = registries.lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:swamp")));
        assertTrue(swampHut.biomes().contains(swamp));
        Holder<Biome> custom = registries.lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("iris:coherent_swamp")));
        assertTrue(swampHut.biomes().contains(custom));
    }
}
