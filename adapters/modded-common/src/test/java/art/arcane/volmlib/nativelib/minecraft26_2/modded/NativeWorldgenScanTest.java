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

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenBiome;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenPlacedFeature;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeWorldgenScanTest {
    private static final String BIOME_JSON = "{\"has_precipitation\":true,\"temperature\":0.8,\"downfall\":0.4,"
            + "\"effects\":{\"water_color\":\"#3f76e4\"},\"carvers\":[],"
            + "\"features\":[[],[],[],[],[],[],[\"examplemod:ore_glimmer\"]]}";

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void scanReadsLiveFeatureStepsAndEncodesDefinitions() {
        NativeWorldgenSnapshot snapshot = NativeWorldgenScan.scan(server(), loader()).encode();

        WorldgenBiome biome = snapshot.biomes().getFirst();
        assertEquals("examplemod:glade", biome.key());
        assertEquals(0.8F, biome.temperature(), 0F);
        assertEquals(0.4F, biome.downfall(), 0.0001F);
        assertTrue(biome.hasPrecipitation());
        assertEquals(GenerationStep.Decoration.values().length, biome.featureSteps().size());
        assertEquals(List.of("examplemod:ore_glimmer"),
                biome.featureSteps().get(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()));
        assertNotNull(biome.definition());
        assertEquals(0.4F, biome.definition().getAsJsonObject().get("downfall").getAsFloat(), 0.0001F);
    }

    @Test
    public void unencodableFeaturesStayInTheCatalogWithoutJson() {
        NativeWorldgenSnapshot snapshot = NativeWorldgenScan.scan(server(), loader()).encode();

        WorldgenPlacedFeature feature = snapshot.placedFeatures().getFirst();
        assertEquals("examplemod:ore_glimmer", feature.key());
        assertEquals("", feature.feature());
        assertNull(feature.config());
        assertTrue(feature.placement().isJsonArray());
        assertEquals(1, snapshot.failures().count());
        assertTrue(snapshot.failures().samples().getFirst().startsWith("feature of examplemod:ore_glimmer"));
        assertTrue(snapshot.levelStems().isEmpty());
    }

    private static NativeModdedServer server() {
        MappedRegistry<PlacedFeature> features = new MappedRegistry<>(Registries.PLACED_FEATURE, Lifecycle.stable());
        features.register(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.parse("examplemod:ore_glimmer")),
                new PlacedFeature(Holder.direct(mock(Feature.class)), List.of()), RegistrationInfo.BUILT_IN);
        features.freeze();
        MappedRegistry<WorldCarver> carvers = new MappedRegistry<>(Registries.CARVER, Lifecycle.stable());
        carvers.freeze();
        MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        MappedRegistry<Structure> structures = new MappedRegistry<>(Registries.STRUCTURE, Lifecycle.stable());
        structures.freeze();
        MappedRegistry<StructureSet> structureSets = new MappedRegistry<>(Registries.STRUCTURE_SET, Lifecycle.stable());
        structureSets.freeze();
        MappedRegistry<DimensionType> dimensionTypes = new MappedRegistry<>(Registries.DIMENSION_TYPE, Lifecycle.stable());
        dimensionTypes.freeze();
        RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(
                List.of(biomes, features, carvers, structures, structureSets, dimensionTypes));
        Biome biome = Biome.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access),
                JsonParser.parseString(BIOME_JSON)).getOrThrow();
        biomes.register(ResourceKey.create(Registries.BIOME, Identifier.parse("examplemod:glade")), biome, RegistrationInfo.BUILT_IN);
        biomes.freeze();
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.registryAccess()).thenReturn(access.freeze());
        return NativeModdedServer.fromHandle(server);
    }

    private static NativeModdedLoader loader() {
        NativeModdedLoader loader = mock(NativeModdedLoader.class);
        when(loader.unmodifiedBiome(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return loader;
    }
}
