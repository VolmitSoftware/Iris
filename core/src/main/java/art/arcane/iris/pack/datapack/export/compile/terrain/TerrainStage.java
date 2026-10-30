package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;

public final class TerrainStage implements CompileStage {
    @Override
    public String name() {
        return "terrain";
    }

    @Override
    public void apply(PackCompileContext context) {
        BiomeCodes codes = context.baseBiomeCodes();
        TerrainHeight terrain = new HeightCompiler(context.styles(), context.diagnostics())
                .compile(codes, context.dimension(), context.data(), context.noises(), context.capabilities());
        context.setHeight(terrain.height());
        context.setClimateHeight(new ClimateHeightCompiler(context.styles())
                .compile(codes, context.dimension(), context.data(), context.noises(), context.capabilities()));
        context.setVolumetric(new VolumetricCompiler(context.styles(), context.diagnostics())
                .compile(terrain, codes, context.dimension(), context.data(), context.noises(), context.capabilities()));
    }
}
