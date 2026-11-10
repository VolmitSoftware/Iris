/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.framework.StructureAssembler;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.validation.Problem;
import com.volmit.validation.Severity;
import com.volmit.validation.Subject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that the structures of a pack assemble: each one's jigsaw graph is compiled, the compiler's diagnostics
 * become problems, and a viable graph is assembled from every seed of {@link #GEOMETRY_SAMPLE_SEEDS} with collisions
 * and radius checked. What the samples show tells {@link NativeReplacementValidation} which structures always give
 * output and how far their pieces reach above and below the anchor.
 * <p>
 * Structures, pools and pieces are the pack's {@link StructureResources}; objects are read only as far as their
 * bounds, straight from their files. A structure file that fails to load is skipped here: Iris's own structure graph
 * check reports malformed JSON. Every problem concerns the structure's file.
 */
public final class StructureGraphValidation {
    private static final List<Long> GEOMETRY_SAMPLE_SEEDS = List.of(
            0L, 1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L, 144L, 233L, 377L, 610L, 987L);
    private static final int GEOMETRY_SAMPLE_ORIGIN_Y = 64;
    private static final String OBJECTS_FOLDER = "objects";
    private static final String OBJECT_EXTENSION = ".iob";
    private static final String JSON_EXTENSION = ".json";

    private StructureGraphValidation() {
    }

    /** Checks every structure of {@code pack}, whose objects are the files below {@code packFolder}. */
    public static Validation validate(PackResources pack, File packFolder) {
        return validate(pack, packFolder, null);
    }

    /** Checks the structures of {@code pack} whose keys are in {@code activeStructureKeys}, or all when it is null. */
    public static Validation validate(PackResources pack, File packFolder, Set<String> activeStructureKeys) {
        List<Problem> problems = new ArrayList<>();
        Set<String> replacementOutputStructures = new LinkedHashSet<>();
        Map<String, List<SampledVerticalEnvelope>> sampledVerticalEnvelopes = new LinkedHashMap<>();
        PackStructureResolver resolver = new PackStructureResolver(pack, packFolder);
        for (ResourceId id : pack.ids(StructureResources.STRUCTURES)) {
            String structureKey = id.path();
            if (activeStructureKeys != null && !activeStructureKeys.contains(structureKey)) {
                continue;
            }
            IrisStructure structure = StructureResources.structure(pack, structureKey);
            if (structure == null) {
                continue;
            }
            validateStructure(structureKey, structure, resolver, problems, replacementOutputStructures, sampledVerticalEnvelopes);
        }
        return new Validation(problems, replacementOutputStructures, sampledVerticalEnvelopes);
    }

    private static void validateStructure(
            String structureKey,
            IrisStructure structure,
            StructureGraphResolver resolver,
            List<Problem> problems,
            Set<String> replacementOutputStructures,
            Map<String, List<SampledVerticalEnvelope>> sampledVerticalEnvelopes
    ) {
        Subject file = Subject.file(StructureResources.STRUCTURES.folder() + "/" + structureKey + JSON_EXTENSION);
        StructureGraphCompilation compilation;
        try {
            compilation = StructureGraphCompiler.compile(structureKey, structure, resolver);
        } catch (MalformedObjectResourceException e) {
            problems.add(Problem.error(file, e.getMessage()));
            return;
        }
        for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
            problems.add(diagnostic.severity() == StructureGraphDiagnostic.Severity.ERROR
                    ? Problem.error(file, diagnostic.message())
                    : Problem.warning(file, diagnostic.message()));
        }
        if (!compilation.isAssemblyViable()) {
            problems.add(Problem.error(file, "Structure '" + structureKey
                    + "' does not produce a complete deterministic assembly and will not generate"
                    + failedSampleSummary(compilation) + "."));
            return;
        }
        RuntimeGeometryValidation geometry = validateRuntimeGeometry(compilation);
        if (!geometry.failures().isEmpty()) {
            problems.add(Problem.error(file, "Structure '" + structureKey
                    + "' fails sampled collision- and radius-aware assembly: "
                    + String.join("; ", geometry.failures()) + "."));
            return;
        }
        sampledVerticalEnvelopes.put(structureKey, geometry.sampledVerticalEnvelopes());
        if (compilation.guaranteesAssemblyOutput() && geometry.outputForEverySample()) {
            replacementOutputStructures.add(structureKey);
        }
    }

    private static RuntimeGeometryValidation validateRuntimeGeometry(
            StructureGraphCompilation compilation
    ) {
        List<String> failures = new ArrayList<>();
        List<SampledVerticalEnvelope> sampledVerticalEnvelopes = new ArrayList<>();
        boolean outputForEverySample = true;
        for (long seed : GEOMETRY_SAMPLE_SEEDS) {
            try {
                StructureAssembler assembler = StructureAssembler.forCompilation(
                        compilation, new BlockPosition(0, GEOMETRY_SAMPLE_ORIGIN_Y, 0));
                StructureAssemblyResult result = assembler.assemble(new RNG(seed));
                if (result.status().isFailure()) {
                    failures.add("seed " + seed + " returned " + result.status() + ": " + result.detail());
                } else if (!result.hasOutput()) {
                    outputForEverySample = false;
                } else {
                    sampledVerticalEnvelopes.add(sampleVerticalEnvelope(seed, result.pieces()));
                }
            } catch (RuntimeException e) {
                failures.add("seed " + seed + " threw " + e.getClass().getSimpleName()
                        + ": " + failureMessage(e));
            }
        }
        return new RuntimeGeometryValidation(
                List.copyOf(failures), outputForEverySample, List.copyOf(sampledVerticalEnvelopes));
    }

    private static SampledVerticalEnvelope sampleVerticalEnvelope(
            long seed,
            List<PlacedStructurePiece> pieces
    ) {
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        for (PlacedStructurePiece piece : pieces) {
            minimumY = Math.min(minimumY, piece.getMinY());
            maximumY = Math.max(maximumY, piece.getMaxY());
        }
        return new SampledVerticalEnvelope(
                seed,
                pieces.size(),
                Math.subtractExact(minimumY, GEOMETRY_SAMPLE_ORIGIN_Y),
                Math.subtractExact(maximumY, GEOMETRY_SAMPLE_ORIGIN_Y));
    }

    private static Map<String, List<SampledVerticalEnvelope>> immutableEnvelopes(
            Map<String, List<SampledVerticalEnvelope>> sampledVerticalEnvelopes
    ) {
        Map<String, List<SampledVerticalEnvelope>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, List<SampledVerticalEnvelope>> entry : sampledVerticalEnvelopes.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private static String failureMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "no failure detail" : exception.getMessage();
    }

    private static String failedSampleSummary(StructureGraphCompilation compilation) {
        List<String> failures = new ArrayList<>();
        for (StructureGraphAssemblySample sample : compilation.getAssemblySamples()) {
            if (sample.outcome().isComplete()) {
                continue;
            }
            failures.add("seed " + sample.seed() + " placed " + sample.outcome().pieceKeys().size()
                    + " piece(s), status=" + sample.outcome().status()
                    + ": " + sample.outcome().detail());
            if (failures.size() == 3) {
                break;
            }
        }
        return failures.isEmpty() ? "" : " (" + String.join("; ", failures) + ")";
    }

    /**
     * What the check found: its problems, the structures that gave output for every sample of a graph that
     * guarantees output (the only ones that may replace a vanilla structure), and each viable structure's sampled
     * vertical envelopes.
     */
    public record Validation(
            List<Problem> problems,
            Set<String> replacementOutputStructures,
            Map<String, List<SampledVerticalEnvelope>> sampledVerticalEnvelopes
    ) {
        public Validation {
            problems = List.copyOf(problems);
            replacementOutputStructures = Set.copyOf(replacementOutputStructures);
            sampledVerticalEnvelopes = immutableEnvelopes(sampledVerticalEnvelopes);
        }

        /** The messages of the problems that stop the pack from loading. */
        public List<String> errors() {
            return messages(Severity.ERROR);
        }

        /** The messages of the problems that are only shown. */
        public List<String> warnings() {
            return messages(Severity.WARNING);
        }

        private List<String> messages(Severity severity) {
            return problems.stream().filter(problem -> problem.severity() == severity).map(Problem::message).distinct().toList();
        }
    }

    /** Where one sampled assembly's pieces reach, relative to its anchor. */
    public record SampledVerticalEnvelope(long seed, int pieceCount, int minimumYOffset, int maximumYOffset) {
        public SampledVerticalEnvelope {
            if (pieceCount <= 0) {
                throw new IllegalArgumentException("Sampled vertical envelope must contain at least one piece");
            }
            if (maximumYOffset < minimumYOffset) {
                throw new IllegalArgumentException("Sampled vertical envelope maximum Y cannot be below minimum Y");
            }
        }
    }

    private record RuntimeGeometryValidation(
            List<String> failures,
            boolean outputForEverySample,
            List<SampledVerticalEnvelope> sampledVerticalEnvelopes
    ) {
    }

    /** Pools and pieces from the pack's structure resources; objects as far as their bounds, read from their files once each. */
    private static final class PackStructureResolver implements StructureGraphResolver {
        private final PackResources pack;
        private final Path objectsRoot;
        private final Map<String, ObjectFrame> objects = new HashMap<>();
        private final Set<String> missingObjects = new LinkedHashSet<>();

        private PackStructureResolver(PackResources pack, File packFolder) {
            this.pack = pack;
            this.objectsRoot = packFolder.toPath().toAbsolutePath().normalize().resolve(OBJECTS_FOLDER);
        }

        @Override
        public IrisJigsawPool loadPool(String key) {
            return StructureResources.pool(pack, key);
        }

        @Override
        public IrisJigsawPiece loadPiece(String key) {
            return StructureResources.piece(pack, key);
        }

        @Override
        public ObjectFrame loadObject(String key) {
            if (objects.containsKey(key)) {
                return objects.get(key);
            }
            if (missingObjects.contains(key)) {
                return null;
            }
            Path file = resolveObject(key);
            ObjectFrame object = file == null ? null : readObjectBounds(file);
            if (object == null) {
                missingObjects.add(key);
                return null;
            }
            objects.put(key, object);
            return object;
        }

        private Path resolveObject(String key) {
            if (key == null || key.isBlank() || key.indexOf('\\') >= 0 || key.indexOf(':') >= 0) {
                return null;
            }
            Path file = objectsRoot.resolve(key + OBJECT_EXTENSION).normalize();
            if (!file.startsWith(objectsRoot) || !Files.isRegularFile(file)) {
                return null;
            }
            return file;
        }

        private ObjectFrame readObjectBounds(Path file) {
            try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file))) {
                return IrisObjectFrameReader.readBounds(input, file.toString());
            } catch (IOException e) {
                throw new MalformedObjectResourceException(e.getMessage(), e);
            }
        }
    }

    private static final class MalformedObjectResourceException extends RuntimeException {
        private MalformedObjectResourceException(String message, IOException cause) {
            super(message, cause);
        }
    }
}
