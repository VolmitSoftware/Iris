package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.framework.StructureAssembler;
import art.arcane.iris.engine.framework.structure.StructureAssemblyResult;
import art.arcane.iris.engine.framework.structure.StructureGraphCompilation;
import art.arcane.iris.engine.framework.structure.StructureGraphCompiler;
import art.arcane.iris.engine.framework.structure.StructureGraphDiagnostic;
import art.arcane.iris.engine.framework.structure.StructureGraphResolver;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.math.RNG;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.iris.core.service.JigsawStudioService.failureMessage;

final class JigsawStudioEvaluator {
    private static final long PREVIEW_SEED = 1337L;
    private static final int SPATIAL_PREVIEW_BASE_Y = JigsawStudioLayout.FLOOR_Y + 48;

    private final JigsawStudioService service;
    final Map<UUID, JigsawStudioGraphEvaluation> evaluations = new ConcurrentHashMap<>();

    JigsawStudioEvaluator(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    void scheduleInitialEvaluation(ActiveStudio studio) {
        if (studio == null) {
            return;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        UUID requestId = request.requestId();
        if (!claimInitialEvaluation(
                studio.evaluationGeneration(),
                service.isCurrentRequest(studio, requestId))) {
            return;
        }
        publishEvaluation(studio, requestId, 1L);
    }

    static boolean claimInitialEvaluation(AtomicLong generation, boolean currentRequest) {
        return currentRequest
                && Objects.requireNonNull(generation, "Jigsaw Studio evaluation generation")
                .compareAndSet(0L, 1L);
    }

    void scheduleEvaluation(ActiveStudio studio) {
        if (studio == null) {
            return;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        UUID requestId = request.requestId();
        if (!service.isCurrentRequest(studio, requestId)) {
            return;
        }
        publishEvaluation(studio, requestId, studio.evaluationGeneration().incrementAndGet());
    }

    private void publishEvaluation(ActiveStudio studio, UUID requestId, long generation) {
        JigsawStudioGraphEvaluation previous = evaluations.get(requestId);
        JigsawStudioPreviewRenderer.PreviewBounds previousBounds = previous == null
                ? JigsawStudioPreviewRenderer.PreviewBounds.empty()
                : previous.previewBounds();
        evaluations.put(requestId, new JigsawStudioGraphEvaluation(
                requestId,
                generation,
                PREVIEW_SEED,
                JigsawStudioEvaluationState.PENDING,
                previous == null ? "" : previous.selectedTheme(),
                previous == null ? 0 : previous.pieceCount(),
                "Compiling the committed graph and assembling seed 1337",
                previousBounds));
        service.playerContext.refreshAllWorkcellContexts(studio);
        J.a(() -> evaluateGraph(studio, requestId, generation));
    }

    private void evaluateGraph(ActiveStudio studio, UUID requestId, long generation) {
        EvaluationComputation computation;
        try {
            JigsawStudioActivation.Request request = studio.generator().getRequest();
            IrisStructure structure = request.source().load(
                    IrisStructure.class,
                    request.structureKey(),
                    false);
            if (structure == null) {
                throw new IOException("The active structure resource no longer exists");
            }
            StructureGraphCompilation compilation = StructureGraphCompiler.compile(
                    structure,
                    StructureGraphResolver.forData(request.source()));
            StructureGraphDiagnostic firstError = firstDiagnostic(
                    compilation,
                    StructureGraphDiagnostic.Severity.ERROR);
            if (firstError != null) {
                computation = invalidEvaluation(
                        requestId,
                        generation,
                        firstError.code() + ": " + firstError.message());
            } else {
                IrisPosition origin = previewOrigin(studio.generator().getLayout(), structure);
                StructureAssembler assembler = StructureAssembler.forCompilation(compilation, origin);
                StructureAssemblyResult assembly = assembler.assemble(new RNG(PREVIEW_SEED));
                computation = evaluationForAssembly(
                        requestId,
                        generation,
                        compilation,
                        assembly,
                        studio.generator().getLayout().mode());
            }
        } catch (Throwable exception) {
            IrisLogging.reportError(exception);
            computation = invalidEvaluation(requestId, generation, failureMessage(exception));
        }
        completeEvaluation(studio, computation);
    }

    private EvaluationComputation evaluationForAssembly(
            UUID requestId,
            long generation,
            StructureGraphCompilation compilation,
            StructureAssemblyResult assembly,
            JigsawStudioMode mode
    ) throws IOException {
        if (assembly.status().isFailure()) {
            return invalidEvaluation(
                    requestId,
                    generation,
                    assembly.status().name().toLowerCase(Locale.ROOT) + ": " + assembly.detail());
        }
        if (!assembly.hasOutput()) {
            return new EvaluationComputation(
                    new JigsawStudioGraphEvaluation(
                            requestId,
                            generation,
                            PREVIEW_SEED,
                            JigsawStudioEvaluationState.WARNING,
                            assembly.selectedTheme(),
                            0,
                            assembly.detail().isEmpty()
                                    ? "Seed 1337 intentionally generated no structure"
                                    : assembly.detail(),
                            JigsawStudioPreviewRenderer.PreviewBounds.empty()),
                    JigsawStudioPreviewRenderer.PreviewPlan.empty());
        }
        List<PlacedStructurePiece> aligned = alignPreviewPieces(assembly.pieces(), mode);
        JigsawStudioPreviewRenderer.PreviewPlan plan = JigsawStudioPreviewRenderer.plan(aligned);
        StructureGraphDiagnostic firstWarning = firstDiagnostic(
                compilation,
                StructureGraphDiagnostic.Severity.WARNING);
        JigsawStudioEvaluationState state = firstWarning == null
                ? JigsawStudioEvaluationState.VALID
                : JigsawStudioEvaluationState.WARNING;
        String detail = firstWarning == null
                ? "Seed 1337 assembled " + aligned.size() + " piece(s)"
                : firstWarning.code() + ": " + firstWarning.message();
        return new EvaluationComputation(
                new JigsawStudioGraphEvaluation(
                        requestId,
                        generation,
                        PREVIEW_SEED,
                        state,
                        assembly.selectedTheme(),
                        aligned.size(),
                        detail,
                        plan.bounds()),
                plan);
    }

    private void completeEvaluation(ActiveStudio studio, EvaluationComputation computation) {
        JigsawStudioGraphEvaluation evaluated = computation.evaluation();
        UUID requestId = evaluated.requestId();
        if (!isCurrentEvaluation(studio, requestId, evaluated.generation())) {
            return;
        }
        service.previewRenderer.render(
                studio.world(),
                requestId,
                evaluated.generation(),
                computation.plan(),
                result -> {
                    if (!isCurrentEvaluation(studio, requestId, evaluated.generation())) {
                        return;
                    }
                    JigsawStudioGraphEvaluation completed = result.successful()
                            ? evaluated
                            : new JigsawStudioGraphEvaluation(
                            requestId,
                            evaluated.generation(),
                            PREVIEW_SEED,
                            JigsawStudioEvaluationState.WARNING,
                            evaluated.selectedTheme(),
                            evaluated.pieceCount(),
                            evaluated.detail() + "; " + result.failure(),
                            evaluated.previewBounds());
                    evaluations.put(requestId, completed);
                    service.playerContext.refreshAllWorkcellContexts(studio);
                });
    }

    private boolean isCurrentEvaluation(ActiveStudio studio, UUID requestId, long generation) {
        if (!service.isCurrentRequest(studio, requestId)
                || studio.evaluationGeneration().get() != generation) {
            return false;
        }
        JigsawStudioGraphEvaluation current = evaluations.get(requestId);
        return current != null && current.generation() == generation;
    }

    private static EvaluationComputation invalidEvaluation(
            UUID requestId,
            long generation,
            String detail
    ) {
        return new EvaluationComputation(
                new JigsawStudioGraphEvaluation(
                        requestId,
                        generation,
                        PREVIEW_SEED,
                        JigsawStudioEvaluationState.INVALID,
                        "",
                        0,
                        detail,
                        JigsawStudioPreviewRenderer.PreviewBounds.empty()),
                JigsawStudioPreviewRenderer.PreviewPlan.empty());
    }

    private static StructureGraphDiagnostic firstDiagnostic(
            StructureGraphCompilation compilation,
            StructureGraphDiagnostic.Severity severity
    ) {
        for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
            if (diagnostic.severity() == severity) {
                return diagnostic;
            }
        }
        return null;
    }

    private static IrisPosition previewOrigin(JigsawStudioLayout layout, IrisStructure structure) {
        int radius = Math.max(1, structure.getMaxSizeChunks()) * 16;
        return new IrisPosition(
                -radius - 32,
                0,
                Math.max(16, layout.extentZ() / 2));
    }

    static List<PlacedStructurePiece> alignPreviewPieces(
            List<PlacedStructurePiece> pieces,
            JigsawStudioMode mode
    ) {
        int minimumY = Integer.MAX_VALUE;
        for (PlacedStructurePiece piece : pieces) {
            minimumY = Math.min(minimumY, piece.getMinY());
        }
        int baseY = mode == JigsawStudioMode.SPATIAL_JIGSAW
                ? SPATIAL_PREVIEW_BASE_Y
                : JigsawStudioLayout.FLOOR_Y + 1;
        int shiftY = baseY - minimumY;
        List<PlacedStructurePiece> aligned = new ArrayList<>(pieces.size());
        for (PlacedStructurePiece piece : pieces) {
            aligned.add(new PlacedStructurePiece(
                    piece.getPiece(),
                    piece.getObject(),
                    piece.getX(),
                    piece.getY() + shiftY,
                    piece.getZ(),
                    piece.getRotation(),
                    piece.getMinX(),
                    piece.getMinY() + shiftY,
                    piece.getMinZ(),
                    piece.getMaxX(),
                    piece.getMaxY() + shiftY,
                    piece.getMaxZ()));
        }
        return List.copyOf(aligned);
    }

    static IrisStructure loadStudioStructure(ActiveStudio studio) {
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        return request.source().load(IrisStructure.class, request.structureKey(), false);
    }

    private record EvaluationComputation(
            JigsawStudioGraphEvaluation evaluation,
            JigsawStudioPreviewRenderer.PreviewPlan plan
    ) {
        private EvaluationComputation {
            Objects.requireNonNull(evaluation, "Jigsaw Studio graph evaluation");
            Objects.requireNonNull(plan, "Jigsaw Studio evaluation preview plan");
        }
    }
}
