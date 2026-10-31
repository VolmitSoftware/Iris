package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.Field;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CaveStage implements CompileStage {
    private static final String CARVERS = "carvers";

    @Override
    public String name() {
        return "caves";
    }

    @Override
    public void apply(PackCompileContext context) {
        CaveInputs inputs = CaveInputs.of(context);
        CaveSelection selection = CaveSelection.resolve(inputs, context.dimension(), context.data(), context.biomeCodes());
        CaveFieldCompiler compiler = new CaveFieldCompiler(context.styles(), inputs);
        Optional<Field> caves = compiler.compile(selection);
        if (caves.isPresent()) {
            Field.Shared field = new Field.Shared(DeepCaveCompiler.FIELD, caves.get());
            Optional<DeepCaveCompiler.DeepCaves> deep = new DeepCaveCompiler(inputs).compile(field, selection);
            context.setCaveField(field, CaveFieldCompiler.WALL_BAND);
            context.setCaves(deep.map(DeepCaveCompiler.DeepCaves::router).orElse(field));
            deep.ifPresent(found -> context.setDeepCaves(found.opening()));
        }
        context.setAquifer(new AquiferCompiler(inputs).compile(selection));
        List<BiomeDef> biomes = context.biomes();
        if (biomes.stream().anyMatch(CaveStage::carves)) {
            context.setBiomes(withoutCarvers(biomes));
        }
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
