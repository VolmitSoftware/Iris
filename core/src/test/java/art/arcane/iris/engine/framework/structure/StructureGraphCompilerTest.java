package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.object.IrisDirection;
import art.arcane.iris.engine.object.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.engine.object.IrisJigsawCompatibility;
import art.arcane.iris.engine.object.IrisJigsawConnector;
import art.arcane.iris.engine.object.IrisJigsawMode;
import art.arcane.iris.engine.object.IrisJigsawPiece;
import art.arcane.iris.engine.object.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.IrisJigsawPieceRules;
import art.arcane.iris.engine.object.IrisJigsawPool;
import art.arcane.iris.engine.object.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.object.JigsawJoint;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.collection.KList;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructureGraphCompilerTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    public void compilesACompleteTerminalGraphAndSamplesDeterministically() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", piece("objects/start"));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        IrisStructure structure = structure("pools/start");

        StructureGraphCompilation first = StructureGraphCompiler.compile(structure, resolver);
        StructureGraphCompilation second = StructureGraphCompiler.compile(structure, resolver);

        assertTrue(first.getDiagnostics().isEmpty());
        assertTrue(first.isAssemblyViable());
        assertEquals(List.of("pools/start"), new ArrayList<>(first.getGraph().getPools().keySet()));
        assertEquals(List.of("pieces/start"), new ArrayList<>(first.getGraph().getPieces().keySet()));
        assertEquals(List.of("objects/start"), new ArrayList<>(first.getGraph().getObjects().keySet()));
        assertEquals(first.getAssemblySamples(), second.getAssemblySamples());
        for (StructureGraphAssemblySample sample : first.getAssemblySamples()) {
            assertEquals(List.of("pieces/start"), sample.outcome().pieceKeys());
            assertEquals(StructureAssemblyStatus.COMPLETE, sample.outcome().status());
            assertEquals("", sample.outcome().detail());
            assertTrue(sample.outcome().isComplete());
        }
    }

    @Test
    public void reportsMissingReferencesInvalidWeightsAndNoViableStart() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawPool start = pool(entry("pieces/missing", 0));
        start.setFallback("pools/missing-fallback");
        resolver.pools.put("pools/start", start);

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure("pools/start"), resolver);

        assertCodes(result,
                StructureGraphDiagnostic.Code.INVALID_WEIGHT,
                StructureGraphDiagnostic.Code.MISSING_PIECE,
                StructureGraphDiagnostic.Code.MISSING_POOL,
                StructureGraphDiagnostic.Code.NO_VIABLE_START_PIECE);
        assertTrue(result.hasErrors());
        assertFalse(result.isAssemblyViable());
    }

    @Test
    public void reportsEmptyPoolsAndConnectorFieldFailures() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector connector = connector(new IrisPosition(4, 1, 1), null, "", "path", "path");
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", piece("objects/start", connector));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/empty", pool());

        StructureGraphCompilation invalidConnector = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);
        StructureGraphCompilation emptyStart = StructureGraphCompiler.compile(
                structure("pools/empty"), resolver);

        assertCodes(invalidConnector,
                StructureGraphDiagnostic.Code.INVALID_CONNECTOR_POSITION,
                StructureGraphDiagnostic.Code.INVALID_CONNECTOR_DIRECTION,
                StructureGraphDiagnostic.Code.MISSING_CONNECTOR_POOL);
        assertCodes(emptyStart,
                StructureGraphDiagnostic.Code.EMPTY_START_POOL,
                StructureGraphDiagnostic.Code.NO_VIABLE_START_PIECE);
    }

    @Test
    public void reportsIncompatibleDirectionsAndUnreachablePieces() {
        InMemoryResolver resolver = connectorGraph(false, IrisDirection.NORTH_NEGATIVE_Z);

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertCodes(result,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);
        assertFalse(result.getGraph().getReachablePieces().contains("pieces/target"));
        assertFalse(result.isAssemblyViable());
        for (StructureGraphAssemblySample sample : result.getAssemblySamples()) {
            assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED, sample.outcome().status());
            assertTrue(sample.outcome().detail().contains("could not place a piece"));
            assertFalse(sample.outcome().isComplete());
        }
    }

    @Test
    public void acceptsHorizontalRotationWithVanillaOneWayTargetMatching() {
        InMemoryResolver resolver = connectorGraph(true, IrisDirection.NORTH_NEGATIVE_Z);

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(result.getDiagnostics().isEmpty());
        assertTrue(result.getGraph().getReachablePieces().contains("pieces/target"));
        for (StructureGraphAssemblySample sample : result.getAssemblySamples()) {
            assertEquals(List.of("pieces/start", "pieces/target"), sample.outcome().pieceKeys());
            assertTrue(sample.outcome().isComplete());
        }
    }

    @Test
    public void connectorNamesUseTheRuntimeExactMatchingContract() {
        InMemoryResolver resolver = connectorGraph(false, IrisDirection.SOUTH_POSITIVE_Z);
        resolver.pieces.get("pieces/target").getConnectors().getFirst().setName(" door");

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertCodes(result,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);
        assertFalse(result.isAssemblyViable());
    }

    @Test
    public void rejectsVerticalConnectorsThatYRotationCannotAlign() {
        InMemoryResolver resolver = connectorGraph(true, IrisDirection.UP_POSITIVE_Y);

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertCodes(result,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);
    }

    @Test
    public void alignedConnectorsRequireMatchingTopOrientation() {
        InMemoryResolver resolver = connectorGraph(false, IrisDirection.SOUTH_POSITIVE_Z);
        IrisJigsawConnector source = resolver.pieces.get("pieces/start").getConnectors().getFirst();
        IrisJigsawConnector target = resolver.pieces.get("pieces/target").getConnectors().getFirst();
        source.setJoint(JigsawJoint.ALIGNED);
        source.setTop(IrisDirection.UP_POSITIVE_Y);
        target.setTop(IrisDirection.DOWN_NEGATIVE_Y);

        StructureGraphCompilation aligned = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);
        assertCodes(aligned,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);

        source.setJoint(JigsawJoint.ROLLABLE);
        StructureGraphCompilation rollable = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);
        assertTrue(rollable.isAssemblyViable());
    }

    @Test
    public void channelsParticipateInMatchingAndPortableGraphsRejectThem() {
        InMemoryResolver resolver = connectorGraph(false, IrisDirection.SOUTH_POSITIVE_Z);
        IrisJigsawConnector source = resolver.pieces.get("pieces/start").getConnectors().getFirst();
        IrisJigsawConnector target = resolver.pieces.get("pieces/target").getConnectors().getFirst();
        source.setChannel("roads");
        target.setChannel("rooms");

        StructureGraphCompilation mismatched = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertCodes(mismatched,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);

        target.setChannel("Roads");
        StructureGraphCompilation mismatchedCase = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertCodes(mismatchedCase,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);

        target.setChannel(" roads");
        StructureGraphCompilation mismatchedWhitespace = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertCodes(mismatchedWhitespace,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);

        target.setChannel("roads");
        StructureGraphCompilation matched = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(matched.isAssemblyViable());

        IrisStructure portable = structure("pools/start")
                .setCompatibility(IrisJigsawCompatibility.VANILLA_PORTABLE)
                .setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH);
        StructureGraphCompilation rejectedPortable = StructureGraphCompiler.compile(portable, resolver);

        assertCodes(rejectedPortable, StructureGraphDiagnostic.Code.NON_PORTABLE_CONNECTOR);
        assertTrue(rejectedPortable.hasErrors());
    }

    @Test
    public void validatesPlanarCellSizeAndConnectorMetadata() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector connector = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/terminal", "source", "door")
                .setFinalState("")
                .setSelectionPriority(-1);
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", piece("objects/start", connector));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/terminal", pool());
        IrisStructure structure = structure("pools/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                .setCellSize(new IrisPosition(16, 0, 16));

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertCodes(result,
                StructureGraphDiagnostic.Code.INVALID_PLANAR_CELL_SIZE,
                StructureGraphDiagnostic.Code.INVALID_CONNECTOR_METADATA);
        assertTrue(result.hasErrors());
    }

    @Test
    public void acceptsRectangularPlanarCellSize() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/start", new IrisObject(16, 8, 8));
        resolver.pieces.put("pieces/start", piece("objects/start"));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        IrisStructure structure = structure("pools/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                .setCellSize(new IrisPosition(16, 8, 8));

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertFalse(hasCode(result, StructureGraphDiagnostic.Code.INVALID_PLANAR_CELL_SIZE));
        assertTrue(result.isAssemblyViable());
    }

    @Test
    public void allowsSmallerPlanarObjectsAndRejectsObjectsOutsideTheirWorkcell() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/start", new IrisObject(4, 3, 4));
        resolver.pieces.put("pieces/start", piece("objects/start"));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        IrisStructure structure = structure("pools/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                .setCellSize(new IrisPosition(4, 3, 4));

        StructureGraphCompilation valid = StructureGraphCompiler.compile(structure, resolver);

        assertFalse(hasCode(valid, StructureGraphDiagnostic.Code.INVALID_PLANAR_OBJECT_SIZE));

        resolver.objects.put("objects/start", new IrisObject(4, 2, 4));
        StructureGraphCompilation smaller = StructureGraphCompiler.compile(structure, resolver);

        assertFalse(hasCode(smaller, StructureGraphDiagnostic.Code.INVALID_PLANAR_OBJECT_SIZE));

        resolver.objects.put("objects/start", new IrisObject(5, 3, 4));
        StructureGraphCompilation oversized = StructureGraphCompiler.compile(structure, resolver);

        assertCodes(oversized, StructureGraphDiagnostic.Code.INVALID_PLANAR_OBJECT_SIZE);
        assertTrue(oversized.getDiagnostics().stream().anyMatch(
                diagnostic -> diagnostic.message().contains("does not fit")));
    }

    @Test
    public void ignoresPlanarObjectSizeForPiecesThatCannotBeReached() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector source = connector(
                new IrisPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/target", "source", "door");
        IrisJigsawConnector target = connector(
                new IrisPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/target", "door", "unused");
        resolver.objects.put("objects/start", new IrisObject(4, 3, 4));
        resolver.objects.put("objects/target", new IrisObject(4, 2, 4));
        resolver.pieces.put("pieces/start", piece("objects/start", source).setRotatable(false));
        resolver.pieces.put("pieces/target", piece("objects/target", target).setRotatable(false));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/target", pool(entry("pieces/target", 1)));
        IrisStructure structure = structure("pools/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                .setCellSize(new IrisPosition(4, 3, 4));

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertFalse(result.getGraph().getReachablePieces().contains("pieces/target"));
        assertFalse(hasCode(result, StructureGraphDiagnostic.Code.INVALID_PLANAR_OBJECT_SIZE));
    }

    @Test
    public void acceptsAllCanonicalPlanarFaceCenterSockets() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector north = connector(
                new IrisPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/terminal", "north", "door");
        IrisJigsawConnector east = connector(
                new IrisPosition(3, 1, 2), IrisDirection.EAST_POSITIVE_X,
                "pools/terminal", "east", "door");
        IrisJigsawConnector south = connector(
                new IrisPosition(2, 1, 3), IrisDirection.SOUTH_POSITIVE_Z,
                "pools/terminal", "south", "door");
        IrisJigsawConnector west = connector(
                new IrisPosition(0, 1, 2), IrisDirection.WEST_NEGATIVE_X,
                "pools/terminal", "west", "door");
        resolver.objects.put("objects/start", new IrisObject(4, 3, 4));
        resolver.pieces.put("pieces/start", piece("objects/start", north, east, south, west));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/terminal", pool());
        IrisStructure structure = structure("pools/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                .setCellSize(new IrisPosition(4, 3, 4));

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertTrue(result.getDiagnostics().isEmpty());
        assertTrue(result.isAssemblyViable());
    }

    @Test
    public void rejectsOffsetVerticalAndTiltedPlanarConnectors() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector offset = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/terminal", "offset", "door");
        IrisJigsawConnector vertical = connector(
                new IrisPosition(2, 1, 2), IrisDirection.UP_POSITIVE_Y,
                "pools/terminal", "vertical", "door");
        IrisJigsawConnector tilted = connector(
                new IrisPosition(2, 1, 3), IrisDirection.SOUTH_POSITIVE_Z,
                "pools/terminal", "tilted", "door")
                .setTop(IrisDirection.DOWN_NEGATIVE_Y);
        resolver.objects.put("objects/start", new IrisObject(4, 3, 4));
        resolver.pieces.put("pieces/start", piece("objects/start", offset, vertical, tilted));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/terminal", pool());
        IrisStructure structure = structure("pools/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                .setCellSize(new IrisPosition(4, 3, 4));

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);
        List<String> messages = new ArrayList<>();
        for (StructureGraphDiagnostic diagnostic : result.getDiagnostics()) {
            if (diagnostic.code() == StructureGraphDiagnostic.Code.INVALID_PLANAR_CONNECTOR) {
                messages.add(diagnostic.message());
            }
        }

        assertEquals(3, messages.size());
        assertTrue(messages.stream().anyMatch(message -> message.contains("canonical NORTH_NEGATIVE_Z")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("uses vertical direction")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("keep connected cells on one Y level")));
        assertTrue(result.hasErrors());
    }

    @Test
    public void spatialGraphsIgnoreThePlanarGeometryContract() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector vertical = connector(
                new IrisPosition(1, 4, 1), IrisDirection.UP_POSITIVE_Y,
                "pools/terminal", "vertical", "door")
                .setTop(IrisDirection.DOWN_NEGATIVE_Y);
        resolver.objects.put("objects/start", new IrisObject(2, 5, 3));
        resolver.pieces.put("pieces/start", piece("objects/start", vertical));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/terminal", pool());
        IrisStructure structure = structure("pools/start")
                .setMode(IrisJigsawMode.SPATIAL_JIGSAW)
                .setCellSize(new IrisPosition(16, 0, 8));

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertFalse(hasCode(result, StructureGraphDiagnostic.Code.INVALID_PLANAR_CELL_SIZE));
        assertFalse(hasCode(result, StructureGraphDiagnostic.Code.INVALID_PLANAR_OBJECT_SIZE));
        assertFalse(hasCode(result, StructureGraphDiagnostic.Code.INVALID_PLANAR_CONNECTOR));
        assertFalse(result.hasErrors());
    }

    @Test
    public void deterministicSamplesHonorSelectionPriorityAndStableTies() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector east = connector(
                new IrisPosition(2, 1, 1), IrisDirection.EAST_POSITIVE_X,
                "pools/east", "source", "door").setSelectionPriority(-20);
        IrisJigsawConnector west = connector(
                new IrisPosition(0, 1, 1), IrisDirection.WEST_NEGATIVE_X,
                "pools/west", "source", "door").setSelectionPriority(-10);
        resolver.objects.put("objects/start", object());
        resolver.objects.put("objects/east", object());
        resolver.objects.put("objects/west", object());
        resolver.pieces.put("pieces/start", piece("objects/start", east, west));
        resolver.pieces.put("pieces/east", piece("objects/east", connector(
                new IrisPosition(0, 1, 1), IrisDirection.WEST_NEGATIVE_X,
                "pools/terminal", "door", "unused")));
        resolver.pieces.put("pieces/west", piece("objects/west", connector(
                new IrisPosition(2, 1, 1), IrisDirection.EAST_POSITIVE_X,
                "pools/terminal", "door", "unused")));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/east", pool(entry("pieces/east", 1)));
        resolver.pools.put("pools/west", pool(entry("pieces/west", 1)));
        resolver.pools.put("pools/terminal", pool());
        IrisStructure structure = structure("pools/start");

        StructureGraphCompilation prioritized = StructureGraphCompiler.compile(structure, resolver);

        for (StructureGraphAssemblySample sample : prioritized.getAssemblySamples()) {
            assertEquals(List.of("pieces/start", "pieces/west", "pieces/east"),
                    sample.outcome().pieceKeys());
        }

        east.setSelectionPriority(-10);
        StructureGraphCompilation tied = StructureGraphCompiler.compile(structure, resolver);

        for (StructureGraphAssemblySample sample : tied.getAssemblySamples()) {
            assertEquals(List.of("pieces/start", "pieces/east", "pieces/west"),
                    sample.outcome().pieceKeys());
        }
    }

    @Test
    public void deterministicSamplesScheduleChildExpansionBySourcePlacementPriority() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector eastSource = connector(
                new IrisPosition(2, 1, 1), IrisDirection.EAST_POSITIVE_X,
                "pools/east", "source", "door").setPlacementPriority(-20);
        IrisJigsawConnector westSource = connector(
                new IrisPosition(0, 1, 1), IrisDirection.WEST_NEGATIVE_X,
                "pools/west", "source", "door").setPlacementPriority(-10);
        IrisJigsawConnector eastTarget = connector(
                new IrisPosition(0, 1, 1), IrisDirection.WEST_NEGATIVE_X,
                "pools/terminal", "door", "unused");
        IrisJigsawConnector eastBranch = connector(
                new IrisPosition(2, 1, 1), IrisDirection.EAST_POSITIVE_X,
                "pools/east-leaf", "east", "leaf");
        IrisJigsawConnector westTarget = connector(
                new IrisPosition(2, 1, 1), IrisDirection.EAST_POSITIVE_X,
                "pools/terminal", "door", "unused");
        IrisJigsawConnector westBranch = connector(
                new IrisPosition(0, 1, 1), IrisDirection.WEST_NEGATIVE_X,
                "pools/west-leaf", "west", "leaf");
        resolver.objects.put("objects/start", object());
        resolver.objects.put("objects/east", object());
        resolver.objects.put("objects/west", object());
        resolver.objects.put("objects/east-leaf", object());
        resolver.objects.put("objects/west-leaf", object());
        resolver.pieces.put("pieces/start", piece("objects/start", eastSource, westSource));
        resolver.pieces.put("pieces/east", piece("objects/east", eastTarget, eastBranch));
        resolver.pieces.put("pieces/west", piece("objects/west", westTarget, westBranch));
        resolver.pieces.put("pieces/east-leaf", piece("objects/east-leaf", connector(
                new IrisPosition(0, 1, 1), IrisDirection.WEST_NEGATIVE_X,
                "pools/terminal", "leaf", "unused")));
        resolver.pieces.put("pieces/west-leaf", piece("objects/west-leaf", connector(
                new IrisPosition(2, 1, 1), IrisDirection.EAST_POSITIVE_X,
                "pools/terminal", "leaf", "unused")));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/east", pool(entry("pieces/east", 1)));
        resolver.pools.put("pools/west", pool(entry("pieces/west", 1)));
        resolver.pools.put("pools/east-leaf", pool(entry("pieces/east-leaf", 1)));
        resolver.pools.put("pools/west-leaf", pool(entry("pieces/west-leaf", 1)));
        resolver.pools.put("pools/terminal", pool());
        IrisStructure structure = structure("pools/start");

        StructureGraphCompilation prioritized = StructureGraphCompiler.compile(structure, resolver);

        for (StructureGraphAssemblySample sample : prioritized.getAssemblySamples()) {
            assertEquals(List.of(
                            "pieces/start", "pieces/east", "pieces/west",
                            "pieces/west-leaf", "pieces/east-leaf"),
                    sample.outcome().pieceKeys());
        }

        westSource.setPlacementPriority(-20);
        StructureGraphCompilation tied = StructureGraphCompiler.compile(structure, resolver);

        for (StructureGraphAssemblySample sample : tied.getAssemblySamples()) {
            assertEquals(List.of(
                            "pieces/start", "pieces/east", "pieces/west",
                            "pieces/east-leaf", "pieces/west-leaf"),
                    sample.outcome().pieceKeys());
        }
    }

    @Test
    public void deterministicSamplesUseSelectionPriorityForCandidateConnectors() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector source = connector(
                new IrisPosition(0, 1, 0), IrisDirection.EAST_POSITIVE_X,
                "pools/target", "source", "door");
        IrisJigsawConnector low = connector(
                new IrisPosition(0, 1, 0), IrisDirection.WEST_NEGATIVE_X,
                "pools/low", "door", "leaf").setSelectionPriority(-20);
        IrisJigsawConnector high = connector(
                new IrisPosition(0, 1, 2), IrisDirection.WEST_NEGATIVE_X,
                "pools/high", "door", "leaf").setSelectionPriority(-10);
        resolver.objects.put("objects/start", new IrisObject(1, 3, 1));
        resolver.objects.put("objects/target", new IrisObject(1, 3, 3));
        resolver.objects.put("objects/low-leaf", new IrisObject(1, 3, 1));
        resolver.objects.put("objects/high-leaf", new IrisObject(1, 3, 1));
        resolver.pieces.put("pieces/start", piece("objects/start", source).setRotatable(false));
        resolver.pieces.put("pieces/target", piece("objects/target", low, high).setRotatable(false));
        resolver.pieces.put("pieces/low-leaf", piece("objects/low-leaf", connector(
                new IrisPosition(0, 1, 0), IrisDirection.EAST_POSITIVE_X,
                "pools/terminal", "leaf", "unused")).setRotatable(false));
        resolver.pieces.put("pieces/high-leaf", piece("objects/high-leaf", connector(
                new IrisPosition(0, 1, 0), IrisDirection.EAST_POSITIVE_X,
                "pools/terminal", "leaf", "unused")).setRotatable(false));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/target", pool(entry("pieces/target", 1)));
        resolver.pools.put("pools/low", pool(entry("pieces/low-leaf", 1)));
        resolver.pools.put("pools/high", pool(entry("pieces/high-leaf", 1)));
        resolver.pools.put("pools/terminal", pool());
        IrisStructure structure = structure("pools/start");

        StructureGraphCompilation prioritized = StructureGraphCompiler.compile(structure, resolver);

        for (StructureGraphAssemblySample sample : prioritized.getAssemblySamples()) {
            assertEquals(List.of("pieces/start", "pieces/target", "pieces/low-leaf"),
                    sample.outcome().pieceKeys());
        }

        low.setSelectionPriority(-10);
        StructureGraphCompilation tied = StructureGraphCompiler.compile(structure, resolver);

        for (StructureGraphAssemblySample sample : tied.getAssemblySamples()) {
            assertEquals(List.of("pieces/start", "pieces/target", "pieces/high-leaf"),
                    sample.outcome().pieceKeys());
        }
    }

    @Test
    public void deterministicSamplingUsesFallbackAtTheMaximumDepth() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector source = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/primary", "source", "door");
        IrisJigsawConnector primaryTarget = connector(
                new IrisPosition(1, 1, 2), IrisDirection.SOUTH_POSITIVE_Z,
                "pools/primary", "door", "unused");
        IrisJigsawConnector primarySource = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/primary", "source", "door");
        IrisJigsawConnector fallbackTarget = connector(
                new IrisPosition(1, 1, 2), IrisDirection.SOUTH_POSITIVE_Z,
                "pools/primary", "door", "unused");
        resolver.objects.put("objects/start", object());
        resolver.objects.put("objects/primary", object());
        resolver.objects.put("objects/fallback", object());
        resolver.pieces.put("pieces/start", piece("objects/start", source));
        resolver.pieces.put("pieces/primary", piece("objects/primary", primaryTarget, primarySource));
        resolver.pieces.put("pieces/fallback", piece("objects/fallback", fallbackTarget));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        IrisJigsawPool primary = pool(entry("pieces/primary", 1));
        primary.setFallback("pools/fallback");
        resolver.pools.put("pools/primary", primary);
        resolver.pools.put("pools/fallback", pool(entry("pieces/fallback", 1)));
        IrisStructure structure = structure("pools/start");
        structure.setMaxDepth(1);

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertTrue(result.getDiagnostics().isEmpty());
        assertTrue(result.isAssemblyViable());
        for (StructureGraphAssemblySample sample : result.getAssemblySamples()) {
            assertEquals(List.of("pieces/start", "pieces/primary", "pieces/fallback"),
                    sample.outcome().pieceKeys());
            assertTrue(sample.outcome().isComplete());
        }
    }

    @Test
    public void deterministicSamplingDoesNotSelectFallbackOfFallback() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector source = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/primary", "source", "door");
        IrisJigsawConnector incompatible = connector(
                new IrisPosition(1, 1, 2), IrisDirection.SOUTH_POSITIVE_Z,
                "pools/primary", "wrong", "unused");
        IrisJigsawConnector nestedTarget = connector(
                new IrisPosition(1, 1, 2), IrisDirection.SOUTH_POSITIVE_Z,
                "pools/primary", "door", "unused");
        resolver.objects.put("objects/start", object());
        resolver.objects.put("objects/incompatible", object());
        resolver.objects.put("objects/nested", object());
        resolver.pieces.put("pieces/start", piece("objects/start", source));
        resolver.pieces.put("pieces/incompatible", piece("objects/incompatible", incompatible));
        resolver.pieces.put("pieces/nested", piece("objects/nested", nestedTarget));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        IrisJigsawPool primary = pool(entry("pieces/incompatible", 1));
        primary.setFallback("pools/direct");
        resolver.pools.put("pools/primary", primary);
        IrisJigsawPool direct = pool(entry("pieces/incompatible", 1));
        direct.setFallback("pools/nested");
        resolver.pools.put("pools/direct", direct);
        resolver.pools.put("pools/nested", pool(entry("pieces/nested", 1)));

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertFalse(result.getGraph().getReachablePools().contains("pools/nested"));
        assertFalse(result.getGraph().getReachablePieces().contains("pieces/nested"));
        assertFalse(result.isAssemblyViable());
        for (StructureGraphAssemblySample sample : result.getAssemblySamples()) {
            assertEquals(List.of("pieces/start"), sample.outcome().pieceKeys());
            assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED, sample.outcome().status());
            assertTrue(sample.outcome().detail().contains("could not place"));
        }
    }

    @Test
    public void reportsFallbackCyclesAndTheirUnreachableClosure() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/terminal", object());
        resolver.pieces.put("pieces/terminal", piece("objects/terminal"));
        IrisJigsawPool start = pool(entry("pieces/terminal", 1));
        IrisJigsawPool first = pool(entry("pieces/terminal", 1));
        IrisJigsawPool second = pool(entry("pieces/terminal", 1));
        start.setFallback("pools/first");
        first.setFallback("pools/second");
        second.setFallback("pools/first");
        resolver.pools.put("pools/start", start);
        resolver.pools.put("pools/first", first);
        resolver.pools.put("pools/second", second);

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertCodes(result,
                StructureGraphDiagnostic.Code.FALLBACK_CYCLE,
                StructureGraphDiagnostic.Code.UNREACHABLE_POOL);
        assertTrue(result.hasErrors());
    }

    @Test
    public void validatesLongFallbackChainsWithoutRecursiveTraversal() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/terminal", object());
        resolver.pieces.put("pieces/terminal", piece("objects/terminal"));
        int poolCount = 10_000;
        for (int index = 0; index < poolCount; index++) {
            IrisJigsawPool pool = pool(entry("pieces/terminal", 1));
            if (index + 1 < poolCount) {
                pool.setFallback("pools/chain-" + (index + 1));
            }
            resolver.pools.put("pools/chain-" + index, pool);
        }

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/chain-0"), resolver);

        assertTrue(result.getDiagnostics().stream().noneMatch(
                diagnostic -> diagnostic.code() == StructureGraphDiagnostic.Code.FALLBACK_CYCLE));
        assertTrue(result.isAssemblyViable());
    }

    @Test
    public void reportsMissingObjectsAndInvalidObjectBounds() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/empty", new IrisObject());
        resolver.pieces.put("pieces/missing-object", piece("objects/missing"));
        resolver.pieces.put("pieces/empty-object", piece("objects/empty"));
        resolver.pools.put("pools/start", pool(
                entry("pieces/missing-object", 1),
                entry("pieces/empty-object", 1)));

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertCodes(result,
                StructureGraphDiagnostic.Code.MISSING_OBJECT,
                StructureGraphDiagnostic.Code.INVALID_OBJECT_BOUNDS);
        assertTrue(result.hasErrors());
    }

    @Test
    public void rejectsNullConnectorCollectionsAndNames() {
        InMemoryResolver nullListResolver = new InMemoryResolver();
        nullListResolver.objects.put("objects/start", object());
        IrisJigsawPiece nullListPiece = piece("objects/start");
        nullListPiece.setConnectors(null);
        nullListResolver.pieces.put("pieces/start", nullListPiece);
        nullListResolver.pools.put("pools/start", pool(entry("pieces/start", 1)));

        StructureGraphCompilation nullList = StructureGraphCompiler.compile(
                structure("pools/start"), nullListResolver);

        assertCodes(nullList, StructureGraphDiagnostic.Code.INVALID_CONNECTOR);
        assertTrue(nullList.hasErrors());

        InMemoryResolver nullNamesResolver = connectorGraph(true, IrisDirection.SOUTH_POSITIVE_Z);
        nullNamesResolver.pieces.get("pieces/start").getConnectors().getFirst().setTargetName(null);
        StructureGraphCompilation nullNames = StructureGraphCompiler.compile(
                structure("pools/start"), nullNamesResolver);

        assertCodes(nullNames, StructureGraphDiagnostic.Code.INVALID_CONNECTOR);
        assertTrue(nullNames.hasErrors());
    }

    @Test
    public void rejectsStructureLimitsOutsideTheRuntimeContract() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", piece("objects/start"));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));

        IrisStructure deep = structure("pools/start");
        deep.setMaxDepth(31);
        IrisStructure wide = structure("pools/start");
        wide.setMaxSizeChunks(33);
        IrisStructure overflowing = structure("pools/start");
        overflowing.setMaxSizeChunks(Integer.MAX_VALUE);

        assertCodes(StructureGraphCompiler.compile(deep, resolver),
                StructureGraphDiagnostic.Code.INVALID_MAX_DEPTH);
        assertCodes(StructureGraphCompiler.compile(wide, resolver),
                StructureGraphDiagnostic.Code.INVALID_MAX_SIZE);
        assertCodes(StructureGraphCompiler.compile(overflowing, resolver),
                StructureGraphDiagnostic.Code.INVALID_MAX_SIZE);
    }

    @Test
    public void emptyPoolChoiceTerminatesABranchWithoutAPlaceholderPiece() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector source = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/terminal", "source", "door");
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", piece("objects/start", source));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/terminal", pool(emptyEntry(4)));

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(result.getDiagnostics().isEmpty());
        assertTrue(result.isAssemblyViable());
        for (StructureGraphAssemblySample sample : result.getAssemblySamples()) {
            assertEquals(List.of("pieces/start"), sample.outcome().pieceKeys());
            assertTrue(sample.outcome().isComplete());
        }
    }

    @Test
    public void intentionalEmptyStartSamplesPreserveRuntimeStatusAndDetail() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.pools.put("pools/start", pool(emptyEntry(1)));

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(result.isAssemblyViable());
        for (StructureGraphAssemblySample sample : result.getAssemblySamples()) {
            assertEquals(StructureAssemblyStatus.INTENTIONAL_EMPTY, sample.outcome().status());
            assertEquals(
                    "The selected start-pool membership intentionally produces no structure",
                    sample.outcome().detail());
            assertTrue(sample.outcome().intentionalEmpty());
        }
    }

    @Test
    public void rareEmptyStartPreventsGuaranteedReplacementOutputWithoutDependingOnSamples() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", piece("objects/start"));
        resolver.pools.put("pools/start", pool(
                entry("pieces/start", Integer.MAX_VALUE), emptyEntry(1)));

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(result.isAssemblyViable());
        assertFalse(result.guaranteesAssemblyOutput());
        assertTrue(result.getAssemblySamples().stream().noneMatch(
                sample -> sample.outcome().intentionalEmpty()));
    }

    @Test
    public void oversizedStartPreventsGuaranteedReplacementOutput() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/start", new IrisObject(130, 3, 3));
        resolver.pieces.put("pieces/start", piece("objects/start"));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(result.isAssemblyViable());
        assertFalse(result.guaranteesAssemblyOutput());
    }

    @Test
    public void reachableBranchRequiresAuthoredEmptyTerminationForReplacementOutput() {
        InMemoryResolver resolver = connectorGraph(true, IrisDirection.NORTH_NEGATIVE_Z);

        StructureGraphCompilation unbounded = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(unbounded.isAssemblyViable());
        assertFalse(unbounded.guaranteesAssemblyOutput());

        resolver.pools.get("pools/target").setFallback("pools/empty");
        resolver.pools.put("pools/empty", pool());
        StructureGraphCompilation terminated = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(terminated.isAssemblyViable());
        assertTrue(terminated.guaranteesAssemblyOutput());
    }

    @Test
    public void branchTerminationPolicyMakesUnmatchedOptionalBranchesViable() {
        InMemoryResolver resolver = connectorGraph(false, IrisDirection.NORTH_NEGATIVE_Z);
        IrisStructure strictStructure = structure("pools/start");
        IrisStructure terminatingStructure = structure("pools/start")
                .setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH);

        StructureGraphCompilation strict = StructureGraphCompiler.compile(strictStructure, resolver);
        StructureGraphCompilation terminating = StructureGraphCompiler.compile(terminatingStructure, resolver);

        assertCodes(strict,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);
        assertFalse(strict.isAssemblyViable());
        assertFalse(strict.guaranteesAssemblyOutput());
        assertCodes(terminating,
                StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                StructureGraphDiagnostic.Code.UNREACHABLE_PIECE);
        assertTrue(terminating.isAssemblyViable());
        assertTrue(terminating.guaranteesAssemblyOutput());
        for (StructureGraphAssemblySample sample : terminating.getAssemblySamples()) {
            assertEquals(StructureAssemblyStatus.COMPLETE, sample.outcome().status());
            assertEquals(List.of("pieces/start"), sample.outcome().pieceKeys());
        }
    }

    @Test
    public void vanillaPortableGraphsRequireBranchTerminationPolicy() {
        InMemoryResolver resolver = new InMemoryResolver();
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", piece("objects/start"));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        IrisStructure structure = structure("pools/start")
                .setCompatibility(IrisJigsawCompatibility.VANILLA_PORTABLE);

        StructureGraphCompilation strict = StructureGraphCompiler.compile(structure, resolver);
        structure.setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH);
        StructureGraphCompilation terminating = StructureGraphCompiler.compile(structure, resolver);

        assertCodes(strict, StructureGraphDiagnostic.Code.NON_PORTABLE_METADATA);
        assertTrue(strict.hasErrors());
        assertFalse(hasCode(terminating, StructureGraphDiagnostic.Code.NON_PORTABLE_METADATA));
        assertTrue(terminating.isAssemblyViable());
    }

    @Test
    public void authoritativeGeometrySamplingDoesNotReportTopologyOnlyPieceCaps() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector startSource = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/recursive", "source", "door");
        IrisJigsawConnector recursiveTarget = connector(
                new IrisPosition(1, 1, 2), IrisDirection.SOUTH_POSITIVE_Z,
                "pools/recursive", "door", "unused");
        IrisJigsawConnector firstBranch = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/recursive", "source", "door");
        IrisJigsawConnector secondBranch = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/recursive", "source", "door");
        resolver.objects.put("objects/start", object());
        resolver.objects.put("objects/recursive", object());
        resolver.pieces.put("pieces/start", piece("objects/start", startSource).setRotatable(false));
        resolver.pieces.put("pieces/recursive", piece(
                "objects/recursive", recursiveTarget, firstBranch, secondBranch).setRotatable(false));
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        IrisJigsawPool recursive = pool(entry("pieces/recursive", 1));
        recursive.setFallback("pools/empty");
        resolver.pools.put("pools/recursive", recursive);
        resolver.pools.put("pools/empty", pool());
        IrisStructure structure = structure("pools/start");
        structure.setMaxDepth(30);
        structure.setMaxSizeChunks(8);

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertFalse(hasCode(result, StructureGraphDiagnostic.Code.ASSEMBLY_PIECE_CAP_REACHED));
        assertTrue(result.isAssemblyViable());
        for (StructureGraphAssemblySample sample : result.getAssemblySamples()) {
            assertFalse(sample.outcome().pieceCapReached());
            assertEquals(31, sample.outcome().pieceKeys().size());
            assertEquals(StructureAssemblyStatus.COMPLETE, sample.outcome().status());
        }
    }

    @Test
    public void validatesChanceThemeAndRuleMetadataBeforeSampling() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawPiece start = piece("objects/start");
        start.getThemes().add("missing-theme");
        start.setRules(new IrisJigsawPieceRules().setMinimumDepth(4).setMaximumDepth(2));
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", start);
        resolver.pools.put("pools/start", pool(
                entry("pieces/start", 1).setChance(Double.NaN)));
        IrisStructure structure = structure("pools/start");
        structure.getThemeSets().add(new IrisJigsawThemeSet("variant-1", 1));

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertCodes(result,
                StructureGraphDiagnostic.Code.INVALID_CHANCE,
                StructureGraphDiagnostic.Code.INVALID_THEME,
                StructureGraphDiagnostic.Code.INVALID_PIECE_RULE);
        assertTrue(result.getAssemblySamples().isEmpty());
    }

    @Test
    public void rejectsADeclaredThemeWithoutAnEligibleStartPiece() {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawPiece start = piece("objects/start");
        start.getThemes().add("variant-1");
        resolver.objects.put("objects/start", object());
        resolver.pieces.put("pieces/start", start);
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        IrisStructure structure = structure("pools/start");
        structure.getThemeSets().add(new IrisJigsawThemeSet("variant-1", 1));
        structure.getThemeSets().add(new IrisJigsawThemeSet("variant-2", 1));

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertCodes(result, StructureGraphDiagnostic.Code.UNSATISFIABLE_PIECE_RULE);
    }

    @Test
    public void terminalPiecesDoNotMakeTheirOutgoingPoolsReachable() {
        InMemoryResolver resolver = connectorGraph(true, IrisDirection.SOUTH_POSITIVE_Z);
        resolver.pieces.get("pieces/start").setRules(
                new IrisJigsawPieceRules().setTerminal(true));

        StructureGraphCompilation result = StructureGraphCompiler.compile(
                structure("pools/start"), resolver);

        assertTrue(result.getGraph().getReachablePieces().contains("pieces/start"));
        assertFalse(result.getGraph().getReachablePieces().contains("pieces/target"));
        assertCodes(result, StructureGraphDiagnostic.Code.UNREACHABLE_POOL);
    }

    @Test
    public void requiredCapsNeedACompatibleTerminalInTheDirectFallback() {
        InMemoryResolver resolver = connectorGraph(true, IrisDirection.SOUTH_POSITIVE_Z);
        resolver.pools.get("pools/target").setFallback("pools/fallback");
        resolver.pools.put("pools/fallback", pool(entry("pieces/target", 1)));
        IrisStructure structure = structure("pools/start").setRequireCaps(true);

        StructureGraphCompilation result = StructureGraphCompiler.compile(structure, resolver);

        assertCodes(result, StructureGraphDiagnostic.Code.NO_REQUIRED_TERMINAL);
    }

    private static InMemoryResolver connectorGraph(boolean rotatable, IrisDirection targetDirection) {
        InMemoryResolver resolver = new InMemoryResolver();
        IrisJigsawConnector source = connector(
                new IrisPosition(1, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                "pools/target", "source", "door");
        IrisJigsawConnector target = connector(
                canonicalPosition(targetDirection), targetDirection,
                "pools/target", "door", "unrelated-target");
        IrisJigsawPiece targetPiece = piece("objects/target", target);
        targetPiece.setRotatable(rotatable);
        resolver.objects.put("objects/start", object());
        resolver.objects.put("objects/target", object());
        IrisJigsawPiece startPiece = piece("objects/start", source);
        startPiece.setRotatable(false);
        resolver.pieces.put("pieces/start", startPiece);
        resolver.pieces.put("pieces/target", targetPiece);
        resolver.pools.put("pools/start", pool(entry("pieces/start", 1)));
        resolver.pools.put("pools/target", pool(entry("pieces/target", 1)));
        return resolver;
    }

    private static IrisStructure structure(String startPool) {
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("structures/test");
        structure.setStartPool(startPool);
        structure.setMaxDepth(3);
        structure.setMaxSizeChunks(4);
        return structure;
    }

    private static IrisObject object() {
        return new IrisObject(3, 3, 3);
    }

    private static IrisPosition canonicalPosition(IrisDirection direction) {
        return switch (direction) {
            case NORTH_NEGATIVE_Z -> new IrisPosition(1, 1, 0);
            case EAST_POSITIVE_X -> new IrisPosition(2, 1, 1);
            case SOUTH_POSITIVE_Z -> new IrisPosition(1, 1, 2);
            case WEST_NEGATIVE_X -> new IrisPosition(0, 1, 1);
            case UP_POSITIVE_Y -> new IrisPosition(1, 2, 1);
            case DOWN_NEGATIVE_Y -> new IrisPosition(1, 0, 1);
        };
    }

    private static IrisJigsawPiece piece(String object, IrisJigsawConnector... connectors) {
        IrisJigsawPiece piece = new IrisJigsawPiece();
        piece.setObject(object);
        piece.setConnectors(new KList<>(connectors));
        return piece;
    }

    private static IrisJigsawPool pool(IrisJigsawPieceEntry... entries) {
        IrisJigsawPool pool = new IrisJigsawPool();
        pool.setPieces(new KList<>(entries));
        return pool;
    }

    private static IrisJigsawPieceEntry entry(String piece, int weight) {
        return new IrisJigsawPieceEntry().setPiece(piece).setWeight(weight);
    }

    private static IrisJigsawPieceEntry emptyEntry(int weight) {
        return new IrisJigsawPieceEntry().setEmpty(true).setWeight(weight);
    }

    private static IrisJigsawConnector connector(IrisPosition position, IrisDirection direction, String pool,
                                                 String name, String targetName) {
        return new IrisJigsawConnector()
                .setPosition(position)
                .setDirection(direction)
                .setPool(pool)
                .setName(name)
                .setTargetName(targetName);
    }

    private static void assertCodes(StructureGraphCompilation result, StructureGraphDiagnostic.Code... expected) {
        List<StructureGraphDiagnostic.Code> actual = new ArrayList<>();
        for (StructureGraphDiagnostic diagnostic : result.getDiagnostics()) {
            if (!actual.contains(diagnostic.code())) {
                actual.add(diagnostic.code());
            }
        }
        for (StructureGraphDiagnostic.Code code : expected) {
            assertTrue("Missing diagnostic " + code + " in " + actual, actual.contains(code));
        }
    }

    private static boolean hasCode(StructureGraphCompilation result, StructureGraphDiagnostic.Code code) {
        for (StructureGraphDiagnostic diagnostic : result.getDiagnostics()) {
            if (diagnostic.code() == code) {
                return true;
            }
        }
        return false;
    }

    private static final class InMemoryResolver implements StructureGraphResolver {
        private final Map<String, IrisJigsawPool> pools = new LinkedHashMap<>();
        private final Map<String, IrisJigsawPiece> pieces = new LinkedHashMap<>();
        private final Map<String, IrisObject> objects = new LinkedHashMap<>();

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
}
