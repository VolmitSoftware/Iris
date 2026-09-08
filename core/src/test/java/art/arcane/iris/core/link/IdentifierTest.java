package art.arcane.iris.core.link;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IdentifierTest {
    @Test
    public void abareKeyIsQualifiedIntoTheMinecraftNamespace() {
        Identifier identifier = Identifier.fromString("stone");

        assertEquals("minecraft", identifier.namespace());
        assertEquals("stone", identifier.key());
    }

    @Test
    public void aQualifiedKeyKeepsBothHalves() {
        Identifier identifier = Identifier.fromString("oraxen:ruby_ore");

        assertEquals("oraxen", identifier.namespace());
        assertEquals("ruby_ore", identifier.key());
    }

    @Test
    public void onlyTheFirstSeparatorSplitsTheIdentifier() {
        Identifier identifier = Identifier.fromString("itemsadder:rocks/ruby:ore");

        assertEquals("itemsadder", identifier.namespace());
        assertEquals("rocks/ruby:ore", identifier.key());
    }

    @Test
    public void aLeadingSeparatorProducesAnEmptyNamespaceRatherThanADefault() {
        Identifier identifier = Identifier.fromString(":ruby_ore");

        assertEquals("", identifier.namespace());
        assertEquals("ruby_ore", identifier.key());
    }

    @Test
    public void theRenderedFormRoundTripsThroughTheParser() {
        Identifier identifier = new Identifier("nexo", "village/lamp");

        assertEquals("nexo:village/lamp", identifier.toString());
        assertEquals(identifier, Identifier.fromString(identifier.toString()));
        assertEquals(Identifier.fromString("stone"), Identifier.fromString("minecraft:stone"));
    }
}
