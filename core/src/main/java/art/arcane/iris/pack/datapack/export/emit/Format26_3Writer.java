package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Set;

public final class Format26_3Writer extends BaseWorldgenJsonWriter {
    private static final Set<String> BIOME_KEYS = Set.of("has_precipitation", "temperature", "temperature_modifier", "downfall", "effects",
            "attributes", "carvers", "features");
    private static final Set<String> BLOCK_STATE_SLOTS = Set.of("result_state", "ore_block", "raw_ore_block", "filler_block");
    private static final Set<String> BLOCK_STATE_KEYS = Set.of("id", "properties");
    private static final double SPAWN_ELEVATION_SCALE = 64.0;
    private static final double SPAWN_LOWEST_ABOVE_SEA = 1.0;
    private static final double SPAWN_HIGHEST_ABOVE_SEA = 48.0;
    private static final double CLIMATE_LIMIT = 2.0;

    public Format26_3Writer() {
        super(DataVersion.V26_3, BIOME_KEYS);
    }

    @Override
    protected JsonObject noise(NoiseDef noise) {
        JsonArray modifiers = new JsonArray(noise.amplitudes().size());
        for (double amplitude : noise.amplitudes()) {
            modifiers.add(JsonNumbers.unbounded(amplitude));
        }
        JsonObject json = new JsonObject();
        json.addProperty("base_octave", noise.firstOctave());
        json.addProperty("octave_count", noise.amplitudes().size());
        json.add("amplitude_modifiers", modifiers);
        json.addProperty("base_amplitude", 1.0);
        json.addProperty("normalize", false);
        return json;
    }

    @Override
    protected JsonObject noiseSettings(ExportIr ir, Roots roots, JsonElement materialRule) {
        RouterDef router = ir.router();
        AquiferDef aquifer = router.aquifer();
        DimensionDef dimension = ir.dimension();
        JsonObject noiseRouter = new JsonObject();
        noiseRouter.add("temperature", roots.inline("temperature", router.temperature()));
        noiseRouter.add("vegetation", roots.inline("vegetation", router.vegetation()));
        noiseRouter.add("continents", roots.inline("continents", router.continents()));
        noiseRouter.add("erosion", roots.inline("erosion", router.erosion()));
        noiseRouter.add("depth", roots.inline("depth", router.depth()));
        noiseRouter.add("ridges", roots.inline("ridges", router.ridges()));
        noiseRouter.add("chunk_surface_level", roots.inline("chunk_surface_level", router.chunkSurfaceLevel()));
        noiseRouter.addProperty("final_density", roots.named("final_density", router.finalDensity()));
        JsonObject aquifers = new JsonObject();
        aquifers.add("barrier", roots.inline("aquifer/barrier", aquifer.barrier()));
        aquifers.add("fluid_level_floodedness", roots.inline("aquifer/fluid_level_floodedness", aquifer.floodedness()));
        aquifers.add("fluid_level_spread", roots.inline("aquifer/fluid_level_spread", aquifer.spread()));
        aquifers.add("lava", roots.inline("aquifer/lava", aquifer.lava()));
        aquifers.add("exclusion", roots.inline("aquifer/exclusion", aquifer.exclusion()));
        aquifers.add("surface_level", roots.inline("aquifer/surface_level", aquifer.surfaceLevel()));
        JsonObject spawn = new JsonObject();
        spawn.add(roots.registered("spawn_elevation", spawnElevation(aquifer.surfaceLevel(), dimension.fluidHeight())),
                span(SPAWN_LOWEST_ABOVE_SEA / SPAWN_ELEVATION_SCALE, SPAWN_HIGHEST_ABOVE_SEA / SPAWN_ELEVATION_SCALE, target()));
        JsonArray spawnTargets = new JsonArray(1);
        spawnTargets.add(spawn);
        JsonObject noise = new JsonObject();
        noise.addProperty("min_y", dimension.minY());
        noise.addProperty("height", dimension.height());
        JsonObject settings = new JsonObject();
        settings.add("aquifers", aquifers);
        settings.add("default_block", blockState(dimension.defaultBlock()));
        settings.add("default_fluid", blockState(dimension.defaultFluid()));
        settings.addProperty("disable_mob_generation", false);
        settings.addProperty("legacy_random_source", false);
        settings.addProperty("material_rule", ir.namespace() + ":" + dimension.name());
        settings.add("noise", noise);
        settings.add("noise_router", noiseRouter);
        settings.addProperty("sea_level", dimension.seaLevel());
        settings.add("spawn_target", spawnTargets);
        return settings;
    }

    @Override
    protected JsonElement materialRule(ExportIr ir) {
        JsonElement rule = ir.material().rule();
        requireBlockStates(rule, "Material rule for '" + ir.dimension().name() + "'");
        return rule;
    }

    @Override
    protected Map<String, JsonElement> materialFiles(ExportIr ir, JsonElement rule) {
        return Map.of(path(ir.namespace(), "worldgen/material_rule", ir.dimension().name()), rule);
    }

    private static Field spawnElevation(Field surfaceLevel, int fluidHeight) {
        Field aboveSea = new Field.Binary(Field.BinaryOp.SUB, surfaceLevel, new Field.Const(fluidHeight));
        return new Field.Clamp(new Field.Binary(Field.BinaryOp.MUL, aboveSea, new Field.Const(1.0 / SPAWN_ELEVATION_SCALE)), -CLIMATE_LIMIT, CLIMATE_LIMIT);
    }

    private static JsonObject blockState(String block) {
        JsonObject state = new JsonObject();
        state.addProperty("id", block);
        return state;
    }

    private static void requireBlockStates(JsonElement element, String resource) {
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                requireBlockStates(item, resource);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> member : element.getAsJsonObject().entrySet()) {
            if (BLOCK_STATE_SLOTS.contains(member.getKey())) {
                requireBlockState(member.getValue(), resource);
            } else {
                requireBlockStates(member.getValue(), resource);
            }
        }
    }

    private static void requireBlockState(JsonElement state, String resource) {
        if (state.isJsonPrimitive() && state.getAsJsonPrimitive().isString()) {
            return;
        }
        if (state.isJsonObject() && state.getAsJsonObject().has("id") && BLOCK_STATE_KEYS.containsAll(state.getAsJsonObject().keySet())) {
            return;
        }
        throw new IllegalArgumentException(resource + " uses block state " + state + "; 26.3 block states are an id string or {\"id\",\"properties\"}");
    }
}
