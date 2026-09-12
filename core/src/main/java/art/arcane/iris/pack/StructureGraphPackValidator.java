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

package art.arcane.iris.pack;

import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.StructureAssembler;
import art.arcane.iris.structure.graph.IrisObjectFrameReader;
import art.arcane.iris.structure.graph.StructureAssemblyResult;
import art.arcane.iris.structure.graph.StructureGraphCompilation;
import art.arcane.iris.structure.graph.StructureGraphCompiler;
import art.arcane.iris.structure.graph.StructureGraphDiagnostic;
import art.arcane.iris.structure.graph.StructureGraphAssemblySample;
import art.arcane.iris.structure.graph.StructureGraphResolver;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class StructureGraphPackValidator {
    private static final Gson GSON = new GsonBuilder().create();
    private static final List<Long> GEOMETRY_SAMPLE_SEEDS = List.of(
            0L, 1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L, 144L, 233L, 377L, 610L, 987L);
    private static final int GEOMETRY_SAMPLE_ORIGIN_Y = 64;

    private StructureGraphPackValidator() {
    }

    static Validation validate(Path packRoot) {
        return validate(packRoot, null);
    }

    static Validation validate(Path packRoot, Set<String> activeStructureKeys) {
        Path normalizedRoot = packRoot.toAbsolutePath().normalize();
        Path structuresRoot = normalizedRoot.resolve("structures");
        Set<String> errors = new LinkedHashSet<>();
        Set<String> warnings = new LinkedHashSet<>();
        Set<String> replacementOutputStructures = new LinkedHashSet<>();
        Map<String, List<SampledVerticalEnvelope>> sampledVerticalEnvelopes = new LinkedHashMap<>();
        FileResolver resolver = new FileResolver(normalizedRoot);
        for (Path structureFile : listJsonFiles(structuresRoot)) {
            String structureKey = resourceKey(structuresRoot, structureFile, ".json");
            if (structureKey.equals("structure-index")) {
                continue;
            }
            IrisStructure structure = readJson(structureFile, IrisStructure.class);
            if (structure == null) {
                continue;
            }
            structure.setLoadKey(structureKey);
            if (activeStructureKeys != null && !activeStructureKeys.contains(structureKey)) {
                continue;
            }
            StructureGraphCompilation compilation;
            try {
                compilation = StructureGraphCompiler.compile(structure, resolver);
            } catch (MalformedObjectResourceException e) {
                errors.add(e.getMessage());
                continue;
            }
            for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
                if (diagnostic.severity() == StructureGraphDiagnostic.Severity.ERROR) {
                    errors.add(diagnostic.message());
                } else {
                    warnings.add(diagnostic.message());
                }
            }
            if (!compilation.isAssemblyViable()) {
                errors.add("Structure '" + structureKey
                        + "' does not produce a complete deterministic assembly and will not generate"
                        + failedSampleSummary(compilation) + ".");
                continue;
            }
            RuntimeGeometryValidation geometry = validateRuntimeGeometry(compilation);
            if (geometry.failures().isEmpty()) {
                sampledVerticalEnvelopes.put(structureKey, geometry.sampledVerticalEnvelopes());
                if (compilation.guaranteesAssemblyOutput() && geometry.outputForEverySample()) {
                    replacementOutputStructures.add(structureKey);
                }
            } else {
                errors.add("Structure '" + structureKey
                        + "' fails sampled collision- and radius-aware assembly: "
                        + String.join("; ", geometry.failures()) + ".");
            }
        }
        return new Validation(
                List.copyOf(errors), List.copyOf(warnings), Set.copyOf(replacementOutputStructures),
                immutableEnvelopes(sampledVerticalEnvelopes));
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
                        compilation, new IrisPosition(0, GEOMETRY_SAMPLE_ORIGIN_Y, 0));
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

    private static List<Path> listJsonFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String resourceKey(Path root, Path file, String extension) {
        String relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
        return relative.substring(0, relative.length() - extension.length());
    }

    private static <T> T readJson(Path file, Class<T> type) {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    record Validation(
            List<String> errors,
            List<String> warnings,
            Set<String> replacementOutputStructures,
            Map<String, List<SampledVerticalEnvelope>> sampledVerticalEnvelopes
    ) {
        Validation {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
            replacementOutputStructures = Set.copyOf(replacementOutputStructures);
            sampledVerticalEnvelopes = immutableEnvelopes(sampledVerticalEnvelopes);
        }
    }

    record SampledVerticalEnvelope(long seed, int pieceCount, int minimumYOffset, int maximumYOffset) {
        SampledVerticalEnvelope {
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

    private static final class FileResolver implements StructureGraphResolver {
        private final Path packRoot;
        private final Map<String, IrisJigsawPool> pools = new HashMap<>();
        private final Map<String, IrisJigsawPiece> pieces = new HashMap<>();
        private final Map<String, IrisObject> objects = new HashMap<>();
        private final Set<String> missingPools = new LinkedHashSet<>();
        private final Set<String> missingPieces = new LinkedHashSet<>();
        private final Set<String> missingObjects = new LinkedHashSet<>();

        private FileResolver(Path packRoot) {
            this.packRoot = packRoot;
        }

        @Override
        public IrisJigsawPool loadPool(String key) {
            if (pools.containsKey(key)) {
                return pools.get(key);
            }
            if (missingPools.contains(key)) {
                return null;
            }
            Path file = resolve("jigsaw-pools", key, ".json");
            IrisJigsawPool pool = file == null ? null : readJson(file, IrisJigsawPool.class);
            if (pool == null) {
                missingPools.add(key);
                return null;
            }
            pools.put(key, pool);
            return pool;
        }

        @Override
        public IrisJigsawPiece loadPiece(String key) {
            if (pieces.containsKey(key)) {
                return pieces.get(key);
            }
            if (missingPieces.contains(key)) {
                return null;
            }
            Path file = resolve("jigsaw-pieces", key, ".json");
            IrisJigsawPiece piece = file == null ? null : readJson(file, IrisJigsawPiece.class);
            if (piece == null) {
                missingPieces.add(key);
                return null;
            }
            pieces.put(key, piece);
            return piece;
        }

        @Override
        public IrisObject loadObject(String key) {
            if (objects.containsKey(key)) {
                return objects.get(key);
            }
            if (missingObjects.contains(key)) {
                return null;
            }
            Path file = resolve("objects", key, ".iob");
            IrisObject object = file == null ? null : readObjectBounds(file);
            if (object == null) {
                missingObjects.add(key);
                return null;
            }
            objects.put(key, object);
            return object;
        }

        private Path resolve(String folder, String key, String extension) {
            if (key == null || key.isBlank() || key.indexOf('\\') >= 0 || key.indexOf(':') >= 0) {
                return null;
            }
            Path root = packRoot.resolve(folder).normalize();
            Path file = root.resolve(key + extension).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                return null;
            }
            return file;
        }

        private IrisObject readObjectBounds(Path file) {
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
