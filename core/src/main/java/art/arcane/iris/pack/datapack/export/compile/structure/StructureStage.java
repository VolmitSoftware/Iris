package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;

public final class StructureStage implements CompileStage {
    @Override
    public String name() {
        return "structures";
    }

    @Override
    public void apply(PackCompileContext context) {
        DatapackBundle bundle = DatapackBundle.load(context);
        StructureDefinitions definitions = StructureDefinitions.of(bundle, context.capabilities().target());
        ExportedBiomes biomes = ExportedBiomes.load(context);
        new NativeStructureOverrides(context, bundle, definitions).apply(PlacementCompiler.suppressedSources(context));
        new PlacementCompiler(context, definitions, biomes).compile();
        BundledTags.write(context, bundle.tags(), biomes);
        bundle.emitFiles(context);
    }
}
