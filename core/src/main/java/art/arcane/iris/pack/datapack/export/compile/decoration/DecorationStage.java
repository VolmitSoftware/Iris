package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.compile.surface.RepaintFeatureCompiler;

public final class DecorationStage implements CompileStage {
    @Override
    public String name() {
        return "decoration";
    }

    @Override
    public void apply(PackCompileContext context) {
        ExportedBiomes biomes = ExportedBiomes.load(context);
        BlockStateResolver blocks = BlockStateResolver.forContext(context);
        FeatureCatalog catalog = new FeatureCatalog(context.namespace(), WorldgenSyntax.forTarget(context.capabilities().target()));
        new RepaintFeatureCompiler(context, biomes, blocks, catalog).compile();
        new DecoratorCompiler(context, biomes, blocks, catalog).compile();
        new DepositCompiler(context, biomes, blocks, catalog).compile();
        context.jsonResources().addAll(catalog.resources());
    }
}
