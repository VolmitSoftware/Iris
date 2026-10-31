package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.MaterialProgram;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public final class ExportIrFixtures {
    public static final String NAMESPACE = "iris_export";
    public static final String DIMENSION = "flat";
    public static final String BIOME = "plains";
    public static final String NOISE = "flat/height";
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    public static final int FLUID_HEIGHT = 62;
    public static final int SEA_LEVEL = 63;
    public static final double BASE_HEIGHT = 63.0;
    public static final double HEIGHT_AMPLITUDE = 16.0;

    private ExportIrFixtures() {
    }

    public static ExportIr flatNoise(DataVersion target, WorldgenExportMode mode) {
        Field zero = new Field.Const(0.0);
        Field noise = new Field.Noise(NAMESPACE + ":" + NOISE, 1.0, 0.0, null, null, null);
        Field height = new Field.Shared(DIMENSION + "/height", new Field.Binary(Field.BinaryOp.ADD, new Field.Const(BASE_HEIGHT),
                new Field.Binary(Field.BinaryOp.MUL, new Field.Const(HEIGHT_AMPLITUDE), noise)));
        Field y = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, -4064.0, 4062.0, -4064.0, 4062.0);
        Field finalDensity = new Field.Grid(new Field.Binary(Field.BinaryOp.MUL, new Field.Const(0.125),
                new Field.Binary(Field.BinaryOp.SUB, height, y)), 4, 8);
        RouterDef router = new RouterDef(zero, zero, zero, zero, zero, zero, new Field.Grid(height, 16, 1), finalDensity, AquiferDef.dry(height));
        DimensionDef dimension = new DimensionDef(DIMENSION, MIN_Y, HEIGHT, HEIGHT, FLUID_HEIGHT, "minecraft:stone", "minecraft:water", dimensionType());
        ClimateLayout climate = new ClimateLayout(List.of(new ClimateLayout.ClimateEntry(NAMESPACE + ":" + BIOME, ClimateLayout.ClimateAxis.TEMPERATURE, -2.0, 2.0)));
        return new ExportIr(NAMESPACE, target, mode, dimension, List.of(new NoiseDef(NOISE, -7, List.of(1.0, 1.0, 1.0))), router, climate,
                List.of(new BiomeDef(BIOME, biome())), MaterialProgram.rock("minecraft:stone"), List.of(), List.of(), List.of());
    }

    public static ExportIr withResources(ExportIr base, List<JsonResource> jsonResources, List<BinaryResource> binaryResources) {
        return new ExportIr(base.namespace(), base.target(), base.mode(), base.dimension(), base.noises(), base.router(), base.climate(), base.biomes(),
                base.material(), base.diagnostics(), jsonResources, binaryResources);
    }

    public static JsonObject dimensionType() {
        JsonObject type = new JsonObject();
        type.addProperty("ambient_light", 0.0);
        type.addProperty("coordinate_scale", 1.0);
        type.addProperty("has_ceiling", false);
        type.addProperty("has_ender_dragon_fight", false);
        type.addProperty("has_skylight", true);
        type.addProperty("infiniburn", "#minecraft:infiniburn_overworld");
        type.addProperty("monster_spawn_block_light_limit", 0);
        type.addProperty("monster_spawn_light_level", 0);
        return type;
    }

    public static JsonObject biome() {
        JsonObject effects = new JsonObject();
        effects.addProperty("water_color", "#3f76e4");
        JsonObject biome = new JsonObject();
        biome.addProperty("has_precipitation", true);
        biome.addProperty("temperature", 0.8);
        biome.addProperty("downfall", 0.4);
        biome.add("effects", effects);
        biome.add("carvers", new JsonArray());
        biome.add("features", new JsonArray());
        return biome;
    }
}
