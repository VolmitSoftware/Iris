package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.pack.ContentKeyValidator;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.LegacyBlockRenames;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BlockStateResolver {
    private final IrisData data;
    private final PlatformRegistries registries;
    private final Set<String> knownBlocks;
    private final List<ExportDiagnostic> diagnostics;
    private final Map<String, Resolution> resolutions = new HashMap<>();

    public BlockStateResolver(IrisData data, PlatformRegistries registries, List<ExportDiagnostic> diagnostics) {
        this.data = Objects.requireNonNull(data);
        this.registries = registries;
        this.knownBlocks = registries == null ? Set.of() : knownBlocks(registries.blockTypeKeys());
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public static BlockStateResolver forContext(PackCompileContext context) {
        PlatformRegistries registries = IrisPlatforms.isBound() ? IrisPlatforms.get().registries() : null;
        return new BlockStateResolver(context.data(), registries, context.diagnostics());
    }

    public Optional<VanillaBlockState> resolve(IrisBlockData block, String resource) {
        if (block == null || block.getBlock() == null || block.getBlock().isBlank()) {
            return Optional.empty();
        }
        return resolve(stateKey(block), resource);
    }

    public Optional<VanillaBlockState> resolve(String rawState, String resource) {
        String normalized = ContentGate.normalizeState(rawState);
        if (normalized == null) {
            return Optional.empty();
        }
        Resolution resolution = resolutions.computeIfAbsent(normalized, this::parse);
        if (resolution.problem() != null) {
            ExportDiagnostic diagnostic = new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.BLOCK_STATE, resource, resolution.problem());
            if (!diagnostics.contains(diagnostic)) {
                diagnostics.add(diagnostic);
            }
        }
        return Optional.ofNullable(resolution.state());
    }

    private String stateKey(IrisBlockData block) {
        IrisBlockData custom = data.getBlockLoader() == null ? null : data.getBlockLoader().load(block.getBlock(), false);
        if (custom == null || custom.getBlock() == null || custom.getBlock().equals(block.getBlock())) {
            return block.stateKey();
        }
        IrisBlockData merged = IrisBlockData.from(custom.stateKey());
        merged.getData().putAll(block.getData());
        return merged.stateKey();
    }

    private Resolution parse(String normalized) {
        String renamed = renamed(normalized);
        if (!renamed.startsWith("minecraft:")) {
            return new Resolution(null, "Block '" + renamed + "' is not a Minecraft block and is not exported");
        }
        String problem = null;
        String validated = renamed;
        List<String> propertyProblems = registries == null ? List.of() : ContentKeyValidator.validateBlockStateProperties(registries, List.of(renamed));
        if (!propertyProblems.isEmpty()) {
            validated = ContentGate.baseKey(renamed);
            problem = String.join("; ", propertyProblems) + "; the block exports in its default state";
        }
        try {
            return new Resolution(VanillaBlockState.parse(validated), problem);
        } catch (IllegalArgumentException exception) {
            return new Resolution(null, "Block state '" + renamed + "' is not exported: " + exception.getMessage());
        }
    }

    private String renamed(String normalized) {
        if (knownBlocks.isEmpty() || knownBlocks.contains(ContentGate.baseKey(normalized))) {
            return normalized;
        }
        String properties = normalized.substring(ContentGate.baseKey(normalized).length());
        String current = normalized;
        for (int hop = 0; hop < LegacyBlockRenames.MAX_HOPS; hop++) {
            String supplement = LegacyBlockRenames.supplementFor(current);
            if (supplement == null) {
                return normalized;
            }
            String next = ContentGate.normalizeState(supplement);
            if (next == null) {
                return normalized;
            }
            if (knownBlocks.contains(ContentGate.baseKey(next))) {
                return ContentGate.baseKey(next) + properties;
            }
            current = next;
        }
        return normalized;
    }

    private static Set<String> knownBlocks(List<String> keys) {
        Set<String> known = new HashSet<>(keys.size());
        for (String key : keys) {
            String normalized = ContentGate.normalizeState(key);
            if (normalized != null) {
                known.add(ContentGate.baseKey(normalized));
            }
        }
        return known;
    }

    private record Resolution(VanillaBlockState state, String problem) {
    }
}
