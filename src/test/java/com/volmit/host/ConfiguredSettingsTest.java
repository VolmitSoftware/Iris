package com.volmit.host;

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.ResourceId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguredSettingsTest {
    private static final ResourceId DIMENSION = ResourceId.parse("pack:dimension/overworld");
    private static final ResourceId BIOME = ResourceId.parse("pack:biome/plains");
    private static final ResourceId OBJECT = ResourceId.parse("pack:object/tree");

    @Feature(id = "test:levels", name = "Levels", key = "levels", targets = {Target.DIMENSION, Target.BIOME, Target.OBJECT_PLACEMENT})
    static final class Levels implements IrisFeature {}

    @Feature(id = "test:other", name = "Other", key = "other", targets = Target.BIOME)
    static final class Other implements IrisFeature {}

    @Test
    void entriesAreGroupedByTypeInLevelOrderAndNeverMerged() {
        var biome = new Entry<>(Target.BIOME, BIOME, new Levels());
        var dimension = new Entry<>(Target.DIMENSION, DIMENSION, new Levels());

        var settings = ConfiguredSettings.of(List.of(biome, dimension));

        assertEquals(List.of(dimension, biome), settings.find(Levels.class));
        assertEquals(List.of(), settings.find(Other.class));
        assertEquals(List.of(), ConfiguredSettings.none().find(Levels.class));
    }

    @Feature(id = "test:base", name = "Base", key = "base", targets = {Target.BIOME, Target.OBJECT_PLACEMENT})
    static class Base implements IrisFeature {}

    static final class Refined extends Base {}

    @Test
    void aSubclassValueIsFoundUnderTheTypeThatDeclaresItsFeature() {
        var refined = new Entry<>(Target.BIOME, BIOME, new Refined());

        assertEquals(List.of(refined), ConfiguredSettings.of(List.of(refined)).find(Base.class));
        var twice = assertThrows(IllegalArgumentException.class, () -> ConfiguredSettings.placement(OBJECT, new Base(), new Refined()));
        assertEquals("Settings configured twice: " + Base.class.getName(), twice.getMessage());
    }

    @Test
    void aPlacementDeclaresEachTypeOnce() {
        var levels = new Levels();
        assertEquals(List.of(new Entry<>(Target.OBJECT_PLACEMENT, OBJECT, levels)),
                ConfiguredSettings.placement(OBJECT, levels).find(Levels.class));
        var twice = assertThrows(IllegalArgumentException.class, () -> ConfiguredSettings.placement(OBJECT, new Levels(), new Levels()));
        assertEquals("Settings configured twice: " + Levels.class.getName(), twice.getMessage());
    }
}
