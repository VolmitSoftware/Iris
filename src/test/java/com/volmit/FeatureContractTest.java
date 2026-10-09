package com.volmit;

import com.volmit.config.*;
import com.volmit.host.FeatureDescriptor;
import com.volmit.resource.ResourceId;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class FeatureContractTest {
    @Desc("Example settings")
    @Snippet("fill-settings")
    @Feature(id = "example:fill", name = "Fill", targets = FeatureAttachment.Target.BIOME)
    public static class FillOptions implements IrisFeature {
        @Desc("Replacement value")
        @MinNumber(0)
        @MaxNumber(128)
        private int value = 12;
        private transient int cache;
        private static int ignored;
    }

    @Test
    void readsPrivateAnnotatedOptionsWithoutIncludingRuntimeState() {
        var metadata = OptionsMetadata.inspect(FillOptions.class);
        assertEquals("Example settings", metadata.description());
        assertEquals("fill-settings", metadata.snippet());
        assertEquals(1, metadata.options().size());
        var option = metadata.options().getFirst();
        assertEquals("value", option.name());
        assertEquals(int.class, option.type());
        assertEquals("Replacement value", option.description());
        assertEquals(0.0, option.minimum());
        assertEquals(128.0, option.maximum());
    }

    @Test
    void descriptorReadsFeatureIdentityAndAttachment() {
        var descriptor = FeatureDescriptor.of(FillOptions.class);
        assertEquals(ResourceId.parse("example:fill"), descriptor.id());
        assertEquals("example:fill", descriptor.key());
        assertEquals(java.util.Set.of(FeatureAttachment.Target.BIOME), descriptor.attachment().targets());
        assertEquals(1, descriptor.options().options().size());
    }

    static class InvalidOptions {
        @MinNumber(10) @MaxNumber(2) private int value;
    }

    static class NonnumericOptions {
        @MinNumber(0) private String value;
    }

    static class MinItemsOnList {
        @MinItems(1) private java.util.List<String> values = java.util.List.of("a");
        @MinItems(2) private java.util.Set<String> unique = java.util.Set.of();
        @MinItems(0) private String[] array = {};
    }

    static class MinItemsOnText {
        @MinItems(1) private String value;
    }

    static class NegativeMinItems {
        @MinItems(-1) private java.util.List<String> values;
    }

    @Test
    void minItemsFitsListsSetsAndArraysOnly() {
        var options = OptionsMetadata.inspect(MinItemsOnList.class).options();

        assertEquals(java.util.List.of(1, 2, 0), options.stream().map(OptionsMetadata.Option::minItems).toList());
        assertThrows(IllegalArgumentException.class, () -> OptionsMetadata.inspect(MinItemsOnText.class));
        assertThrows(IllegalArgumentException.class, () -> OptionsMetadata.inspect(NegativeMinItems.class));
    }

    @Test
    void rejectsInconsistentAnnotationBounds() {
        assertThrows(IllegalArgumentException.class, () -> OptionsMetadata.inspect(InvalidOptions.class));
        assertThrows(IllegalArgumentException.class, () -> OptionsMetadata.inspect(NonnumericOptions.class));
    }
}
