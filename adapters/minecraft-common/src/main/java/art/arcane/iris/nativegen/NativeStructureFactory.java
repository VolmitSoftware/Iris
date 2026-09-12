package art.arcane.iris.nativegen;

import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.structure.jigsaw.IrisJigsawConfiguration;
import art.arcane.iris.structure.jigsaw.IrisJigsawHeightmap;
import art.arcane.iris.structure.jigsaw.IrisJigsawLiquidSettings;
import art.arcane.iris.spi.PlatformStructureHooks.JigsawSourceMetadata;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Locale;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

public final class NativeStructureFactory {
    private NativeStructureFactory() {
    }

    public static StructureStart generate(GenerationContext context, Holder<Structure> sourceHolder,
                                          NativeStructureStartPlan plan, int references) {
        Structure source = sourceHolder.value();
        Structure configured = configure(
                context.registryAccess(), source, plan.source().getJigsaw(),
                plan.placement().isUnderground(), plan.baseY());
        Holder<Biome> sourceBiome = source.biomes().stream().findFirst()
                .or(() -> context.biomeSource().possibleBiomes().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("Configured native structure '"
                        + plan.source().getStructure() + "' has no usable generation biome"));
        ChunkGenerator forcedGenerator = new ForcedStructureChunkGenerator(
                context.generator(), sourceBiome);
        StructureStart generated = configured.generate(
                sourceHolder,
                context.levelKey(),
                context.registryAccess(),
                forcedGenerator,
                forcedGenerator.getBiomeSource(),
                context.randomState(),
                context.templateManager(),
                context.levelSeed(),
                new ChunkPos(plan.chunkX(), plan.chunkZ()),
                references,
                context.heightAccessor(),
                context.biomePredicate()
        );
        if (generated == null || !generated.isValid()) {
            return StructureStart.INVALID_START;
        }
        StructureStart positioned;
        if (plan.placement().isUnderground()) {
            positioned = NativeStructureVerticalPlacer.relocateToMinY(
                    generated, source, plan.baseY(), context.heightAccessor());
        } else {
            NativeStructureVerticalPlacer.applyVerticalPlacement(
                    generated,
                    plan.source().getStructure(),
                    0,
                    context.seaLevel(),
                    context.heightAccessor().getMinY(),
                    Math.addExact(context.heightAccessor().getMinY(), context.heightAccessor().getHeight()),
                    false,
                    false,
                    null,
                    context.surfaceHeight());
            positioned = generated;
        }
        if (!positioned.isValid()) {
            return StructureStart.INVALID_START;
        }
        return NativeStructureReferenceEnvelope.wrapForPublication(
                positioned,
                source,
                references,
                plan.placement().resolvedTerrain(),
                plan.source().getStructure()
        );
    }

    public static JigsawSourceMetadata sourceMetadata(RegistryAccess registryAccess,
                                                       StructureTemplateManager templateManager,
                                                       JigsawStructure source) {
        CompoundTag sourceTag = encodeJigsaw(registryAccess, source);
        return new JigsawSourceMetadata(
                sourceDistance(sourceTag, "horizontal"),
                horizontalReferenceExpansion(source),
                NativeStructureTemplatePoolBounds.sourceHorizontalSpan(
                        registryAccess, templateManager, source));
    }

    public static int templatePoolHorizontalSpan(RegistryAccess registryAccess,
                                                 StructureTemplateManager templateManager,
                                                 String templatePoolKey) {
        return NativeStructureTemplatePoolBounds.horizontalSpan(
                registryAccess, templateManager, templatePoolKey);
    }

    public static int jigsawStartPoolHorizontalSpan(RegistryAccess registryAccess,
                                                    StructureTemplateManager templateManager,
                                                    JigsawStructure source,
                                                    String templatePoolKey) {
        return NativeStructureTemplatePoolBounds.horizontalSpan(
                registryAccess, templateManager, source, templatePoolKey);
    }

    static Structure configure(RegistryAccess registryAccess, Structure source,
                               IrisJigsawConfiguration configuration,
                               boolean underground, int baseY) {
        Objects.requireNonNull(source, "Native structure source must not be null");
        if (!(source instanceof JigsawStructure sourceJigsaw)) {
            if (configuration != null) {
                throw new IllegalStateException("Jigsaw options require a registered jigsaw structure, found "
                        + source.getClass().getName());
            }
            return source;
        }
        RegistryOps<Tag> registryOps = RegistryOps.create(NbtOps.INSTANCE, registryAccess);
        CompoundTag structureTag = encodeJigsaw(registryOps, sourceJigsaw);
        CompoundTag configuredTag = structureTag.copy();
        if (underground) {
            CompoundTag height = new CompoundTag();
            height.putInt("absolute", baseY);
            configuredTag.put("start_height", height);
            configuredTag.remove("project_start_to_heightmap");
        }
        applyConfiguration(configuredTag, configuration);
        Structure decoded = Structure.DIRECT_CODEC.parse(registryOps, configuredTag).getOrThrow();
        if (!(decoded instanceof JigsawStructure configured)) {
            throw new IllegalStateException("Configured native jigsaw codec produced "
                    + decoded.getClass().getName());
        }
        return configured;
    }

    private static CompoundTag encodeJigsaw(RegistryAccess registryAccess,
                                             JigsawStructure source) {
        return encodeJigsaw(RegistryOps.create(NbtOps.INSTANCE, registryAccess), source);
    }

