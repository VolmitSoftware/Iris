package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionType;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Locale;

public final class DimensionStage implements CompileStage {
    private static final String DEFAULT_ROCK = "minecraft:stone";
    private static final String DEFAULT_FLUID = "minecraft:water";
    private static final float FULLBRIGHT_AMBIENT_LIGHT = 1.0f;

    @Override
    public String name() {
        return "dimension";
    }

    @Override
    public void apply(PackCompileContext context) {
        IrisDimension dimension = context.dimension();
        int minY = dimension.getMinHeight();
        DimensionDef aligned = DimensionDef.aligned(
                VanillaResourceIdentifier.sanitizePath(dimension.getLoadKey()),
                minY,
                dimension.getMaxHeight() - minY,
                dimension.getLogicalHeight(),
                dimension.getFluidHeight() + minY,
                paletteBlock(dimension.getRockPalette(), DEFAULT_ROCK, "rock"),
                paletteBlock(dimension.getFluidPalette(), DEFAULT_FLUID, "fluid"),
                new JsonObject(),
                context.diagnostics());
        IrisDimensionType type = new IrisDimensionType(
                dimension.getBaseDimension(),
                typeOptions(dimension),
                aligned.logicalHeight(),
                aligned.height(),
                aligned.minY());
        JsonObject typeJson = JsonParser.parseString(type.toJson(context.capabilities().target().get())).getAsJsonObject();
        context.setDimensionDef(new DimensionDef(
                aligned.name(),
                aligned.minY(),
                aligned.height(),
                aligned.logicalHeight(),
                aligned.fluidHeight(),
                aligned.defaultBlock(),
                aligned.defaultFluid(),
                typeJson));
    }

    private static IrisDimensionTypeOptions typeOptions(IrisDimension dimension) {
        IrisDimensionTypeOptions options = dimension.getDimensionOptions();
        return dimension.isFullbright() ? options.copy().ambientLight(FULLBRIGHT_AMBIENT_LIGHT) : options;
    }

    private static String paletteBlock(IrisMaterialPalette palette, String fallback, String role) {
        List<IrisBlockData> entries = palette == null ? null : palette.getPalette();
        if (entries == null || entries.isEmpty() || entries.getFirst() == null) {
            return fallback;
        }
        String raw = entries.getFirst().getBlock();
        String block = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int properties = block.indexOf('[');
        String id = properties < 0 ? block : block.substring(0, properties);
        String namespaced = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        return VanillaResourceIdentifier.requireIdentifier(namespaced, "dimension " + role + " block");
    }
}
