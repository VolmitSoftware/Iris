package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeStage;
import art.arcane.iris.pack.datapack.export.compile.cave.CaveStage;
import art.arcane.iris.pack.datapack.export.compile.decoration.DecorationStage;
import art.arcane.iris.pack.datapack.export.compile.object.ObjectStage;
import art.arcane.iris.pack.datapack.export.compile.structure.StructureStage;
import art.arcane.iris.pack.datapack.export.compile.subterrain.SubterrainStage;
import art.arcane.iris.pack.datapack.export.compile.surface.SurfaceStage;
import art.arcane.iris.pack.datapack.export.compile.terrain.RiverStage;
import art.arcane.iris.pack.datapack.export.compile.terrain.TerrainStage;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;

import java.util.List;

public final class PackCompiler {
    private static final String ROUTER_STAGE = "router";

    private final List<CompileStage> stages;

    public PackCompiler(List<CompileStage> stages) {
        this.stages = List.copyOf(stages);
    }

    public static PackCompiler create() {
        return new PackCompiler(List.of(
                new DimensionStage(),
                new ImageMapStage(),
                new BiomeStage(),
                new TerrainStage(),
                new RiverStage(),
                new CaveStage(),
                new SurfaceStage(),
                new DecorationStage(),
                new StructureStage(),
                new SubterrainStage(),
                new ObjectStage()));
    }

    public ExportIr compile(PackCompileContext context, WorldgenExportProgress progress) {
        for (CompileStage stage : stages) {
            progress.stage(stage.name());
            stage.apply(context);
        }
        progress.stage(ROUTER_STAGE);
        DimensionDef dimension = context.dimensionDef();
        BiomeCodes codes = context.biomeCodes();
        RouterDef router = RouterCompiler.compile(
                context.height(),
                context.volumetric(),
                context.caves(),
                context.aquifer(),
                codes,
                dimension);
        return new ExportIr(
                context.namespace(),
                context.capabilities().target(),
                context.mode(),
                dimension,
                context.noises().definitions(),
                router,
                codes.layout(),
                context.features().apply(context.biomes(), context.namespace()),
                context.material(),
                context.diagnostics(),
                context.jsonResources(),
                context.binaryResources());
    }
}
