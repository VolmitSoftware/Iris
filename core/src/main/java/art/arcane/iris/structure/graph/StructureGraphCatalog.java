package art.arcane.iris.structure.graph;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.placement.StructureAssembler;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class StructureGraphCatalog {
    private static final Cache<IrisData, ConcurrentMap<String, StructureGraphCompilation>> CACHE =
            Caffeine.newBuilder().weakKeys().build();
    private static final Cache<IrisData, ConcurrentMap<String, Boolean>> RUNTIME_OUTPUT =
            Caffeine.newBuilder().weakKeys().build();
    private static final List<Long> RUNTIME_SAMPLE_SEEDS = List.of(
            0L, 1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L, 144L, 233L, 377L, 610L, 987L);

    private StructureGraphCatalog() {
    }

    public static StructureGraphCompilation compile(IrisData data, IrisStructure structure) {
        IrisData activeData = Objects.requireNonNull(data);
        IrisStructure activeStructure = Objects.requireNonNull(structure);
        String structureKey = activeStructure.getLoadKey();
        if (structureKey == null || structureKey.isBlank()) {
            return compileAndReport(activeData, activeStructure);
        }
        ConcurrentMap<String, StructureGraphCompilation> catalog = CACHE.get(
                activeData, ignored -> new ConcurrentHashMap<>());
        return catalog.computeIfAbsent(structureKey, ignored -> compileAndReport(activeData, activeStructure));
    }

    public static void invalidate(IrisData data) {
        if (data != null) {
            CACHE.invalidate(data);
            RUNTIME_OUTPUT.invalidate(data);
        }
    }

    public static boolean guaranteesRuntimeOutput(IrisData data, IrisStructure structure) {
        IrisData activeData = Objects.requireNonNull(data);
        IrisStructure activeStructure = Objects.requireNonNull(structure);
        if (!compile(activeData, activeStructure).guaranteesAssemblyOutput()) {
            return false;
        }
        String structureKey = activeStructure.getLoadKey();
        if (structureKey == null || structureKey.isBlank()) {
            return sampleRuntimeAssembly(activeData, activeStructure);
        }
        ConcurrentMap<String, Boolean> catalog = RUNTIME_OUTPUT.get(
                activeData, ignored -> new ConcurrentHashMap<>());
        return catalog.computeIfAbsent(
                structureKey, ignored -> sampleRuntimeAssembly(activeData, activeStructure));
    }

    private static StructureGraphCompilation compileAndReport(IrisData data, IrisStructure structure) {
        StructureGraphCompilation compilation = StructureGraphCompiler.compile(
                structure, StructureGraphResolver.forData(data));
        String structureKey = structure.getLoadKey() == null || structure.getLoadKey().isBlank()
                ? "<unkeyed>" : structure.getLoadKey();
        for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
            String message = "[StructureGraph:" + structureKey + "] " + diagnostic.message();
            if (diagnostic.severity() == StructureGraphDiagnostic.Severity.ERROR) {
                IrisLogging.error(message);
            } else {
                IrisLogging.warn(message);
            }
        }
        if (!compilation.isAssemblyViable()) {
            IrisLogging.error("[StructureGraph:" + structureKey
                    + "] Graph rejected because deterministic assembly did not complete.");
        }
        return compilation;
    }

    private static boolean sampleRuntimeAssembly(IrisData data, IrisStructure structure) {
        for (long seed : RUNTIME_SAMPLE_SEEDS) {
            try {
                StructureAssembler assembler = StructureAssembler.forData(
                        data, structure, new IrisPosition(0, 64, 0));
                StructureAssemblyResult result = assembler.assemble(new RNG(seed));
                if (!result.hasOutput()) {
                    return false;
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                return false;
            }
        }
        return true;
    }
}
