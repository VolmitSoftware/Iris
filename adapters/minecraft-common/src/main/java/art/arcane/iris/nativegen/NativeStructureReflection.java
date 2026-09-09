package art.arcane.iris.nativegen;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

final class NativeStructureReflection {
    private NativeStructureReflection() {
    }

    static Field resolveScatteredHeightPositionField() {
        Field resolved = null;
        for (Field field : ScatteredFeaturePiece.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || field.getType() != int.class
                    || !Modifier.isProtected(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("ScatteredFeaturePiece has multiple mutable protected int fields");
            }
            resolved = field;
        }
        if (resolved == null) {
            throw new IllegalStateException("ScatteredFeaturePiece height-position field is missing");
        }
        if (!resolved.trySetAccessible()) {
            throw new IllegalStateException("ScatteredFeaturePiece height-position field is inaccessible");
        }
        return resolved;
    }

    static RuinedPortalPiece.VerticalPlacement ruinedPortalVerticalPlacement(RuinedPortalPiece piece) {
        try {
            return (RuinedPortalPiece.VerticalPlacement) RuinedPortalPlacementAccess.FIELD.get(piece);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read native ruined portal vertical placement", error);
        }
    }

    private static Field resolveRuinedPortalPlacementField() {
        Field resolved = null;
        for (Field field : RuinedPortalPiece.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    || field.getType() != RuinedPortalPiece.VerticalPlacement.class) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("RuinedPortalPiece has multiple vertical-placement fields");
            }
            resolved = field;
        }
        if (resolved == null || !resolved.trySetAccessible()) {
            throw new IllegalStateException("RuinedPortalPiece vertical-placement field is inaccessible");
        }
        return resolved;
    }

    private static Field resolveMonumentChildPiecesField() {
        Field resolved = null;
        for (Field field : OceanMonumentPieces.MonumentBuilding.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != List.class) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("Ocean Monument has multiple instance List fields");
            }
            resolved = field;
        }
        if (resolved == null) {
            throw new IllegalStateException("Ocean Monument child-pieces List field is missing");
        }
        if (!Modifier.isPrivate(resolved.getModifiers()) || !Modifier.isFinal(resolved.getModifiers())) {
            throw new IllegalStateException("Ocean Monument child-pieces field has an unexpected access contract");
        }
        if (!resolved.trySetAccessible()) {
            throw new IllegalStateException("Ocean Monument child-pieces field is inaccessible");
        }
        return resolved;
    }

    static Field resolveSinglePoolTemplateField() {
        Field resolved = null;
        for (Field field : SinglePoolElement.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != Either.class) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("SinglePoolElement has multiple instance Either fields");
            }
            resolved = field;
        }
        if (resolved == null) {
            throw new IllegalStateException("SinglePoolElement template Either field is missing");
        }
        if (!Modifier.isProtected(resolved.getModifiers()) || !Modifier.isFinal(resolved.getModifiers())) {
            throw new IllegalStateException("SinglePoolElement template field has an unexpected access contract");
        }
        if (!resolved.trySetAccessible()) {
            throw new IllegalStateException("SinglePoolElement template field is inaccessible");
        }
        return resolved;
    }

    static StructureTemplate resolveTemplate(SinglePoolElement element,
                                             Supplier<StructureTemplateManager> templates) {
        Object value;
        try {
            value = SinglePoolTemplateAccess.FIELD.get(element);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read native structure pool template", error);
        }
        if (!(value instanceof Either<?, ?> reference)) {
            throw new IllegalStateException("Native structure pool template field is not an Either");
        }
        return resolveTemplateReference(reference, templates);
    }

    @SuppressWarnings("unchecked")
    static List<StructureTemplate.StructureBlockInfo> resolveTemplateBlocks(
            StructureTemplate template, StructurePlaceSettings settings,
            BlockPos origin) {
        Object value;
        try {
            value = StructureTemplatePalettesAccess.FIELD.get(template);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read native structure template palettes", error);
        }
        if (!(value instanceof List<?> paletteValues)) {
            throw new IllegalStateException("Native structure template palettes field is not a List");
        }
        List<StructureTemplate.Palette> palettes =
                (List<StructureTemplate.Palette>) paletteValues;
        if (palettes.isEmpty()) {
            return List.of();
        }
        return settings.getRandomPalette(palettes, origin).blocks();
    }

    static StructurePlaceSettings resolvePlacementSettings(
            SinglePoolElement element, PoolElementStructurePiece piece,
            BoundingBox area) {
        LiquidSettings liquidSettings;
        try {
            liquidSettings = (LiquidSettings) PoolPieceLiquidSettingsAccess.FIELD.get(piece);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read native structure piece liquid settings", error);
        }
        try {
            return (StructurePlaceSettings) SinglePoolSettingsAccess.METHOD.invoke(
                    element, piece.getRotation(), area, liquidSettings, false);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new IllegalStateException("Cannot create native structure placement settings", error);
        }
    }

    @SuppressWarnings("unchecked")
    static List<StructureTemplate.StructureBlockInfo> processTemplateBlocks(
            ServerLevelAccessor world, BlockPos origin, BlockPos referencePos,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> blocks,
            StructureTemplate template) {
        Method forgeMethod = StructureTemplateProcessingAccess.FORGE_METHOD;
        if (forgeMethod == null) {
            return StructureTemplate.processBlockInfos(
                    world, origin, referencePos, settings, blocks);
        }
        try {
            return (List<StructureTemplate.StructureBlockInfo>) forgeMethod.invoke(
                    null, world, origin, referencePos, settings, blocks, template);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new IllegalStateException("Cannot process native structure template blocks", error);
        }
    }

    static StructureTemplate resolveTemplateReference(Either<?, ?> reference,
                                                       Supplier<StructureTemplateManager> templates) {
        return reference.map(
                location -> resolveNamedTemplate(location, templates),
                NativeStructureReflection::requireRuntimeTemplate);
    }

    private static StructureTemplate resolveNamedTemplate(Object value,
                                                          Supplier<StructureTemplateManager> templates) {
        if (!(value instanceof Identifier identifier)) {
            throw new IllegalStateException("Native structure pool template identifier is "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        return Objects.requireNonNull(templates == null ? null : templates.get(),
                "Native structure template manager is unavailable").getOrCreate(identifier);
    }

    private static StructureTemplate requireRuntimeTemplate(Object value) {
        if (!(value instanceof StructureTemplate template)) {
            throw new IllegalStateException("Native runtime structure pool template is "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        return template;
    }

    private static Field resolveStructureTemplatePalettesField() {
        Field resolved = null;
        for (Field field : StructureTemplate.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != List.class
                    || !isPaletteList(field.getGenericType())) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("StructureTemplate has multiple palette List fields");
            }
            resolved = field;
        }
        if (resolved == null) {
            throw new IllegalStateException("StructureTemplate palette List field is missing");
        }
        if (!Modifier.isFinal(resolved.getModifiers())) {
            throw new IllegalStateException("StructureTemplate palette field has an unexpected access contract");
        }
        if (!resolved.trySetAccessible()) {
            throw new IllegalStateException("StructureTemplate palette field is inaccessible");
        }
        return resolved;
    }

    private static boolean isPaletteList(Type type) {
        if (!(type instanceof ParameterizedType parameterized)) {
            return false;
        }
        Type[] arguments = parameterized.getActualTypeArguments();
        return arguments.length == 1 && arguments[0] == StructureTemplate.Palette.class;
    }

    private static Method resolveSinglePoolSettingsMethod() {
        Method resolved = null;
        Class<?>[] parameterTypes = {
                Rotation.class, BoundingBox.class, LiquidSettings.class, boolean.class
        };
        for (Method method : SinglePoolElement.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != StructurePlaceSettings.class
                    || !Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("SinglePoolElement has multiple placement-settings methods");
            }
            resolved = method;
        }
        if (resolved == null) {
            throw new IllegalStateException("SinglePoolElement placement-settings method is missing");
        }
        if (!Modifier.isProtected(resolved.getModifiers())) {
            throw new IllegalStateException("SinglePoolElement placement-settings method has an unexpected access contract");
        }
        if (!resolved.trySetAccessible()) {
            throw new IllegalStateException("SinglePoolElement placement-settings method is inaccessible");
        }
        return resolved;
    }

    private static Field resolvePoolPieceLiquidSettingsField() {
        Field resolved = null;
        for (Field field : PoolElementStructurePiece.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    || field.getType() != LiquidSettings.class) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("PoolElementStructurePiece has multiple liquid-settings fields");
            }
            resolved = field;
        }
        if (resolved == null) {
            throw new IllegalStateException("PoolElementStructurePiece liquid-settings field is missing");
        }
        if (!Modifier.isPrivate(resolved.getModifiers())
                || !Modifier.isFinal(resolved.getModifiers())) {
            throw new IllegalStateException("PoolElementStructurePiece liquid-settings field has an unexpected access contract");
        }
        if (!resolved.trySetAccessible()) {
            throw new IllegalStateException("PoolElementStructurePiece liquid-settings field is inaccessible");
        }
        return resolved;
    }

    private static Method resolveForgeTemplateProcessingMethod() {
        Class<?>[] parameterTypes = {
                ServerLevelAccessor.class, BlockPos.class, BlockPos.class,
                StructurePlaceSettings.class, List.class, StructureTemplate.class
        };
        Method resolved = null;
        for (Method method : StructureTemplate.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != List.class
                    || !Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException(
                        "StructureTemplate has multiple Forge block-processing methods");
            }
            resolved = method;
        }
        return resolved;
    }

    static final class MonumentChildPiecesAccess {
        static final Field FIELD = resolveMonumentChildPiecesField();

        private MonumentChildPiecesAccess() {
        }
    }

    private static final class RuinedPortalPlacementAccess {
        private static final Field FIELD = resolveRuinedPortalPlacementField();

        private RuinedPortalPlacementAccess() {
        }
    }

    static final class ScatteredHeightPositionAccess {
        static final Field FIELD = resolveScatteredHeightPositionField();

        private ScatteredHeightPositionAccess() {
        }
    }

    private static final class SinglePoolTemplateAccess {
        private static final Field FIELD = resolveSinglePoolTemplateField();

        private SinglePoolTemplateAccess() {
        }
    }

    private static final class StructureTemplatePalettesAccess {
        private static final Field FIELD = resolveStructureTemplatePalettesField();

        private StructureTemplatePalettesAccess() {
        }
    }

    private static final class SinglePoolSettingsAccess {
        private static final Method METHOD = resolveSinglePoolSettingsMethod();

        private SinglePoolSettingsAccess() {
        }
    }

    private static final class PoolPieceLiquidSettingsAccess {
        private static final Field FIELD = resolvePoolPieceLiquidSettingsField();

        private PoolPieceLiquidSettingsAccess() {
        }
    }

    private static final class StructureTemplateProcessingAccess {
        private static final Method FORGE_METHOD = resolveForgeTemplateProcessingMethod();

        private StructureTemplateProcessingAccess() {
        }
    }
}
