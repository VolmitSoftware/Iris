package art.arcane.iris.nativegen.v26_3_R1;

public final class NativeStructureGenerationException extends IllegalStateException {
    private NativeStructureGenerationException(FailureContext context, Throwable cause) {
        super(message(context), cause);
    }

    private NativeStructureGenerationException(FailureContext context) {
        super(message(context));
    }

    public static NativeStructureGenerationException failure(String operation, String structureContext,
                                                             int chunkX, int chunkZ, Throwable cause) {
        return new NativeStructureGenerationException(
                new FailureContext(operation, structureContext, chunkX, chunkZ), cause);
    }

    public static NativeStructureGenerationException failure(String operation, String structureContext,
                                                             int chunkX, int chunkZ) {
        return new NativeStructureGenerationException(
                new FailureContext(operation, structureContext, chunkX, chunkZ));
    }

    private static String message(FailureContext context) {
        String resolvedContext = context.structureContext() == null || context.structureContext().isBlank()
                ? "<unregistered structure>"
                : context.structureContext();
        return "Iris native structure " + context.operation() + " failed for " + resolvedContext
                + " in chunk " + context.chunkX() + "," + context.chunkZ()
                + "; chunk generation was aborted to prevent partial structure state";
    }

    private record FailureContext(String operation, String structureContext, int chunkX, int chunkZ) {
    }
}
