package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;

public final class BiomeStage implements CompileStage {
    @Override
    public String name() {
        return "biomes";
    }

    @Override
    public void apply(PackCompileContext context) {
        BiomeDecision decision = new BiomeDecisionCompiler(context.styles()).compile(context);
        context.setBiomeDecision(decision);
        BiomeDefinitionCompiler.Definitions definitions = BiomeDefinitionCompiler.forTarget(context.capabilities().target())
                .compile(context, decision.base().loadKeyByBiomeId());
        context.setBiomes(definitions.biomes());
        context.jsonResources().addAll(definitions.tags());
    }
}
