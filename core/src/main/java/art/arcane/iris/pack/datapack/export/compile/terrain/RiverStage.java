package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.Field;

public final class RiverStage implements CompileStage {
    private static final String ESTIMATE_TAG = "estimate";

    @Override
    public String name() {
        return "rivers";
    }

    @Override
    public void apply(PackCompileContext context) {
        Field natural = context.naturalHeight();
        Field carved = new RiverCompiler(context.diagnostics()).compile(natural, context.dimension(), context.noises(), context.capabilities());
        context.setCarvedHeight(carved);
        context.setSurfaceEstimate(FieldMacros.substitute(carved, natural, context.climateHeight(), ESTIMATE_TAG));
    }
}
