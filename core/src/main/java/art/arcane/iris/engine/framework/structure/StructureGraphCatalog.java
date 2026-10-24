package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.framework.StructureAssembler;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.volmlib.util.math.RNG;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.volmit.ModuleLog;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Compiled structure graphs, kept per resource snapshot of the pack: every reload of the pack's resources starts a new
 * snapshot ({@link IrisData#getPackResources()}), so nothing stale is ever read and nothing is invalidated by hand.
 */
public final class StructureGraphCatalog {
    private static final Cache<IrisPackResources, ConcurrentMap<String, StructureGraphCompilation>> CACHE =
            Caffeine.newBuilder().weakKeys().build();
    private static final Cache<IrisPackResources, ConcurrentMap<String, Boolean>> RUNTIME_OUTPUT =
            Caffeine.newBuilder().weakKeys().build();
    private static final List<Long> RUNTIME_SAMPLE_SEEDS = List.of(
            0L, 1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L, 144L, 233L, 377L, 610L, 987L);

    private StructureGraphCatalog() {
    }

    public static StructureGraphCompilation compile(IrisData data, IrisStructure structure, ModuleLog log) {
        IrisData activeData = Objects.requireNonNull(data);
        IrisStructure activeStructure = Objects.requireNonNull(structure);
        String structureKey = activeStructure.getLoadKey();
        if (structureKey == null || structureKey.isBlank()) {
            return compileAndReport(activeData, activeStructure, log);
        }
        ConcurrentMap<String, StructureGraphCompilation> catalog = CACHE.get(
                activeData.getPackResources(), ignored -> new ConcurrentHashMap<>());
        return catalog.computeIfAbsent(structureKey, ignored -> compileAndReport(activeData, activeStructure, log));
    }

    public static boolean guaranteesRuntimeOutput(IrisData data, IrisStructure structure, ModuleLog log) {
        IrisData activeData = Objects.requireNonNull(data);
        IrisStructure activeStructure = Objects.requireNonNull(structure);
        if (!compile(activeData, activeStructure, log).guaranteesAssemblyOutput()) {
            return false;
        }
        String structureKey = activeStructure.getLoadKey();
        if (structureKey == null || structureKey.isBlank()) {
            return sampleRuntimeAssembly(activeData, activeStructure, log);
        }
        ConcurrentMap<String, Boolean> catalog = RUNTIME_OUTPUT.get(
                activeData.getPackResources(), ignored -> new ConcurrentHashMap<>());
        return catalog.computeIfAbsent(
                structureKey, ignored -> sampleRuntimeAssembly(activeData, activeStructure, log));
    }

    private static StructureGraphCompilation compileAndReport(IrisData data, IrisStructure structure, ModuleLog log) {
        StructureGraphCompilation compilation = StructureGraphCompiler.compile(
                structure, StructureGraphResolver.forData(data));
        String structureKey = structure.getLoadKey() == null || structure.getLoadKey().isBlank()
                ? "<unkeyed>" : structure.getLoadKey();
        for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
            String message = "[StructureGraph:" + structureKey + "] " + diagnostic.message();
            if (diagnostic.severity() == StructureGraphDiagnostic.Severity.ERROR) {
                log.error(message, null);
            } else {
                log.warn(message);
            }
        }
        if (!compilation.isAssemblyViable()) {
            log.error("[StructureGraph:" + structureKey
                    + "] Graph rejected because deterministic assembly did not complete.", null);
        }
        return compilation;
    }

    private static boolean sampleRuntimeAssembly(IrisData data, IrisStructure structure, ModuleLog log) {
        for (long seed : RUNTIME_SAMPLE_SEEDS) {
            try {
                StructureAssembler assembler = StructureAssembler.forData(
                        data, structure, new IrisPosition(0, 64, 0), log);
                StructureAssemblyResult result = assembler.assemble(new RNG(seed));
                if (!result.hasOutput()) {
                    return false;
                }
            } catch (Throwable e) {
                log.error("Failed to sample the runtime assembly of a structure", e);
                return false;
            }
        }
        return true;
    }
}
