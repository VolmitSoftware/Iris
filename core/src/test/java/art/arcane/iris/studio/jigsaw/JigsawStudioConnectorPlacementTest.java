package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.LocalPosition;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Box;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.BlockClick;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Cell;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.CellContent;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.CellContents;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Decision;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Frame;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Identity;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Operation;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Ray;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.ReseatResult;
import art.arcane.iris.studio.jigsaw.JigsawStudioConnectorPlacement.Target;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioConnectorPlacementTest {
    private static final Box BOX = new Box(16, 65, 16, 20, 69, 20);
    private static final CellContents EMPTY = position -> Optional.empty();
    private static final Identity SPATIAL = new Identity("demo/pieces", "iris:spatial", "iris:spatial", JigsawJoint.ROLLABLE);

    @Test
    public void clickedBlockInsideTheVariantBecomesTheConnectorFacingOutOfTheClickedFace() {
        Optional<Target> target = JigsawStudioConnectorPlacement.target(
                BOX,
                new Ray(18.5D, 67.5D, 25.0D, 0.0D, 0.0D, -1.0D),
                Optional.of(new BlockClick(18, 67, 20, IrisDirection.SOUTH_POSITIVE_Z)),
                Set.of(),
                false);

        assertEquals(Optional.of(new Target(18, 67, 20, IrisDirection.SOUTH_POSITIVE_Z)), target);
    }

    @Test
    public void sneakingFacesTheConnectorIntoTheClickedFace() {
        Optional<Target> target = JigsawStudioConnectorPlacement.target(
                BOX,
                new Ray(18.5D, 67.5D, 25.0D, 0.0D, 0.0D, -1.0D),
                Optional.of(new BlockClick(18, 67, 20, IrisDirection.SOUTH_POSITIVE_Z)),
                Set.of(),
                true);

        assertEquals(Optional.of(new Target(18, 67, 20, IrisDirection.NORTH_NEGATIVE_Z)), target);
    }

    @Test
    public void clickingTheStudioFloorPlacesADownConnectorOnTheBottomLayer() {
        Optional<Target> target = JigsawStudioConnectorPlacement.target(
                BOX,
                new Ray(18.5D, 70.0D, 18.5D, 0.0D, -1.0D, 0.0D),
                Optional.of(new BlockClick(18, 64, 18, IrisDirection.UP_POSITIVE_Y)),
                Set.of(),
                false);

        assertEquals(Optional.of(new Target(18, 65, 18, IrisDirection.DOWN_NEGATIVE_Y)), target);
    }

    @Test
    public void pointingAtAirFromInsideUsesTheBoundaryCellWhereTheRayLeaves() {
        Optional<Target> target = JigsawStudioConnectorPlacement.target(
                BOX,
                new Ray(18.5D, 67.5D, 18.5D, 0.0D, 0.0D, -1.0D),
                Optional.empty(),
                Set.of(),
                false);

        assertEquals(Optional.of(new Target(18, 67, 16, IrisDirection.NORTH_NEGATIVE_Z)), target);
    }

    @Test
    public void pointingAtAirFromOutsideUsesTheBoundaryCellWhereTheRayEnters() {
        Optional<Target> target = JigsawStudioConnectorPlacement.target(
                BOX,
                new Ray(18.5D, 67.5D, 30.0D, 0.0D, 0.0D, -1.0D),
                Optional.empty(),
                Set.of(),
                false);

        assertEquals(Optional.of(new Target(18, 67, 20, IrisDirection.SOUTH_POSITIVE_Z)), target);
    }

    @Test
    public void connectorCellsInFrontOfTheClickedBlockWinSoHiddenConnectorsStaySelectable() {
        Optional<Target> target = JigsawStudioConnectorPlacement.target(
                BOX,
                new Ray(18.5D, 67.5D, 18.5D, 0.0D, 0.0D, -1.0D),
                Optional.of(new BlockClick(18, 67, 16, IrisDirection.SOUTH_POSITIVE_Z)),
                Set.of(new Cell(18, 67, 17)),
                false);

        assertEquals(Optional.of(new Target(18, 67, 17, IrisDirection.SOUTH_POSITIVE_Z)), target);
    }

    @Test
    public void raysThatMissTheVariantResolveNothing() {
        Optional<Target> target = JigsawStudioConnectorPlacement.target(
                BOX,
                new Ray(30.5D, 67.5D, 30.5D, 0.0D, 0.0D, 1.0D),
                Optional.empty(),
                Set.of(),
                false);

        assertTrue(target.isEmpty());
    }

    @Test
    public void spatialRightClicksAddSelectMoveReorientAndDeselect() {
        LocalPosition door = new LocalPosition(2, 1, 0);
        LocalPosition other = new LocalPosition(4, 1, 2);
        LocalPosition empty = new LocalPosition(0, 1, 2);
        Map<LocalPosition, IrisDirection> connectors = Map.of(
                door, IrisDirection.NORTH_NEGATIVE_Z,
                other, IrisDirection.EAST_POSITIVE_X);

        assertEquals(Operation.ADD, spatial(false, empty, IrisDirection.WEST_NEGATIVE_X, connectors, null));
        assertEquals(Operation.SELECT, spatial(false, door, IrisDirection.NORTH_NEGATIVE_Z, connectors, null));
        assertEquals(Operation.MOVE, spatial(false, empty, IrisDirection.WEST_NEGATIVE_X, connectors, door));
        assertEquals(Operation.DESELECT, spatial(false, door, IrisDirection.NORTH_NEGATIVE_Z, connectors, door));
        assertEquals(Operation.REORIENT, spatial(false, door, IrisDirection.UP_POSITIVE_Y, connectors, door));
        assertEquals(Operation.SELECT, spatial(false, other, IrisDirection.EAST_POSITIVE_X, connectors, door));
        assertEquals(Operation.ADD, spatial(false, empty, IrisDirection.WEST_NEGATIVE_X, connectors, empty));
    }

    @Test
    public void spatialLeftClicksRemoveOrClearTheSelection() {
        LocalPosition door = new LocalPosition(2, 1, 0);
        Map<LocalPosition, IrisDirection> connectors = Map.of(door, IrisDirection.NORTH_NEGATIVE_Z);
        LocalPosition empty = new LocalPosition(1, 1, 1);

        assertEquals(Operation.REMOVE, spatial(true, door, IrisDirection.NORTH_NEGATIVE_Z, connectors, null));
        assertEquals(Operation.CLEAR_SELECTION, spatial(true, empty, IrisDirection.UP_POSITIVE_Y, connectors, door));
        assertEquals(Operation.NONE, spatial(true, empty, IrisDirection.UP_POSITIVE_Y, connectors, null));
    }

    @Test
    public void planarRightClicksReseatOnlyTheWorkcellsTopologySides() {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(5, 5, 5);
        Map<LocalPosition, IrisDirection> connectors = Map.of();

        Decision east = JigsawStudioConnectorPlacement.decidePlanar(false, new LocalPosition(4, 2, 2),
                IrisDirection.EAST_POSITIVE_X, dimensions, JigsawPlanarTopology.NORTH_EAST_WEST_TEE, connectors, "T Junction");
        Decision south = JigsawStudioConnectorPlacement.decidePlanar(false, new LocalPosition(2, 2, 4),
                IrisDirection.SOUTH_POSITIVE_Z, dimensions, JigsawPlanarTopology.NORTH_EAST_WEST_TEE, connectors, "T Junction");
        Decision floor = JigsawStudioConnectorPlacement.decidePlanar(false, new LocalPosition(0, 0, 3),
                IrisDirection.DOWN_NEGATIVE_Y, dimensions, JigsawPlanarTopology.NORTH_EAST_WEST_TEE, connectors, "T Junction");

        assertEquals(Operation.RESEAT, east.operation());
        assertEquals(JigsawPlanarDirection.EAST, east.side());
        assertEquals(Operation.REFUSE, south.operation());
        assertTrue(south.message().contains("north, east, and west"));
        assertEquals(Operation.RESEAT, floor.operation());
        assertEquals(JigsawPlanarDirection.WEST, floor.side());
    }

    @Test
    public void planarLeftClicksRefuseTheOnlySocketOfAConnectedSideButRemoveStrays() {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(5, 5, 5);
        LocalPosition socket = new LocalPosition(2, 2, 0);
        LocalPosition stray = new LocalPosition(1, 2, 0);
        Map<LocalPosition, IrisDirection> single = Map.of(socket, IrisDirection.NORTH_NEGATIVE_Z);
        Map<LocalPosition, IrisDirection> doubled = Map.of(
                socket, IrisDirection.NORTH_NEGATIVE_Z,
                stray, IrisDirection.NORTH_NEGATIVE_Z);

        Decision refused = JigsawStudioConnectorPlacement.decidePlanar(true, socket,
                IrisDirection.NORTH_NEGATIVE_Z, dimensions, JigsawPlanarTopology.NORTH_END, single, "End Cap");
        Decision removed = JigsawStudioConnectorPlacement.decidePlanar(true, stray,
                IrisDirection.NORTH_NEGATIVE_Z, dimensions, JigsawPlanarTopology.NORTH_END, doubled, "End Cap");

        assertEquals(Operation.REFUSE, refused.operation());
        assertEquals(Operation.REMOVE, removed.operation());
    }

    @Test
    public void framesRoundTripPositionsAndDirectionsForEveryQuarterTurn() {
        JigsawStudioCellDimensions source = new JigsawStudioCellDimensions(6, 3, 4);
        for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
            Frame frame = new Frame(source, quarterTurns);
            for (int x = 0; x < 6; x++) {
                for (int z = 0; z < 4; z++) {
                    IrisPosition position = new IrisPosition(x, 1, z);
                    LocalPosition display = frame.display(position);
                    JigsawStudioCellDimensions dimensions = frame.displayDimensions();
                    assertTrue(display.x() >= 0 && display.x() < dimensions.width());
                    assertTrue(display.z() >= 0 && display.z() < dimensions.depth());
                    assertEquals(position, frame.source(display));
                }
            }
            for (IrisDirection direction : IrisDirection.values()) {
                assertEquals(direction, frame.sourceDirection(frame.displayDirection(direction)));
            }
        }
    }

    @Test
    public void addConvertsDisplayPlacementIntoSourceSpaceWithDefaultIdentity() {
        Frame frame = new Frame(new JigsawStudioCellDimensions(6, 3, 4), 1);
        LocalPosition target = new LocalPosition(0, 1, 2);

        List<IrisJigsawConnector> added = JigsawStudioConnectorPlacement.add(
                List.of(), frame, target, IrisDirection.WEST_NEGATIVE_X, IrisDirection.UP_POSITIVE_Y, SPATIAL, EMPTY);

        IrisJigsawConnector connector = added.getFirst();
        assertEquals(target, frame.display(connector.getPosition()));
        assertEquals(IrisDirection.WEST_NEGATIVE_X, frame.displayDirection(connector.getDirection()));
        assertEquals(IrisDirection.UP_POSITIVE_Y, connector.getTop());
        assertEquals("demo/pieces", connector.getPool());
        assertEquals("iris:spatial", connector.getName());
        assertEquals("iris:spatial", connector.getTargetName());
        assertEquals(JigsawJoint.ROLLABLE, connector.getJoint());
        assertEquals("minecraft:structure_void", connector.getFinalState());
        assertEquals("", connector.getChannel());
    }

    @Test
    public void connectorsPlacedOnBuiltBlocksKeepThatBlockAsTheirFinalState() {
        Frame frame = new Frame(new JigsawStudioCellDimensions(5, 5, 5), 0);
        CellContents wall = position -> Optional.of(new CellContent("minecraft:stone_bricks", false));
        CellContents chest = position -> Optional.of(new CellContent("minecraft:chest[facing=north]", true));

        List<IrisJigsawConnector> added = JigsawStudioConnectorPlacement.add(
                List.of(), frame, new LocalPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                IrisDirection.UP_POSITIVE_Y, SPATIAL, wall);

        assertEquals("minecraft:stone_bricks", added.getFirst().getFinalState());
        assertThrows(IllegalArgumentException.class, () -> JigsawStudioConnectorPlacement.add(
                List.of(), frame, new LocalPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                IrisDirection.UP_POSITIVE_Y, SPATIAL, chest));
    }

    @Test
    public void addingOntoAnExistingConnectorIsRejected() {
        Frame frame = new Frame(new JigsawStudioCellDimensions(5, 5, 5), 0);
        List<IrisJigsawConnector> source = List.of(connector(new IrisPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z));

        assertThrows(IllegalArgumentException.class, () -> JigsawStudioConnectorPlacement.add(
                source, frame, new LocalPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z,
                IrisDirection.UP_POSITIVE_Y, SPATIAL, EMPTY));
    }

    @Test
    public void moveKeepsIdentityAndOrderAndOnlyReplacesTheFinalStateOnBuiltBlocks() {
        Frame frame = new Frame(new JigsawStudioCellDimensions(5, 5, 5), 0);
        IrisJigsawConnector first = connector(new IrisPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z)
                .setChannel("hall").setFinalState("minecraft:air");
        IrisJigsawConnector second = connector(new IrisPosition(4, 1, 2), IrisDirection.EAST_POSITIVE_X);
        List<IrisJigsawConnector> source = List.of(first, second);

        List<IrisJigsawConnector> moved = JigsawStudioConnectorPlacement.move(
                source, frame, new LocalPosition(2, 1, 0), new LocalPosition(0, 1, 2),
                IrisDirection.WEST_NEGATIVE_X, IrisDirection.UP_POSITIVE_Y, EMPTY);
        List<IrisJigsawConnector> onWall = JigsawStudioConnectorPlacement.move(
                source, frame, new LocalPosition(2, 1, 0), new LocalPosition(0, 1, 2),
                IrisDirection.WEST_NEGATIVE_X, IrisDirection.UP_POSITIVE_Y,
                position -> Optional.of(new CellContent("minecraft:stone", false)));

        assertEquals(2, moved.size());
        assertEquals(new IrisPosition(0, 1, 2), moved.getFirst().getPosition());
        assertEquals(IrisDirection.WEST_NEGATIVE_X, moved.getFirst().getDirection());
        assertEquals("hall", moved.getFirst().getChannel());
        assertEquals("demo/pieces", moved.getFirst().getPool());
        assertEquals("minecraft:air", moved.getFirst().getFinalState());
        assertEquals("minecraft:stone", onWall.getFirst().getFinalState());
        assertEquals(second.getPosition(), moved.get(1).getPosition());
        assertEquals(new IrisPosition(2, 1, 0), first.getPosition());
        assertThrows(IllegalArgumentException.class, () -> JigsawStudioConnectorPlacement.move(
                source, frame, new LocalPosition(2, 1, 0), new LocalPosition(4, 1, 2),
                IrisDirection.EAST_POSITIVE_X, IrisDirection.UP_POSITIVE_Y, EMPTY));
    }

    @Test
    public void removeDropsOnlyTheConnectorAtThatDisplayPosition() {
        Frame frame = new Frame(new JigsawStudioCellDimensions(5, 5, 5), 2);
        IrisJigsawConnector north = connector(new IrisPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z);
        IrisJigsawConnector east = connector(new IrisPosition(4, 1, 2), IrisDirection.EAST_POSITIVE_X);

        List<IrisJigsawConnector> remaining = JigsawStudioConnectorPlacement.remove(
                List.of(north, east), frame, frame.display(north.getPosition()));

        assertEquals(1, remaining.size());
        assertEquals(east.getPosition(), remaining.getFirst().getPosition());
    }

    @Test
    public void reseatSnapsAMovedSocketBackToTheSourceFaceCenterAndDropsStrays() {
        JigsawStudioCellDimensions source = new JigsawStudioCellDimensions(4, 4, 6);
        Frame frame = new Frame(source, 1);
        IrisJigsawConnector moved = connector(new IrisPosition(0, 1, 0), IrisDirection.NORTH_NEGATIVE_Z).setChannel("road");
        IrisJigsawConnector stray = connector(new IrisPosition(3, 1, 0), IrisDirection.NORTH_NEGATIVE_Z);
        IrisJigsawConnector south = connector(
                IrisJigsawConnector.canonicalPlanarPosition(new IrisPosition(4, 4, 6), IrisDirection.SOUTH_POSITIVE_Z),
                IrisDirection.SOUTH_POSITIVE_Z);
        JigsawPlanarDirection displaySide = JigsawPlanarDirection.EAST;
        assertEquals(IrisDirection.NORTH_NEGATIVE_Z, frame.sourceDirection(displaySide.irisDirection()));

        ReseatResult result = JigsawStudioConnectorPlacement.reseat(
                List.of(moved, stray, south), frame, displaySide, SPATIAL, EMPTY);

        assertTrue(result.changed());
        assertEquals(1, result.removedStrays());
        assertEquals(2, result.connectors().size());
        IrisJigsawConnector seated = result.connectors().getFirst();
        assertEquals(IrisJigsawConnector.canonicalPlanarPosition(
                new IrisPosition(4, 4, 6), IrisDirection.NORTH_NEGATIVE_Z), seated.getPosition());
        assertEquals("road", seated.getChannel());
        assertEquals(south.getPosition(), result.connectors().get(1).getPosition());

        ReseatResult unchanged = JigsawStudioConnectorPlacement.reseat(
                result.connectors(), frame, displaySide, SPATIAL, EMPTY);
        assertFalse(unchanged.changed());
    }

    @Test
    public void reseatAddsAMissingSocketWithTheDefaultIdentity() {
        Frame frame = new Frame(new JigsawStudioCellDimensions(5, 5, 5), 0);
        Identity planar = new Identity("demo/pieces", "iris:planar", "iris:planar", JigsawJoint.ALIGNED);

        ReseatResult result = JigsawStudioConnectorPlacement.reseat(
                List.of(), frame, JigsawPlanarDirection.WEST, planar, EMPTY);

        assertTrue(result.changed());
        IrisJigsawConnector added = result.connectors().getFirst();
        assertEquals(new IrisPosition(0, 2, 2), added.getPosition());
        assertEquals(IrisDirection.WEST_NEGATIVE_X, added.getDirection());
        assertEquals(IrisDirection.UP_POSITIVE_Y, added.getTop());
        assertEquals(JigsawJoint.ALIGNED, added.getJoint());
        assertEquals("iris:planar", added.getName());
    }

    @Test
    public void changedPositionsListEveryDisplayCellWhoseConnectorDiffers() {
        Frame frame = new Frame(new JigsawStudioCellDimensions(5, 5, 5), 0);
        IrisJigsawConnector north = connector(new IrisPosition(2, 1, 0), IrisDirection.NORTH_NEGATIVE_Z);
        IrisJigsawConnector east = connector(new IrisPosition(4, 1, 2), IrisDirection.EAST_POSITIVE_X);
        List<IrisJigsawConnector> before = List.of(north, east);
        List<IrisJigsawConnector> after = JigsawStudioConnectorPlacement.move(
                before, frame, new LocalPosition(2, 1, 0), new LocalPosition(0, 1, 2),
                IrisDirection.WEST_NEGATIVE_X, IrisDirection.UP_POSITIVE_Y, EMPTY);

        Set<LocalPosition> changed = JigsawStudioConnectorPlacement.changedPositions(before, after, frame);

        assertEquals(Set.of(new LocalPosition(2, 1, 0), new LocalPosition(0, 1, 2)), changed);
    }

    private static Operation spatial(
            boolean primary,
            LocalPosition target,
            IrisDirection facing,
            Map<LocalPosition, IrisDirection> connectors,
            LocalPosition selection
    ) {
        return JigsawStudioConnectorPlacement.decideSpatial(
                primary, target, facing, new LinkedHashMap<>(connectors), Optional.ofNullable(selection)).operation();
    }

    private static IrisJigsawConnector connector(IrisPosition position, IrisDirection direction) {
        return new IrisJigsawConnector()
                .setPosition(position)
                .setDirection(direction)
                .setTop(IrisDirection.UP_POSITIVE_Y)
                .setPool("demo/pieces")
                .setName("iris:spatial")
                .setTargetName("iris:spatial")
                .setJoint(JigsawJoint.ROLLABLE)
                .setFinalState("minecraft:structure_void");
    }
}
