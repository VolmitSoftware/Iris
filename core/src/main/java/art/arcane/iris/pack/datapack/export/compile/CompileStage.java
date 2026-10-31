package art.arcane.iris.pack.datapack.export.compile;

public interface CompileStage {
    String name();

    void apply(PackCompileContext context);
}
