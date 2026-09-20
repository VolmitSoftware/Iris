package art.arcane.iris.nativegen.v26_3_R1;

import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureTemplateOccupancy;

import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureReflection;

import com.mojang.datafixers.util.Either;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlackstoneReplaceProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockAgeProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LavaSubmergedBlockProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.NopProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructureTemplateOccupancyTest {
    private static final BoundingBox PROCESSING_AREA = new BoundingBox(0, 56, 0, 15, 80, 0);
    private static final int TEMPLATE_WIDTH = 32;
    private static final int TERRAIN_HEIGHT = 64;

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void straddlingGravityPieceHeightReadsHonorTheVanillaClipContract() throws Exception {
        ColumnRecorder recorder = new ColumnRecorder();

        resolve(straddlingPiece(
                List.of(new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, 0))), recorder);

        // 26.2 vanilla clips processor evaluation to the placement bounding box; 26.1.2 vanilla
        // runs processors across the whole piece. Iris mirrors the pinned version's semantics.
        assertEquals(expectedClippedColumns(), recorder.heightColumns);
    }

    @Test
    public void cappedStraddlingPieceRunsProcessorsAcrossTheWholePiece() throws Exception {
        ColumnRecorder recorder = new ColumnRecorder();

        resolve(straddlingPiece(List.of(
                new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, 0),
                new CappedProcessor(NopProcessor.INSTANCE, ConstantInt.of(0)))), recorder);

        assertEquals(columns(0, TEMPLATE_WIDTH - 1), recorder.heightColumns);
    }

    @Test
    public void cappedProcessorIsTheOnlyProcessorThatDisablesTheProcessingAreaClip() throws Exception {
        Method contract = clipContractMethod();
        Assume.assumeTrue("evaluatesEntirePieceState only exists on 26.2+", contract != null);

        for (StructureProcessor processor : List.of(
                BlockIgnoreProcessor.STRUCTURE_BLOCK,
                JigsawReplacementProcessor.INSTANCE,
                LavaSubmergedBlockProcessor.INSTANCE,
                NopProcessor.INSTANCE,
                BlackstoneReplaceProcessor.INSTANCE,
                new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, 0),
                new RuleProcessor(List.of()),
                new BlockAgeProcessor(0.5F),
                protectedBlockProcessor())) {
            assertFalse(processor.getClass().getName(),
                    (Boolean) contract.invoke(processor));
        }
        assertTrue((Boolean) contract.invoke(
                new CappedProcessor(NopProcessor.INSTANCE, ConstantInt.of(4))));
    }

    @Test
    public void placementSettingsCarryTheProcessingAreaAsTheirClipBox() throws Exception {
        PoolElementStructurePiece piece = straddlingPiece(List.of());

        StructurePlaceSettings settings = NativeStructureReflection.resolvePlacementSettings(
                (SinglePoolElement) piece.getElement(), piece, PROCESSING_AREA);

        assertEquals(PROCESSING_AREA, settings.getBoundingBox());
    }

    private static void resolve(PoolElementStructurePiece piece, ColumnRecorder recorder) {
        NativeStructureTemplateOccupancy.resolve(
                world(recorder), piece, piece.getPosition(), PROCESSING_AREA,
                NativeStructureTemplateOccupancyTest::forbiddenTemplateManager,
                PROCESSING_AREA::isInside, amount -> {
                });
    }

    private static PoolElementStructurePiece straddlingPiece(
            List<StructureProcessor> processors) throws Exception {
        BoundingBox bounds = new BoundingBox(0, 70, 0, TEMPLATE_WIDTH - 1, 76, 0);
        return new PoolElementStructurePiece(
                null, new InlineSinglePoolElement(template(), processors),
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                1, Rotation.NONE, bounds, LiquidSettings.APPLY_WATERLOGGING);
    }

    private static Set<Integer> columns(int minimumX, int maximumX) {
        Set<Integer> columns = new TreeSet<>();
        for (int x = minimumX; x <= maximumX; x++) {
            columns.add(x);
        }
        return columns;
    }

    private static Method clipContractMethod() {
        try {
            return StructureProcessor.class.getMethod("evaluatesEntirePieceState");
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static Set<Integer> expectedClippedColumns() {
        return clipContractMethod() != null
                ? columns(PROCESSING_AREA.minX(), PROCESSING_AREA.maxX())
                : columns(0, TEMPLATE_WIDTH - 1);
    }

    private static StructureProcessor protectedBlockProcessor() throws Exception {
        // Constructed reflectively: the constructor takes HolderSet on 26.2 and TagKey on 26.1.2.
        Constructor<?> constructor = ProtectedBlockProcessor.class.getConstructors()[0];
        Class<?> parameter = constructor.getParameterTypes()[0];
        Object argument = parameter == HolderSet.class
                ? HolderSet.empty()
                : TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "air"));
        return (StructureProcessor) constructor.newInstance(argument);
    }

    private static StructureTemplateManager forbiddenTemplateManager() {
        throw new AssertionError("Inline templates must not resolve a template manager");
    }

    private static StructureTemplate template() throws Exception {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (int x = 0; x < TEMPLATE_WIDTH; x++) {
            blocks.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(x, 0, 0), Blocks.COBBLESTONE.defaultBlockState(), null));
        }
        Constructor<StructureTemplate.Palette> constructor =
                StructureTemplate.Palette.class.getDeclaredConstructor(List.class);
        assertTrue(constructor.trySetAccessible());
        StructureTemplate template = new StructureTemplate();
        template.palettes.add(constructor.newInstance(blocks));
        return template;
    }

    private static WorldGenLevel world(ColumnRecorder recorder) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            String methodName = method.getName();
            if (methodName.equals("getHeight") && arguments.length == 3) {
                recorder.heightColumns.add((Integer) arguments[1]);
                return TERRAIN_HEIGHT;
            }
            if (methodName.equals("getBlockState")) {
                recorder.stateColumns.add(((BlockPos) arguments[0]).getX());
                return Blocks.STONE.defaultBlockState();
            }
            if (methodName.equals("getLevel")) {
                return null;
            }
            if (methodName.equals("holderLookup")) {
                return BuiltInRegistries.BLOCK;
            }
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == arguments[0];
            }
            if (methodName.equals("toString")) {
                return "occupancy-test-world";
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private static final class ColumnRecorder {
        private final Set<Integer> heightColumns = new TreeSet<>();
        private final Set<Integer> stateColumns = new TreeSet<>();
    }

    private static final class InlineSinglePoolElement extends SinglePoolElement {
        private InlineSinglePoolElement(
                StructureTemplate template, List<StructureProcessor> processors) {
            super(Either.right(template),
                    Holder.direct(new StructureProcessorList(processors)),
                    StructureTemplatePool.Projection.RIGID,
                    Optional.<LiquidSettings>empty());
        }
    }
}
