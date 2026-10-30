package art.arcane.iris.pack.datapack.export.compile.surface;

import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;

public final class SurfaceStage implements CompileStage {
    @Override
    public String name() {
        return "surface";
    }

    @Override
    public void apply(PackCompileContext context) {
        context.setMaterial(new MaterialProgramCompiler(context, BlockStateResolver.forContext(context)).compile());
    }
}
