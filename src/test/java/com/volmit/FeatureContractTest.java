package com.volmit;

import com.volmit.config.*;
import com.volmit.generation.*;
import com.volmit.hunk.Hunk;
import com.volmit.host.ArrayHunk;
import com.volmit.host.ModuleRegistry;
import com.volmit.resource.ResourceId;
import com.volmit.support.TestContexts;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class FeatureContractTest {
    @Desc("Example settings")
    @Snippet("fill-settings")
    @Feature(id = "example:fill", name = "Fill", field = "fill", targets = FeatureAttachment.Target.BIOME)
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
    void registryBindsDecodedOptionsToTheCorrectModifier() {
        var host = new ModuleRegistry();
        host.register(FillOptions.class, Integer.class, FillModifier::new);
        var registration = host.freeze().generation(FillOptions.class, Integer.class);
        var modifier = registration.configure(ResourceId.parse("test:fill/0"), new FillOptions());
        var output = new ArrayHunk<>(Integer.class, 1, 1, 1, 0);
        modifier.modify(TestContexts.generation(), output);
        assertEquals(12, output.get(0, 0, 0));
    }

    private static class FillModifier extends GenerationModifier<Integer> {
        private final FillOptions options;
        FillModifier(FillOptions options) {
            this.options = options;
        }
        public String id() { return "example:fill-modifier"; }
        public void modify(GenerationContext context, Hunk<Integer> volume) {
            volume.set(0, 0, 0, options.value);
        }
    }

    static class InvalidOptions {
        @MinNumber(10) @MaxNumber(2) private int value;
    }

    static class NonnumericOptions {
        @MinNumber(0) private String value;
    }

    @Test
    void rejectsInconsistentAnnotationBounds() {
        assertThrows(IllegalArgumentException.class, () -> OptionsMetadata.inspect(InvalidOptions.class));
        assertThrows(IllegalArgumentException.class, () -> OptionsMetadata.inspect(NonnumericOptions.class));
    }
}
