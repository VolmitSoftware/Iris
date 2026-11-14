package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Empties the carvers of the exported biomes and exports the open water of the aquifer. Listed caves are not exported
 * yet: the exported world has no caves, and the stage says so when the pack lists any.
 */
public final class CaveStage implements CompileStage {
    static final String LISTED_CAVES_NOT_EXPORTED = "Listed caves are not exported yet; the exported world has no Iris caves";
    private static final String CARVERS = "carvers";

    @Override
    public String name() {
        return "caves";
    }

    @Override
    public void apply(PackCompileContext context) {
        CaveInputs inputs = CaveInputs.of(context);
        if (listsCaves(context.dimension(), context.data())) {
            inputs.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.LISTED_CAVES,
                    inputs.resource(), LISTED_CAVES_NOT_EXPORTED));
        }
        context.setAquifer(new AquiferCompiler(inputs).compile());
        List<BiomeDef> biomes = context.biomes();
        if (biomes.stream().anyMatch(CaveStage::carves)) {
            context.setBiomes(withoutCarvers(biomes));
        }
    }

    private static boolean listsCaves(IrisDimension dimension, IrisData data) {
        if (!dimension.getCaves().isEmpty()) {
            return true;
        }
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            if (!region.getCaves().isEmpty()) {
                return true;
            }
        }
        for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
            if (!biome.getCaves().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<BiomeDef> withoutCarvers(List<BiomeDef> biomes) {
        List<BiomeDef> cleaned = new ArrayList<>(biomes.size());
        for (BiomeDef biome : biomes) {
            if (!carves(biome)) {
                cleaned.add(biome);
                continue;
            }
            JsonObject json = biome.json().deepCopy();
            json.add(CARVERS, new JsonArray());
            cleaned.add(new BiomeDef(biome.id(), json));
        }
        return cleaned;
    }

    private static boolean carves(BiomeDef biome) {
        JsonElement carvers = biome.json().get(CARVERS);
        return carvers != null && !(carvers.isJsonArray() && carvers.getAsJsonArray().isEmpty());
    }
}
