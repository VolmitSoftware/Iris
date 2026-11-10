package art.arcane.iris.core.service;

import com.volmit.tooling.ToolPack;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class JigsawStudioRegistryTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final JigsawStudioRegistry registry = new JigsawStudioRegistry();

    @After
    public void clearActivation() {
        registry.finishOpen(OWNER);
        registry.deactivate("overworld");
        registry.deactivate("nether");
    }

    @Test
    public void storesRequestAndSessionByNormalizedPackKey() {
        ToolPack source = StudioToolPacks.toolPack();
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(11, 7, 11);
        JigsawStudioRequest request = registry.activate(
                "OverWorld",
                "village/plains",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE,
                dimensions,
                source
        );

        assertTrue(registry.isActive("overworld"));
        assertSame(request, registry.getRequest("OVERWORLD"));
        assertSame(source, request.source());
        assertEquals(JigsawStudioMode.PLANAR_JIGSAW, request.mode());
        assertEquals(JigsawStudioCompatibilityTarget.VANILLA_PORTABLE, request.compatibilityTarget());
        assertEquals(dimensions, request.cellDimensions());
        assertNotNull(registry.getSession("overworld"));
        assertEquals(6, registry.getLayout("overworld").bays().size());
    }

    @Test
    public void replacementGetsANewRequestScopedSession() {
        ToolPack source = StudioToolPacks.toolPack();
        JigsawStudioRequest first = registry.activate(
                "overworld",
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(7, 5, 7),
                source
        );
        JigsawStudioRequest second = registry.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(13, 9, 13),
                source
        );

        assertNotEquals(first.requestId(), second.requestId());
        assertEquals(second.requestId(), registry.getSession("overworld").sessionId());
        assertEquals(JigsawStudioMode.SPATIAL_JIGSAW, registry.getLayout("overworld").mode());
        assertEquals(1, registry.getLayout("overworld").bays().size());

        registry.deactivate("overworld");
        assertFalse(registry.isActive("overworld"));
    }

    @Test
    public void staleRequestCannotDeactivateItsReplacement() {
        ToolPack source = StudioToolPacks.toolPack();
        JigsawStudioRequest first = registry.activate(
                "overworld",
                "village",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16),
                source
        );
        JigsawStudioRequest second = registry.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16),
                source
        );

        assertFalse(registry.deactivate("overworld", first.requestId()));
        assertSame(second, registry.getRequest("overworld"));
        assertTrue(registry.deactivate("overworld", second.requestId()));
        assertFalse(registry.isActive("overworld"));
    }

    @Test
    public void acceptsExistingGraphLayoutAsCleanInitialState() {
        ToolPack source = StudioToolPacks.toolPack();
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(9, 6, 9);
        JigsawStudioVariant variant = new JigsawStudioVariant(
                "stronghold/hall",
                "stronghold/hall",
                "",
                Optional.of(dimensions),
                JigsawStudioMode.SPATIAL_JIGSAW,
                Optional.empty(),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
        JigsawStudioLayout initialLayout = JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                dimensions,
                new JigsawStudioVariantCatalog(List.of(variant))
        );

        registry.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                initialLayout
        );

        assertSame(initialLayout, registry.getLayout("overworld"));
        assertSame(variant, registry.getSession("overworld")
                .activeVariant(JigsawStudioLayout.SPATIAL_WORKCELL_ID).orElseThrow());
        assertFalse(registry.getSession("overworld").isDirty());
    }

    @Test
    public void stagedSamePackReplacementKeepsPriorActivationUntilCommit() {
        ToolPack source = StudioToolPacks.toolPack();
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout oldLayout = JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                dimensions,
                JigsawStudioVariantCatalog.empty());
        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRequest previous = registry.activate(
                "overworld",
                "stronghold/old",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                oldLayout,
                OWNER);
        JigsawStudioSession previousSession = registry.getSession("overworld");
        registry.finishOpen(OWNER);

        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRegistry.StagedActivation staged = registry.stage(
                "overworld",
                "stronghold/new",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                oldLayout,
                OWNER,
                previous.requestId());

        assertSame(previous, registry.getRequest("overworld"));
        assertSame(previousSession, registry.getSession("overworld"));
        assertTrue(registry.beginStagedGeneration(staged));
        assertSame(previous, registry.getRequest("overworld"));
        assertFalse(registry.deactivate("overworld", previous.requestId()));
        assertSame(previous, registry.getRequest("overworld"));

        assertTrue(registry.commit(staged));
        assertSame(staged.request(), registry.getRequest("overworld"));
        assertSame(staged.session(), registry.getSession("overworld"));
    }

    @Test
    public void failedSamePackReplacementRollsBackWithoutReplacingPriorSession() {
        ToolPack source = StudioToolPacks.toolPack();
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                dimensions,
                JigsawStudioVariantCatalog.empty());
        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRequest previous = registry.activate(
                "overworld",
                "stronghold/old",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                layout,
                OWNER);
        JigsawStudioSession previousSession = registry.getSession("overworld");
        registry.finishOpen(OWNER);

        assertTrue(registry.tryBeginOpen(OWNER));
        JigsawStudioRegistry.StagedActivation staged = registry.stage(
                "overworld",
                "stronghold/new",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                layout,
                OWNER,
                previous.requestId());
        assertTrue(registry.beginStagedGeneration(staged));
        assertFalse(registry.deactivate("overworld", previous.requestId()));

        assertTrue(registry.rollback(staged));
        assertSame(previous, registry.getRequest("overworld"));
        assertSame(previousSession, registry.getSession("overworld"));
    }
}
