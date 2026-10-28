package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.core.loader.JsonFieldNames;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The fields the object module declares for its placement type are the keys Gson reads, which settings keys may not take. */
public class PlacementTypeFieldsTest {
    @Test
    public void theDeclaredFieldsAreTheKeysGsonReadsForAPlacement() {
        assertEquals(JsonFieldNames.of(ObjectPlacement.class), ObjectPlacement.jsonFields());
    }

    @Test
    public void aSerializedNameAndItsAlternatesAreFields() {
        assertTrue(ObjectPlacement.jsonFields().contains("forcePlace"));
        assertTrue(ObjectPlacement.jsonFields().contains("force"));
    }

    @Test
    public void whatIrisKeepsBesideThePlacementIsNoField() {
        assertTrue(!ObjectPlacement.jsonFields().contains("json"));
        assertTrue(!ObjectPlacement.jsonFields().contains("hostForm"));
    }
}