    private static CompoundTag encodeJigsaw(RegistryOps<Tag> registryOps,
                                             JigsawStructure source) {
        Tag encoded = Structure.DIRECT_CODEC.encodeStart(registryOps, source).getOrThrow();
        if (!(encoded instanceof CompoundTag structureTag)) {
            throw new IllegalStateException("Native jigsaw codec did not produce a compound");
        }
        return structureTag;
    }

    private static void applyConfiguration(CompoundTag tag, IrisJigsawConfiguration configuration) {
        if (configuration == null) {
            return;
        }
        if (configuration.getStartPool() != null && !configuration.getStartPool().isBlank()) {
            tag.putString("start_pool", configuration.getStartPool().trim());
        }
        if (configuration.getStartJigsawName() != null
                && !configuration.getStartJigsawName().isBlank()) {
            if ("NONE".equalsIgnoreCase(configuration.getStartJigsawName().trim())) {
                tag.remove("start_jigsaw_name");
            } else {
                tag.putString("start_jigsaw_name", configuration.getStartJigsawName().trim());
            }
        }
        if (configuration.getMaxDepth() != null) {
            tag.putInt("size", configuration.getMaxDepth());
        }
        applyDistance(tag, configuration);
        if (configuration.getUseExpansionHack() != null) {
            tag.putBoolean("use_expansion_hack", configuration.getUseExpansionHack());
        }
        applyHeightmap(tag, configuration.getProjectStartToHeightmap());
        applyDimensionPadding(tag, configuration);
        applyLiquidSettings(tag, configuration.getLiquidSettings());
    }

    private static void applyDistance(CompoundTag tag, IrisJigsawConfiguration configuration) {
        Integer horizontal = configuration.getMaxDistanceHorizontal();
        Integer vertical = configuration.getMaxDistanceVertical();
        if (horizontal == null && vertical == null) {
            return;
        }
        int resolvedHorizontal = horizontal == null ? sourceDistance(tag, "horizontal") : horizontal;
        int resolvedVertical = vertical == null ? sourceDistance(tag, "vertical") : vertical;
        if (resolvedHorizontal == resolvedVertical) {
            tag.putInt("max_distance_from_center", resolvedHorizontal);
            return;
        }
        CompoundTag distance = new CompoundTag();
        distance.putInt("horizontal", resolvedHorizontal);
        distance.putInt("vertical", resolvedVertical);
        tag.put("max_distance_from_center", distance);
    }

    static int sourceDistance(CompoundTag tag, String axis) {
        Tag raw = tag.get("max_distance_from_center");
        if (raw instanceof CompoundTag compound) {
            return compound.getIntOr(axis, 128);
        }
        return tag.getIntOr("max_distance_from_center", 128);
    }

    static int horizontalReferenceExpansion(Structure structure) {
        BoundingBox content = new BoundingBox(0, 0, 0, 0, 0, 0);
        BoundingBox adjusted = structure.adjustBoundingBox(content);
        int expansion = Math.max(content.minX() - adjusted.minX(), adjusted.maxX() - content.maxX());
        expansion = Math.max(expansion, content.minZ() - adjusted.minZ());
        return Math.max(0, Math.max(expansion, adjusted.maxZ() - content.maxZ()));
    }

    private static void applyHeightmap(CompoundTag tag, IrisJigsawHeightmap heightmap) {
        if (heightmap == null || heightmap == IrisJigsawHeightmap.SOURCE) {
            return;
        }
        if (heightmap == IrisJigsawHeightmap.NONE) {
            tag.remove("project_start_to_heightmap");
            return;
        }
        tag.putString("project_start_to_heightmap", heightmap.name());
    }

    private static void applyDimensionPadding(CompoundTag tag, IrisJigsawConfiguration configuration) {
        Integer bottom = configuration.getDimensionPaddingBottom();
        Integer top = configuration.getDimensionPaddingTop();
        if (bottom == null && top == null) {
            return;
        }
        int resolvedBottom = bottom == null ? sourcePadding(tag, "bottom") : bottom;
        int resolvedTop = top == null ? sourcePadding(tag, "top") : top;
        if (resolvedBottom == resolvedTop) {
            tag.putInt("dimension_padding", resolvedBottom);
            return;
        }
        CompoundTag padding = new CompoundTag();
        padding.putInt("bottom", resolvedBottom);
        padding.putInt("top", resolvedTop);
        tag.put("dimension_padding", padding);
    }

    private static int sourcePadding(CompoundTag tag, String side) {
        Tag raw = tag.get("dimension_padding");
        if (raw instanceof CompoundTag compound) {
            return compound.getIntOr(side, 0);
        }
        return tag.getIntOr("dimension_padding", 0);
    }

    private static void applyLiquidSettings(CompoundTag tag, IrisJigsawLiquidSettings settings) {
        if (settings == null || settings == IrisJigsawLiquidSettings.SOURCE) {
            return;
        }
        tag.putString("liquid_settings", settings.name().toLowerCase(Locale.ROOT));
    }

    public record GenerationContext(
            RegistryAccess registryAccess,
            ChunkGenerator generator,
            BiomeSource biomeSource,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long levelSeed,
            ResourceKey<Level> levelKey,
            LevelHeightAccessor heightAccessor,
            Predicate<Holder<Biome>> biomePredicate,
            int seaLevel,
            IntBinaryOperator surfaceHeight
    ) {
    }
}
