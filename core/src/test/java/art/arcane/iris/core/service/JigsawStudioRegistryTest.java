package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.loader.IrisData;
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

public class JigsawStudioActivationTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @After
    public void clearActivation() {
        JigsawStudioActivation.finishOpen(OWNER);
        JigsawStudioActivation.deactivate("overworld");
        JigsawStudioActivation.deactivate("nether");
    }

    @Test
    public void storesRequestAndSessionByNormalizedPackKey() {
        IrisData source = mock(IrisData.class);
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(11, 7, 11);
        JigsawStudioActivation.Request request = JigsawStudioActivation.activate(
                "OverWorld",
                "village/plains",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE,
                dimensions,
                source
        );

        assertTrue(JigsawStudioActivation.isActive("overworld"));
        assertSame(request, JigsawStudioActivation.getRequest("OVERWORLD"));
        assertSame(source, request.source());
        assertEquals(JigsawStudioMode.PLANAR_JIGSAW, request.mode());
        assertEquals(JigsawStudioCompatibilityTarget.VANILLA_PORTABLE, request.compatibilityTarget());
        assertEquals(dimensions, request.cellDimensions());
        assertNotNull(JigsawStudioActivation.getSession("overworld"));
        assertEquals(6, JigsawStudioActivation.getLayout("overworld").bays().size());
    }

    @Test
    public void replacementGetsANewRequestScopedSession() {
        IrisData source = mock(IrisData.class);
        JigsawStudioActivation.Request first = JigsawStudioActivation.activate(
                "overworld",
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(7, 5, 7),
                source
        );
        JigsawStudioActivation.Request second = JigsawStudioActivation.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(13, 9, 13),
                source
        );

        assertNotEquals(first.requestId(), second.requestId());
        assertEquals(second.requestId(), JigsawStudioActivation.getSession("overworld").sessionId());
        assertEquals(JigsawStudioMode.SPATIAL_JIGSAW, JigsawStudioActivation.getLayout("overworld").mode());
        assertEquals(1, JigsawStudioActivation.getLayout("overworld").bays().size());

        JigsawStudioActivation.deactivate("overworld");
        assertFalse(JigsawStudioActivation.isActive("overworld"));
    }

    @Test
    public void staleRequestCannotDeactivateItsReplacement() {
        IrisData source = mock(IrisData.class);
        JigsawStudioActivation.Request first = JigsawStudioActivation.activate(
                "overworld",
                "village",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16),
                source
        );
        JigsawStudioActivation.Request second = JigsawStudioActivation.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                new JigsawStudioCellDimensions(16, 16, 16),
                source
        );

        assertFalse(JigsawStudioActivation.deactivate("overworld", first.requestId()));
        assertSame(second, JigsawStudioActivation.getRequest("overworld"));
        assertTrue(JigsawStudioActivation.deactivate("overworld", second.requestId()));
        assertFalse(JigsawStudioActivation.isActive("overworld"));
    }

    @Test
    public void acceptsExistingGraphLayoutAsCleanInitialState() {
        IrisData source = mock(IrisData.class);
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

        JigsawStudioActivation.activate(
                "overworld",
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                initialLayout
        );

        assertSame(initialLayout, JigsawStudioActivation.getLayout("overworld"));
        assertSame(variant, JigsawStudioActivation.getSession("overworld")
                .activeVariant(JigsawStudioLayout.SPATIAL_WORKCELL_ID).orElseThrow());
        assertFalse(JigsawStudioActivation.getSession("overworld").isDirty());
    }

    @Test
    public void stagedSamePackReplacementKeepsPriorActivationUntilCommit() {
        IrisData source = mock(IrisData.class);
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout oldLayout = JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                dimensions,
                JigsawStudioVariantCatalog.empty());
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.Request previous = JigsawStudioActivation.activate(
                "overworld",
                "stronghold/old",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                oldLayout,
                OWNER);
        JigsawStudioSession previousSession = JigsawStudioActivation.getSession("overworld");
        JigsawStudioActivation.finishOpen(OWNER);

        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.StagedActivation staged = JigsawStudioActivation.stage(
                "overworld",
                "stronghold/new",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                oldLayout,
                OWNER,
                previous.requestId());

        assertSame(previous, JigsawStudioActivation.getRequest("overworld"));
        assertSame(previousSession, JigsawStudioActivation.getSession("overworld"));
        assertSame(previous, JigsawStudioActivation.getGeneratorRequest("overworld"));
        assertTrue(JigsawStudioActivation.beginStagedGeneration(staged));
        assertSame(staged.request(), JigsawStudioActivation.getGeneratorRequest("overworld"));
        assertSame(staged.session(), JigsawStudioActivation.getGeneratorSession("overworld"));
        assertSame(previous, JigsawStudioActivation.getRequest("overworld"));
        assertFalse(JigsawStudioActivation.deactivate("overworld", previous.requestId()));
        assertSame(previous, JigsawStudioActivation.getRequest("overworld"));

        assertTrue(JigsawStudioActivation.commit(staged));
        assertSame(staged.request(), JigsawStudioActivation.getRequest("overworld"));
        assertSame(staged.session(), JigsawStudioActivation.getSession("overworld"));
    }

    @Test
    public void failedSamePackReplacementRollsBackWithoutReplacingPriorSession() {
        IrisData source = mock(IrisData.class);
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(16, 16, 16);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                dimensions,
                JigsawStudioVariantCatalog.empty());
        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.Request previous = JigsawStudioActivation.activate(
                "overworld",
                "stronghold/old",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                layout,
                OWNER);
        JigsawStudioSession previousSession = JigsawStudioActivation.getSession("overworld");
        JigsawStudioActivation.finishOpen(OWNER);

        assertTrue(JigsawStudioActivation.tryBeginOpen(OWNER));
        JigsawStudioActivation.StagedActivation staged = JigsawStudioActivation.stage(
                "overworld",
                "stronghold/new",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                dimensions,
                source,
                layout,
                OWNER,
                previous.requestId());
        assertTrue(JigsawStudioActivation.beginStagedGeneration(staged));
        assertFalse(JigsawStudioActivation.deactivate("overworld", previous.requestId()));

        assertTrue(JigsawStudioActivation.rollback(staged));
        assertSame(previous, JigsawStudioActivation.getRequest("overworld"));
        assertSame(previousSession, JigsawStudioActivation.getSession("overworld"));
        assertSame(previous, JigsawStudioActivation.getGeneratorRequest("overworld"));
    }
}
