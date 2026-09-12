package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.object.IrisObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StructureGraphCompilation {
    private final CompiledStructureGraph graph;
    private final List<StructureGraphDiagnostic> diagnostics;
    private final List<StructureGraphAssemblySample> assemblySamples;

    StructureGraphCompilation(Builder builder) {
        graph = Objects.requireNonNull(builder.graph);
        diagnostics = Collections.unmodifiableList(new ArrayList<>(builder.diagnostics));
        assemblySamples = Collections.unmodifiableList(new ArrayList<>(builder.assemblySamples));
    }

    static Builder builder(CompiledStructureGraph graph) {
        return new Builder(graph);
    }

    public CompiledStructureGraph getGraph() {
        return graph;
    }

    public List<StructureGraphDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public List<StructureGraphAssemblySample> getAssemblySamples() {
        return assemblySamples;
    }

    public boolean hasErrors() {
        for (StructureGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == StructureGraphDiagnostic.Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    public boolean isAssemblyViable() {
        if (hasErrors() || assemblySamples.isEmpty()) {
            return false;
        }
        for (StructureGraphAssemblySample sample : assemblySamples) {
            if (!sample.outcome().isComplete()) {
                return false;
            }
        }
        return true;
    }

    public boolean guaranteesAssemblyOutput() {
        if (!isAssemblyViable()) {
            return false;
        }
        String startPoolKey = graph.getStructure().getStartPool();
        if (startPoolKey == null || startPoolKey.isBlank()) {
            return false;
        }
        IrisJigsawPool startPool = graph.getPools().get(startPoolKey.trim());
        if (startPool == null || startPool.getPieces() == null || startPool.getPieces().isEmpty()) {
            return false;
        }
        for (String theme : themes()) {
            if (!startPoolGuaranteesOutput(startPool, theme)
                    || !reachableBranchesCanTerminate(theme)) {
                return false;
            }
        }
        return true;
    }

    private boolean startPoolGuaranteesOutput(IrisJigsawPool startPool, String theme) {
        boolean certainPhysicalEntry = false;
        for (IrisJigsawPieceEntry entry : startPool.getPieces()) {
            if (entry == null || entry.getWeight() <= 0 || !Double.isFinite(entry.getChance())
                    || entry.getChance() <= 0D || entry.getChance() > 1D) {
                continue;
            }
            if (entry.isEmpty()) {
                return false;
            }
            IrisJigsawPiece piece = graph.getPieces().get(normalize(entry.getPiece()));
            if (piece == null || !JigsawPoolSelection.pieceEligible(piece, theme, 0, 0)) {
                continue;
            }
            IrisObject object = graph.getObjects().get(normalize(piece.getObject()));
            if (object == null || !fitsStartRadius(object)) {
                return false;
            }
            if (entry.getChance() == 1D) {
                certainPhysicalEntry = true;
            }
        }
        return certainPhysicalEntry;
    }

    private boolean reachableBranchesCanTerminate(String theme) {
        for (String pieceKey : graph.getReachablePieces()) {
            IrisJigsawPiece piece = graph.getPieces().get(pieceKey);
            if (piece == null || piece.getConnectors() == null) {
                return false;
            }
            if (!piece.supportsTheme(theme) || piece.resolvedRules().isTerminal()) {
                continue;
            }
            for (IrisJigsawConnector connector : piece.getConnectors()) {
                if (connector == null) {
                    return false;
                }
                IrisJigsawPool pool = graph.getPools().get(normalize(connector.getPool()));
                if (pool == null) {
                    return false;
                }
                if (pool.requiresFallback(graph.getStructure().isRequireCaps())) {
                    if (!hasRequiredTerminal(pool, theme)) {
                        return false;
                    }
                } else if (graph.getStructure().resolvedBranchFailurePolicy()
                        != IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH
                        && !canTerminateWithoutPlacement(pool)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasRequiredTerminal(IrisJigsawPool pool, String theme) {
        String fallbackKey = JigsawPoolSelection.directFallbackKey(pool);
        if (fallbackKey.isEmpty()) {
            return false;
        }
        IrisJigsawPool fallback = graph.getPools().get(fallbackKey);
        if (fallback == null || fallback.getPieces() == null) {
            return false;
        }
        for (IrisJigsawPieceEntry entry : fallback.getPieces()) {
            if (entry == null || entry.isEmpty() || entry.getWeight() <= 0 || entry.getChance() != 1D) {
                continue;
            }
            IrisJigsawPiece piece = graph.getPieces().get(normalize(entry.getPiece()));
            if (piece != null && piece.supportsTheme(theme) && piece.resolvedRules().isTerminal()) {
                return true;
            }
        }
        return false;
    }

    private boolean canTerminateWithoutPlacement(IrisJigsawPool pool) {
        if (hasEmptyChoice(pool)) {
            return true;
        }
        if (pool == null) {
            return false;
        }
        String fallbackKey = JigsawPoolSelection.directFallbackKey(pool);
        return !fallbackKey.isEmpty() && hasEmptyChoice(graph.getPools().get(fallbackKey));
    }

    private boolean hasEmptyChoice(IrisJigsawPool pool) {
        if (pool == null || pool.getPieces() == null) {
            return false;
        }
        if (pool.getPieces().isEmpty()) {
            return true;
        }
        for (IrisJigsawPieceEntry entry : pool.getPieces()) {
            if (entry != null && entry.getWeight() > 0 && entry.getChance() == 1D && entry.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean fitsStartRadius(IrisObject object) {
        long radius = (long) graph.getStructure().getMaxSizeChunks() * 16L;
        return axisFitsRadius(object.getW(), radius) && axisFitsRadius(object.getD(), radius);
    }

    private List<String> themes() {
        if (graph.getStructure().getThemeSets() == null || graph.getStructure().getThemeSets().isEmpty()) {
            return List.of("");
        }
        List<String> themes = new ArrayList<>(graph.getStructure().getThemeSets().size());
        for (IrisJigsawThemeSet theme : graph.getStructure().getThemeSets()) {
            if (theme != null && theme.getKey() != null && !theme.getKey().isBlank()) {
                themes.add(theme.getKey().trim());
            }
        }
        return List.copyOf(themes);
    }

    private boolean axisFitsRadius(int size, long radius) {
        if (size < 1 || radius < 1L) {
            return false;
        }
        long minimum = -(size / 2L);
        long maximum = minimum + size - 1L;
        return minimum >= -radius && maximum <= radius;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Builder {
        private final CompiledStructureGraph graph;
        private final List<StructureGraphDiagnostic> diagnostics = new ArrayList<>();
        private final List<StructureGraphAssemblySample> assemblySamples = new ArrayList<>();

        private Builder(CompiledStructureGraph graph) {
            this.graph = Objects.requireNonNull(graph);
        }

        Builder diagnostics(List<StructureGraphDiagnostic> values) {
            diagnostics.addAll(values);
            return this;
        }

        Builder assemblySamples(List<StructureGraphAssemblySample> values) {
            assemblySamples.addAll(values);
            return this;
        }

        StructureGraphCompilation build() {
            return new StructureGraphCompilation(this);
        }
    }
}
