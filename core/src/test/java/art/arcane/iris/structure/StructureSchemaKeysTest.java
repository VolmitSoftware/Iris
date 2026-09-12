package art.arcane.iris.structure;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructureSchemaKeysTest {
    @Test
    public void exposesOnlyExistingIrisStructureResources() {
        KList<String> result = StructureSchemaKeys.collect(
                Arrays.asList("minecraft_stronghold", "custom/cool_tower"),
                Collections.emptyList());

        assertEquals(Arrays.asList("custom/cool_tower", "minecraft_stronghold"), result);
    }

    @Test
    public void nativeRegistryKeysAreNotSynthesized() {
        KList<String> result = StructureSchemaKeys.collect(
                Collections.singletonList("custom/castle"),
                Collections.emptyList());

        assertFalse(result.contains("minecraft_stronghold"));
        assertFalse(result.contains("minecraft:stronghold"));
        assertTrue(result.contains("custom/castle"));
    }

    @Test
    public void jigsawComponentPiecesAreExcluded() {
        List<String> pieces = Arrays.asList(
                "village/taiga/houses/taiga_small_house_5",
                "trial_chambers/corridor/straight_1");

        KList<String> result = StructureSchemaKeys.collect(
                Arrays.asList("village/taiga/houses/taiga_small_house_5", "custom/castle"),
                pieces);

        assertFalse(result.contains("village/taiga/houses/taiga_small_house_5"));
        assertTrue(result.contains("custom/castle"));
    }

    @Test
    public void resultIsSortedAndDeduplicated() {
        KList<String> result = StructureSchemaKeys.collect(
                Arrays.asList("zeta", "alpha", "zeta"),
                Collections.emptyList());

        assertEquals(Arrays.asList("alpha", "zeta"), result);
    }

    @Test
    public void nullCollectionsAreHandled() {
        assertTrue(StructureSchemaKeys.collect(null, null).isEmpty());
    }

    @Test
    public void blankAndNullEntriesAreIgnored() {
        KList<String> result = StructureSchemaKeys.collect(
                Arrays.asList("custom_one", "", null, "   "),
                Arrays.asList("", null));

        assertEquals(Collections.singletonList("custom_one"), result);
    }
}
