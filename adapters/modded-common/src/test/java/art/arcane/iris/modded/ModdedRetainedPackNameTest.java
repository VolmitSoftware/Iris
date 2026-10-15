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

import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContract.PhysicalResourceKey;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ModdedRetainedPackNameTest {
    private static final String FINGERPRINT = "11".repeat(32);
    private static final Path PACK_ROOT = Path.of("/srv/world/dimensions/aether/the_aether/iris/generation/epochs/e/pack");

    @Test
    public void borrowedRetainedPackTakesItsNameFromTheRecordedScopedBiomeAliases() throws IOException {
        GenerationEpoch epoch = epoch("aether:the_aether", "sky", List.of(
                ModdedWorldgenIds.biomeRef("skypack", "sky", "meadow"),
                "sky:meadow",
                "iris:biomes/" + "ab".repeat(32)));

        assertEquals("skypack", ModdedForcedDatapack.retainedPackName(epoch, PACK_ROOT));
        assertEquals(List.of(ModdedWorldgenIds.biomeRef("skypack", "sky", "meadow"), "sky:meadow"),
                ModdedWorldgenIds.legacyBiomeRefs(ModdedForcedDatapack.retainedPackName(epoch, PACK_ROOT), "sky", "meadow"));
    }

    @Test
    public void scopedAliasesOfOtherDimensionsAreIgnored() throws IOException {
        GenerationEpoch epoch = epoch("aether:the_aether", "sky", List.of(
                ModdedWorldgenIds.biomeRef("otherpack", "ground", "meadow"),
                ModdedWorldgenIds.biomeRef("skypack", "sky", "meadow")));

        assertEquals("skypack", ModdedForcedDatapack.retainedPackName(epoch, PACK_ROOT));
    }

    @Test
    public void borrowedRetainedPackWithoutScopedAliasesKeepsTheDimensionKey() throws IOException {
        GenerationEpoch epoch = epoch("aether:the_aether", "sky", List.of("iris:biomes/" + "ab".repeat(32)));

        assertEquals("sky", ModdedForcedDatapack.retainedPackName(epoch, PACK_ROOT));
    }

    @Test
    public void aliasesFromSeveralPacksAreRefused() {
        GenerationEpoch epoch = epoch("aether:the_aether", "sky", List.of(
                ModdedWorldgenIds.biomeRef("skypack", "sky", "meadow"),
                ModdedWorldgenIds.biomeRef("otherpack", "sky", "meadow")));

        IOException refused = assertThrows(IOException.class, () -> ModdedForcedDatapack.retainedPackName(epoch, PACK_ROOT));
        assertEquals(true, refused.getMessage().contains(PACK_ROOT.toString()));
    }

    static GenerationEpoch epoch(String dimensionTypeKey, String dimensionKey, List<String> biomeKeys) {
        Map<PhysicalResourceKey, String> definitions = new TreeMap<>();
        for (String biome : biomeKeys) {
            definitions.put(new PhysicalResourceKey(GenerationRegistryContractFactory.BIOME_REGISTRY, biome), FINGERPRINT);
        }
        GenerationEpoch.DimensionContract dimension = new GenerationEpoch.DimensionContract(
                dimensionKey,
                dimensionTypeKey,
                "NORMAL",
                "OVERWORLD",
                127,
                0,
                256,
                256,
                1D,
                false,
                "none",
                0,
                "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA,
                FINGERPRINT
        );
        return GenerationEpoch.create(new GenerationEpoch.Spec(FINGERPRINT, 1, 5L, 1, 1, 1, FINGERPRINT, dimension,
                GenerationRegistryContract.fromDefinitions(definitions)));
    }
}
