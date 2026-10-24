package art.arcane.iris.engine.terrain;

import art.arcane.iris.modules.post.PostProcessingSettings;
import art.arcane.iris.modules.post.PostProcessingSlabsSettings;
import art.arcane.iris.modules.post.PostProcessingWallsSettings;
import art.arcane.iris.modules.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.modules.terrain.FluidPaletteSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.modules.terrain.LockLayersMaxSettings;
import art.arcane.iris.modules.terrain.LockLayersSettings;
import art.arcane.iris.modules.terrain.RockPaletteSettings;
import art.arcane.iris.modules.terrain.SeaLayersSettings;
import art.arcane.iris.modules.terrain.SlabSettings;
import art.arcane.iris.modules.terrain.WallSettings;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.ModuleSettings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.volmit.IrisFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pack JSON as the module settings the decoder reads it into: each key a terrain or post feature declares, under its root
 * option, without loading the module host. {@code ModuleSettingsDecoder} reads the same shapes (see the settings decoding tests).
 */
public final class TerrainTestSettings {
    private static final Gson GSON = new Gson();
    private static final Map<String, Object[]> FEATURES = Map.ofEntries(
            Map.entry("layers", new Object[]{LayersSettings.class, "layers"}),
            Map.entry("seaLayers", new Object[]{SeaLayersSettings.class, "layers"}),
            Map.entry("caveCeilingLayers", new Object[]{CaveCeilingLayersSettings.class, "layers"}),
            Map.entry("slab", new Object[]{SlabSettings.class, "layer"}),
            Map.entry("wall", new Object[]{WallSettings.class, "layer"}),
            Map.entry("lockLayers", new Object[]{LockLayersSettings.class, "locked"}),
            Map.entry("lockLayersMax", new Object[]{LockLayersMaxSettings.class, "layers"}),
            Map.entry("rockPalette", new Object[]{RockPaletteSettings.class, "palette"}),
            Map.entry("fluidPalette", new Object[]{FluidPaletteSettings.class, "palette"}),
            Map.entry("postProcessing", new Object[]{PostProcessingSettings.class, "enabled"}),
            Map.entry("postProcessingSlabs", new Object[]{PostProcessingSlabsSettings.class, "enabled"}),
            Map.entry("postProcessingWalls", new Object[]{PostProcessingWallsSettings.class, "enabled"}));

    private TerrainTestSettings() {
    }

    /** The settings a biome or dimension file holds, of the terrain and post keys it writes. */
    @SuppressWarnings("unchecked")
    public static ModuleSettings of(String json) {
        List<IrisFeature> settings = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : GSON.fromJson(json, JsonObject.class).entrySet()) {
            Object[] feature = FEATURES.get(entry.getKey());
            if (feature == null) {
                continue;
            }
            JsonObject wrapped = new JsonObject();
            wrapped.add((String) feature[1], entry.getValue());
            settings.add(GSON.fromJson(wrapped, (Class<? extends IrisFeature>) feature[0]));
        }
        return BuiltInPack.settings(settings.toArray(new IrisFeature[0]));
    }
}
