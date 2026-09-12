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

package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.placement.StructureAssembler;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StructureResourceBundleGraphCompiler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final List<Long> ASSEMBLY_SEEDS = List.of(
            0L, 1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L, 144L, 233L, 377L, 610L, 987L);

    private StructureResourceBundleGraphCompiler() {
    }

    public static List<StructureGraphCompilation> compile(StructureResourceBundle bundle) {
        BundleGraph graph = parse(Objects.requireNonNull(bundle));
        List<StructureGraphCompilation> compilations = new ArrayList<>(graph.structures().size());
        for (IrisStructure structure : graph.structures().values()) {
            compilations.add(StructureGraphCompiler.compile(structure, graph.resolver()));
        }
        return List.copyOf(compilations);
    }

    public static void requireViable(StructureResourceBundle bundle) {
        BundleGraph graph = parse(Objects.requireNonNull(bundle));
        if (graph.structures().isEmpty()) {
            throw new StructureGraphValidationException("Structure bundle does not contain a root structure resource");
        }
        for (IrisStructure structure : graph.structures().values()) {
            StructureGraphCompilation compilation = StructureGraphCompiler.compile(structure, graph.resolver());
            if (!compilation.isAssemblyViable()) {
                throw new StructureGraphValidationException(
                        "Structure bundle graph is not viable: " + viabilityFailure(compilation));
            }
            requireGeometryViable(compilation);
        }
    }

    private static String viabilityFailure(StructureGraphCompilation compilation) {
        for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
            if (diagnostic.severity() == StructureGraphDiagnostic.Severity.ERROR) {
                return diagnostic.message();
            }
        }
        for (StructureGraphAssemblySample sample : compilation.getAssemblySamples()) {
            StructureGraphAssemblySample.Outcome outcome = sample.outcome();
            if (!outcome.isComplete()) {
                return "sampled assembly at seed " + sample.seed() + " returned "
                        + outcome.status() + ": " + outcome.detail();
            }
        }
        return "deterministic assembly did not complete";
    }

    private static BundleGraph parse(StructureResourceBundle activeBundle) {
        Map<String, IrisStructure> structures = new LinkedHashMap<>();
        Map<String, IrisJigsawPool> pools = new LinkedHashMap<>();
        Map<String, IrisJigsawPiece> pieces = new LinkedHashMap<>();
        Map<String, IrisObject> objects = new LinkedHashMap<>();
        for (StructureResourceBundle.Resource resource : activeBundle.resources().values()) {
            String path = resource.relativePath();
            if (matches(path, "structures/", ".json")) {
                IrisStructure structure = readJson(resource, IrisStructure.class);
                String key = key(path, "structures/", ".json");
                structure.setLoadKey(key);
                structures.put(key, structure);
            } else if (matches(path, "jigsaw-pools/", ".json")) {
                pools.put(key(path, "jigsaw-pools/", ".json"), readJson(resource, IrisJigsawPool.class));
            } else if (matches(path, "jigsaw-pieces/", ".json")) {
                pieces.put(key(path, "jigsaw-pieces/", ".json"), readJson(resource, IrisJigsawPiece.class));
            } else if (matches(path, "objects/", ".iob")) {
                objects.put(key(path, "objects/", ".iob"), readObjectBounds(resource));
            }
        }
        return new BundleGraph(structures, new BundleResolver(pools, pieces, objects));
    }

    private static void requireGeometryViable(StructureGraphCompilation compilation) {
        String structureKey = compilation.getGraph().getStructureKey();
        for (long seed : ASSEMBLY_SEEDS) {
            try {
                StructureAssembler assembler = StructureAssembler.forCompilation(
                        compilation, new IrisPosition(0, 64, 0));
                StructureAssemblyResult result = assembler.assemble(new RNG(seed));
                if (result.status().isFailure()) {
                    throw new StructureGraphValidationException("Structure bundle graph '" + structureKey
                            + "' fails sampled runtime geometry assembly at seed " + seed + ": "
                            + result.status() + ": " + result.detail());
                }
            } catch (StructureGraphValidationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IllegalStateException("Structure bundle graph '" + structureKey
                        + "' encountered an unexpected sampled runtime geometry failure at seed " + seed + ": "
                        + e.getClass().getSimpleName() + ": " + failureMessage(e), e);
            }
        }
    }

    private static String failureMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "no failure detail" : exception.getMessage();
    }

    private static boolean matches(String path, String prefix, String suffix) {
        return path.startsWith(prefix) && path.endsWith(suffix) && path.length() > prefix.length() + suffix.length();
    }

    private static String key(String path, String prefix, String suffix) {
        return path.substring(prefix.length(), path.length() - suffix.length());
    }

    private static <T> T readJson(StructureResourceBundle.Resource resource, Class<T> type) {
        T value;
        try {
            value = GSON.fromJson(new String(resource.content(), StandardCharsets.UTF_8), type);
        } catch (RuntimeException e) {
            throw new StructureGraphValidationException("Malformed structure resource " + resource.relativePath(), e);
        }
        if (value == null) {
            throw new StructureGraphValidationException("Empty structure resource " + resource.relativePath());
        }
        return value;
    }

    private static IrisObject readObjectBounds(StructureResourceBundle.Resource resource) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(resource.content())) {
            return IrisObjectFrameReader.readBounds(input, resource.relativePath());
        } catch (IOException e) {
            throw new StructureGraphValidationException(e.getMessage(), e);
        }
    }

    private record BundleResolver(
            Map<String, IrisJigsawPool> pools,
            Map<String, IrisJigsawPiece> pieces,
            Map<String, IrisObject> objects
    ) implements StructureGraphResolver {
        private BundleResolver {
            pools = Map.copyOf(pools);
            pieces = Map.copyOf(pieces);
            objects = Map.copyOf(objects);
        }

        @Override
        public IrisJigsawPool loadPool(String key) {
            return pools.get(key);
        }

        @Override
        public IrisJigsawPiece loadPiece(String key) {
            return pieces.get(key);
        }

        @Override
        public IrisObject loadObject(String key) {
            return objects.get(key);
        }
    }

    private record BundleGraph(Map<String, IrisStructure> structures, BundleResolver resolver) {
        private BundleGraph {
            structures = Map.copyOf(structures);
            Objects.requireNonNull(resolver);
        }
    }
}
