package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.framework.StructureAssembler;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.PackResources;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Compiled structure graphs, kept per resource snapshot of the pack: every reload of the pack's resources starts a new
 * snapshot ({@link PackResources#snapshotId()}), so nothing stale is ever read and nothing is invalidated by hand.
 */
public final class StructureGraphCatalog {
    private static final Map<PackResources, ConcurrentMap<String, StructureGraphCompilation>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<PackResources, ConcurrentMap<String, Boolean>> RUNTIME_OUTPUT =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final BlockPosition SAMPLE_ORIGIN = new BlockPosition(0, 64, 0);
    private static final List<Long> RUNTIME_SAMPLE_SEEDS = List.of(
            0L, 1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L, 144L, 233L, 377L, 610L, 987L);

    private StructureGraphCatalog() {
    }

    /** The compilation of {@code structure}, the pack's structure {@code structureKey} (blank for one that is not in the pack, which is not kept). */
    public static StructureGraphCompilation compile(PackResources pack, String structureKey, IrisStructure structure, ModuleLog log) {
        PackResources activePack = Objects.requireNonNull(pack);
        IrisStructure activeStructure = Objects.requireNonNull(structure);
        if (structureKey == null || structureKey.isBlank()) {
            return compileAndReport(activePack, structureKey, activeStructure, log);
        }
        ConcurrentMap<String, StructureGraphCompilation> catalog = CACHE.computeIfAbsent(
                activePack, ignored -> new ConcurrentHashMap<>());
        return catalog.computeIfAbsent(structureKey, ignored -> compileAndReport(activePack, structureKey, activeStructure, log));
    }

    public static boolean guaranteesRuntimeOutput(PackResources pack, String structureKey, IrisStructure structure, ModuleLog log) {
        PackResources activePack = Objects.requireNonNull(pack);
        IrisStructure activeStructure = Objects.requireNonNull(structure);
        if (!compile(activePack, structureKey, activeStructure, log).guaranteesAssemblyOutput()) {
            return false;
        }
        if (structureKey == null || structureKey.isBlank()) {
            return sampleRuntimeAssembly(activePack, structureKey, activeStructure, log);
        }
        ConcurrentMap<String, Boolean> catalog = RUNTIME_OUTPUT.computeIfAbsent(
                activePack, ignored -> new ConcurrentHashMap<>());
        return catalog.computeIfAbsent(
                structureKey, ignored -> sampleRuntimeAssembly(activePack, structureKey, activeStructure, log));
    }

    private static StructureGraphCompilation compileAndReport(PackResources pack, String structureKey, IrisStructure structure, ModuleLog log) {
        StructureGraphCompilation compilation = StructureGraphCompiler.compile(
                structureKey, structure, StructureGraphResolver.forPack(pack));
        String reportedKey = structureKey == null || structureKey.isBlank() ? "<unkeyed>" : structureKey;
        for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
            String message = "[StructureGraph:" + reportedKey + "] " + diagnostic.message();
            if (diagnostic.severity() == StructureGraphDiagnostic.Severity.ERROR) {
                log.error(message, null);
            } else {
                log.warn(message);
            }
        }
        if (!compilation.isAssemblyViable()) {
            log.error("[StructureGraph:" + reportedKey
                    + "] Graph rejected because deterministic assembly did not complete.", null);
        }
        return compilation;
    }

    private static boolean sampleRuntimeAssembly(PackResources pack, String structureKey, IrisStructure structure, ModuleLog log) {
        for (long seed : RUNTIME_SAMPLE_SEEDS) {
            try {
                StructureAssembler assembler = StructureAssembler.forPack(pack, structureKey, structure, SAMPLE_ORIGIN, log);
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
