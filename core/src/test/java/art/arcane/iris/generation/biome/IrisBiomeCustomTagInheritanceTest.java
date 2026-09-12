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

package art.arcane.iris.generation.biome;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisBiomeCustomTagInheritanceTest {
    private static KList<String> tags(String... values) {
        KList<String> list = new KList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    @Test
    public void customBiomeInheritsTheVanillaDerivativeTagsOnTopOfAuthorTags() {
        IrisBiomeCustom biome = new IrisBiomeCustom()
                .setTags(tags("mymod:is_spooky"));

        KList<String> resolved = biome.getEffectiveTags("minecraft:plains");

        assertEquals("mymod:is_spooky", resolved.get(0));
        assertTrue(resolved.contains("minecraft:is_overworld"));
        // stronghold_biased_to is not inherited: strongholds.json reads it as preferred_biomes, so inheriting it
        // would enter every derived Iris biome into vanilla's stronghold ring around spawn.
        assertFalse(resolved.contains("minecraft:stronghold_biased_to"));
    }

    @Test
    public void unknownDerivativeContributesNothingButKeepsAuthorTags() {
        IrisBiomeCustom biome = new IrisBiomeCustom()
                .setTags(tags("mymod:is_spooky"));

        assertEquals(List.of("mymod:is_spooky"), biome.getEffectiveTags("somemod:alien_waste"));
        assertEquals(List.of("mymod:is_spooky"), biome.getEffectiveTags(null));
        assertEquals(List.of("mymod:is_spooky"), biome.getEffectiveTags(""));
    }

    @Test
    public void noAuthorTagsStillInheritsAndNeverReturnsNull() {
        IrisBiomeCustom biome = new IrisBiomeCustom();

        KList<String> resolved = biome.getEffectiveTags("minecraft:warm_ocean");

        assertTrue(resolved.contains("minecraft:is_ocean"));
        assertTrue(resolved.contains("minecraft:produces_corals_from_bonemeal"));
        assertFalse(resolved.isEmpty());
        assertTrue(biome.getEffectiveTags(null).isEmpty());
    }

    @Test
    public void duplicateTagsCollapse() {
        IrisBiomeCustom biome = new IrisBiomeCustom()
                .setTags(tags("minecraft:is_overworld", "minecraft:is_overworld"));

        KList<String> resolved = biome.getEffectiveTags("minecraft:plains");

        assertEquals(1, resolved.stream().filter("minecraft:is_overworld"::equals).count());
    }

    @Test
    public void structureTagsAreNeverInherited() {
        // Native structure placement resolves through the biome's structure derivative, so pulling a custom
        // biome into a has_structure tag would place the structure twice.
        for (String biomeKey : List.of("minecraft:plains", "minecraft:desert", "minecraft:deep_ocean",
                "minecraft:nether_wastes", "minecraft:the_end")) {
            for (String tag : IrisVanillaBiomeTags.tagsFor(biomeKey)) {
                assertFalse(biomeKey + " -> " + tag, tag.contains("has_structure"));
            }
        }
    }

    @Test
    public void tagTableCoversTheVanillaDimensionFamilies() {
        assertTrue(IrisVanillaBiomeTags.knownBiomeCount() >= 60);
        assertTrue(IrisVanillaBiomeTags.tagsFor("minecraft:plains").contains("minecraft:is_overworld"));
        assertTrue(IrisVanillaBiomeTags.tagsFor("minecraft:nether_wastes").contains("minecraft:is_nether"));
        assertTrue(IrisVanillaBiomeTags.tagsFor("minecraft:the_end").contains("minecraft:is_end"));
        // Case and namespace normalisation: pack authors write both forms.
        assertEquals(IrisVanillaBiomeTags.tagsFor("minecraft:plains"),
                IrisVanillaBiomeTags.tagsFor("PLAINS"));
        assertTrue(IrisVanillaBiomeTags.tagsFor("minecraft:nowhere_at_all").isEmpty());
    }
}
