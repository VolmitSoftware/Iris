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

package art.arcane.iris.structure.placement;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.graph.StructureAssemblyResult;
import art.arcane.iris.structure.graph.StructureAssemblyStatus;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.math.RNG;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructureAssemblerTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    public void attachedNonRotatablePieceKeepsItsAuthoredFacing() {
        Fixture fixture = new Fixture();
        IrisJigsawConnector startConnector = connector(IrisDirection.EAST_POSITIVE_X, "target", "start", "door");
        IrisJigsawPiece startPiece = piece("start-object", false, startConnector);
        IrisJigsawConnector candidateConnector = connector(IrisDirection.NORTH_NEGATIVE_Z, "", "door", "");
        IrisJigsawPiece candidatePiece = piece("candidate-object", false, candidateConnector);
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("target", pool("", "candidate-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("candidate-piece", candidatePiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("candidate-object", new IrisObject(1, 1, 1));

        StructureAssemblyResult fixed = fixture.assembler(
                structure("start", 1), 0, 0, 0).assemble(new RNG(11L));

        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED, fixed.status());

        candidatePiece.setRotatable(true);
        List<PlacedStructurePiece> rotatable = completedPieces(
                fixture.assembler(structure("start", 1), 0, 0, 0), new RNG(11L));

        assertEquals(2, rotatable.size());
        assertSame(candidatePiece, rotatable.get(1).getPiece());
    }

    @Test
    public void fallbackCandidatesAreTriedAfterPrimaryCandidatesFail() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "branch", "start", "door"));
        IrisJigsawPiece incompatiblePiece = piece("incompatible-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "wrong", ""));
        IrisJigsawPiece fallbackPiece = piece("fallback-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("branch", pool("fallback", "incompatible-piece"));
        fixture.pool("fallback", pool("", "fallback-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("incompatible-piece", incompatiblePiece);
        fixture.piece("fallback-piece", fallbackPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("incompatible-object", new IrisObject(1, 1, 1));
        fixture.object("fallback-object", new IrisObject(1, 1, 1));

        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure("start", 2), 0, 0, 0), new RNG(29L));

        assertEquals(2, placed.size());
        assertSame(fallbackPiece, placed.get(1).getPiece());

        List<PlacedStructurePiece> vanillaPolicyPlaced = completedPieces(
                fixture.assembler(structure("start", 2)
                        .setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH),
                        0, 0, 0), new RNG(29L));

        assertEquals(2, vanillaPolicyPlaced.size());
        assertSame(fallbackPiece, vanillaPolicyPlaced.get(1).getPiece());
    }

    @Test
    public void branchTerminationPolicyEndsOnlyAnUnmatchedOptionalBranch() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "target", "start", "door"),
                connector(IrisDirection.SOUTH_POSITIVE_Z, "sibling", "start", "hall"));
        IrisJigsawPiece incompatiblePiece = piece("incompatible-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "wrong", ""));
        IrisJigsawPiece siblingPiece = piece("sibling-object", false,
                connector(IrisDirection.NORTH_NEGATIVE_Z, "", "hall", ""));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("target", pool("", "incompatible-piece"));
        fixture.pool("sibling", pool("", "sibling-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("incompatible-piece", incompatiblePiece);
        fixture.piece("sibling-piece", siblingPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("incompatible-object", new IrisObject(1, 1, 1));
        fixture.object("sibling-object", new IrisObject(1, 1, 1));

        StructureAssemblyResult strict = fixture.assembler(
                structure("start", 2), 0, 0, 0).assemble(new RNG(17L));
        StructureAssemblyResult terminating = fixture.assembler(
                structure("start", 2)
                        .setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH),
                0, 0, 0).assemble(new RNG(17L));

        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED, strict.status());
        assertEquals(StructureAssemblyStatus.COMPLETE, terminating.status());
        assertEquals(2, terminating.pieces().size());
        assertSame(startPiece, terminating.pieces().getFirst().getPiece());
        assertSame(siblingPiece, terminating.pieces().get(1).getPiece());
    }

    @Test
    public void requiredFallbackOverridesBranchTerminationPolicy() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "target", "start", "door"));
        IrisJigsawPiece capPiece = piece("cap-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", "unused"));
        capPiece.setRules(new IrisJigsawPieceRules().setTerminal(true));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("target", pool("caps"));
        fixture.pool("caps", pool("", "cap-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("cap-piece", capPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("cap-object", new IrisObject(64, 1, 1));
        IrisStructure structure = structure("start", 2)
                .setMaxSizeChunks(1)
                .setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH);

        StructureAssemblyResult structureRequired = fixture.assembler(
                structure.setRequireCaps(true), 0, 0, 0).assemble(new RNG(19L));
        structure.setRequireCaps(false);
        fixture.pool("target", pool("caps").setMandatoryFallback(true));
        StructureAssemblyResult poolRequired = fixture.assembler(
                structure, 0, 0, 0).assemble(new RNG(19L));

        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED, structureRequired.status());
        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED, poolRequired.status());
    }

    @Test
    public void explicitEmptyAndEmptyPrimaryPoolsCutOffDirectFallback() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "target", "start", "door"));
        IrisJigsawPiece fallbackPiece = piece("fallback-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        IrisJigsawPool explicitEmpty = pool("fallback");
        explicitEmpty.getPieces().add(new IrisJigsawPieceEntry().setEmpty(true).setWeight(1));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("target", explicitEmpty);
        fixture.pool("fallback", pool("", "fallback-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("fallback-piece", fallbackPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("fallback-object", new IrisObject(1, 1, 1));
        IrisStructure structure = structure("start", 2)
                .setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH);

        StructureAssemblyResult explicit = fixture.assembler(
                structure, 0, 0, 0).assemble(firstChoiceRng());
        fixture.pool("target", pool("fallback"));
        StructureAssemblyResult zeroEntry = fixture.assembler(
                structure, 0, 0, 0).assemble(firstChoiceRng());

        assertEquals(StructureAssemblyStatus.COMPLETE, explicit.status());
        assertEquals(1, explicit.pieces().size());
        assertEquals(StructureAssemblyStatus.COMPLETE, zeroEntry.status());
        assertEquals(1, zeroEntry.pieces().size());
    }

    @Test
    public void alignedRuntimeConnectorsPreserveTopOrientation() {
        Fixture fixture = new Fixture();
        IrisJigsawConnector source = connector(IrisDirection.EAST_POSITIVE_X, "target", "start", "door")
                .setJoint(JigsawJoint.ALIGNED)
                .setTop(IrisDirection.UP_POSITIVE_Y);
        IrisJigsawConnector target = connector(IrisDirection.WEST_NEGATIVE_X, "target", "door", "unused")
                .setTop(IrisDirection.DOWN_NEGATIVE_Y);
        IrisJigsawPiece startPiece = piece("start-object", false, source);
        IrisJigsawPiece targetPiece = piece("target-object", false, target);
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("target", pool("", "target-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("target-piece", targetPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("target-object", new IrisObject(1, 1, 1));

        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED,
                fixture.assembler(structure("start", 1), 0, 0, 0)
                        .assemble(new RNG(13L)).status());

        target.setTop(IrisDirection.UP_POSITIVE_Y);
        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure("start", 1), 0, 0, 0), new RNG(13L));
        assertEquals(2, placed.size());
    }

    @Test
    public void planarCanonicalSocketsPlaceEveryNeighborOnTheExactCellGrid() {
        Fixture fixture = new Fixture();
        IrisJigsawConnector northSource = connector(
                IrisDirection.NORTH_NEGATIVE_Z, "north", "start", "door")
                .setPosition(new IrisPosition(8, 4, 0));
        IrisJigsawConnector eastSource = connector(
                IrisDirection.EAST_POSITIVE_X, "east", "start", "door")
                .setPosition(new IrisPosition(15, 4, 8));
        IrisJigsawConnector southSource = connector(
                IrisDirection.SOUTH_POSITIVE_Z, "south", "start", "door")
                .setPosition(new IrisPosition(8, 4, 15));
        IrisJigsawConnector westSource = connector(
                IrisDirection.WEST_NEGATIVE_X, "west", "start", "door")
                .setPosition(new IrisPosition(0, 4, 8));
        IrisJigsawPiece startPiece = piece(
                "start-object", false, northSource, eastSource, southSource, westSource);
        IrisJigsawPiece northPiece = piece("north-object", false,
                connector(IrisDirection.SOUTH_POSITIVE_Z, "", "door", "")
                        .setPosition(new IrisPosition(8, 4, 15)));
        IrisJigsawPiece eastPiece = piece("east-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", "")
                        .setPosition(new IrisPosition(0, 4, 8)));
        IrisJigsawPiece southPiece = piece("south-object", false,
                connector(IrisDirection.NORTH_NEGATIVE_Z, "", "door", "")
                        .setPosition(new IrisPosition(8, 4, 0)));
        IrisJigsawPiece westPiece = piece("west-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "", "door", "")
                        .setPosition(new IrisPosition(15, 4, 8)));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("north", pool("", "north-piece"));
        fixture.pool("east", pool("", "east-piece"));
        fixture.pool("south", pool("", "south-piece"));
        fixture.pool("west", pool("", "west-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("north-piece", northPiece);
        fixture.piece("east-piece", eastPiece);
        fixture.piece("south-piece", southPiece);
        fixture.piece("west-piece", westPiece);
        fixture.object("start-object", new IrisObject(16, 8, 16));
        fixture.object("north-object", new IrisObject(16, 8, 16));
        fixture.object("east-object", new IrisObject(16, 8, 16));
        fixture.object("south-object", new IrisObject(16, 8, 16));
        fixture.object("west-object", new IrisObject(16, 8, 16));
        IrisStructure structure = structure("start", 1)
                .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                .setCellSize(new IrisPosition(16, 8, 16));

        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure, 32, 64, -48), new RNG(127L));
        PlacedStructurePiece start = requirePlacedPiece(placed, startPiece);
        PlacedStructurePiece north = requirePlacedPiece(placed, northPiece);
        PlacedStructurePiece east = requirePlacedPiece(placed, eastPiece);
        PlacedStructurePiece south = requirePlacedPiece(placed, southPiece);
        PlacedStructurePiece west = requirePlacedPiece(placed, westPiece);

        assertEquals(5, placed.size());
        assertEquals(32, north.getX());
        assertEquals(64, north.getY());
        assertEquals(-64, north.getZ());
        assertEquals(48, east.getX());
        assertEquals(64, east.getY());
        assertEquals(-48, east.getZ());
        assertEquals(32, south.getX());
        assertEquals(64, south.getY());
        assertEquals(-32, south.getZ());
        assertEquals(16, west.getX());
        assertEquals(64, west.getY());
        assertEquals(-48, west.getZ());
        assertEquals(north.getMaxZ() + 1, start.getMinZ());
        assertEquals(start.getMaxX() + 1, east.getMinX());
        assertEquals(start.getMaxZ() + 1, south.getMinZ());
        assertEquals(west.getMaxX() + 1, start.getMinX());
    }

    @Test
    public void selectionPriorityProcessesHigherConnectorsFirstAndPreservesStableTies() {
        Fixture fixture = new Fixture();
        IrisJigsawConnector eastSource = connector(
                IrisDirection.EAST_POSITIVE_X, "east", "start", "door").setSelectionPriority(-20);
        IrisJigsawConnector westSource = connector(
                IrisDirection.WEST_NEGATIVE_X, "west", "start", "door").setSelectionPriority(-10);
        IrisJigsawPiece startPiece = piece("start-object", false, eastSource, westSource);
        IrisJigsawPiece eastPiece = piece("east-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        IrisJigsawPiece westPiece = piece("west-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "", "door", ""));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("east", pool("", "east-piece"));
        fixture.pool("west", pool("", "west-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("east-piece", eastPiece);
        fixture.piece("west-piece", westPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("east-object", new IrisObject(1, 1, 1));
        fixture.object("west-object", new IrisObject(1, 1, 1));
        IrisStructure structure = structure("start", 1);

        List<PlacedStructurePiece> prioritized = completedPieces(
                fixture.assembler(structure, 0, 0, 0), new RNG(101L));

        assertEquals(3, prioritized.size());
        assertSame(westPiece, prioritized.get(1).getPiece());
        assertSame(eastPiece, prioritized.get(2).getPiece());

        eastSource.setSelectionPriority(-10);
        List<PlacedStructurePiece> tied = completedPieces(
                fixture.assembler(structure, 0, 0, 0), new RNG(101L));

        assertSame(eastPiece, tied.get(1).getPiece());
        assertSame(westPiece, tied.get(2).getPiece());
    }

    @Test
    public void selectionPriorityChoosesHigherCandidateConnectorAndPreservesStableTies() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "target", "start", "door"));
        IrisJigsawConnector low = connector(IrisDirection.WEST_NEGATIVE_X, "", "door", "")
                .setPosition(new IrisPosition(0, 0, 0))
                .setSelectionPriority(-20);
        IrisJigsawConnector high = connector(IrisDirection.WEST_NEGATIVE_X, "", "door", "")
                .setPosition(new IrisPosition(0, 0, 2))
                .setSelectionPriority(-10);
        IrisJigsawPiece targetPiece = piece("target-object", false, low, high);
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("target", pool("", "target-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("target-piece", targetPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("target-object", new IrisObject(1, 1, 3));
        IrisStructure structure = structure("start", 1);

        List<PlacedStructurePiece> prioritized = completedPieces(
                fixture.assembler(structure, 0, 0, 0), new RNG(103L));

        assertEquals(-1, prioritized.get(1).getZ());

        low.setSelectionPriority(-10);
        List<PlacedStructurePiece> tied = completedPieces(
                fixture.assembler(structure, 0, 0, 0), new RNG(103L));

        assertEquals(1, tied.get(1).getZ());
    }

    @Test
    public void placementPrioritySchedulesChildExpansionAfterTheCurrentPieceAndPreservesStableTies() {
        Fixture fixture = new Fixture();
        IrisJigsawConnector eastSource = connector(
                IrisDirection.EAST_POSITIVE_X, "east", "start", "door").setPlacementPriority(-20);
        IrisJigsawConnector westSource = connector(
                IrisDirection.WEST_NEGATIVE_X, "west", "start", "door").setPlacementPriority(-10);
        IrisJigsawPiece startPiece = piece("start-object", false, eastSource, westSource);
        IrisJigsawPiece eastPiece = piece("east-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""),
                connector(IrisDirection.EAST_POSITIVE_X, "east-leaf", "east", "leaf"));
        IrisJigsawPiece westPiece = piece("west-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "", "door", ""),
                connector(IrisDirection.WEST_NEGATIVE_X, "west-leaf", "west", "leaf"));
        IrisJigsawPiece eastLeaf = piece("east-leaf-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "leaf", ""));
        IrisJigsawPiece westLeaf = piece("west-leaf-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "", "leaf", ""));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("east", pool("", "east-piece"));
        fixture.pool("west", pool("", "west-piece"));
        fixture.pool("east-leaf", pool("", "east-leaf-piece"));
        fixture.pool("west-leaf", pool("", "west-leaf-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("east-piece", eastPiece);
        fixture.piece("west-piece", westPiece);
        fixture.piece("east-leaf-piece", eastLeaf);
        fixture.piece("west-leaf-piece", westLeaf);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("east-object", new IrisObject(1, 1, 1));
        fixture.object("west-object", new IrisObject(1, 1, 1));
        fixture.object("east-leaf-object", new IrisObject(1, 1, 1));
        fixture.object("west-leaf-object", new IrisObject(1, 1, 1));
        IrisStructure structure = structure("start", 2);

        List<PlacedStructurePiece> prioritized = completedPieces(
                fixture.assembler(structure, 0, 0, 0), new RNG(109L));

        assertEquals(5, prioritized.size());
        assertSame(eastPiece, prioritized.get(1).getPiece());
        assertSame(westPiece, prioritized.get(2).getPiece());
        assertSame(westLeaf, prioritized.get(3).getPiece());
        assertSame(eastLeaf, prioritized.get(4).getPiece());

        westSource.setPlacementPriority(-20);
        List<PlacedStructurePiece> tied = completedPieces(
                fixture.assembler(structure, 0, 0, 0), new RNG(109L));

        assertSame(eastLeaf, tied.get(3).getPiece());
        assertSame(westLeaf, tied.get(4).getPiece());
    }

    @Test
    public void connectorChannelsMustMatchExactly() {
        Fixture fixture = new Fixture();
        IrisJigsawConnector source = connector(
                IrisDirection.EAST_POSITIVE_X, "target", "start", "door").setChannel("road");
        IrisJigsawConnector target = connector(
                IrisDirection.WEST_NEGATIVE_X, "", "door", "").setChannel("room");
        IrisJigsawPiece startPiece = piece("start-object", false, source);
        IrisJigsawPiece targetPiece = piece("target-object", false, target);
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("target", pool("", "target-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("target-piece", targetPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("target-object", new IrisObject(1, 1, 1));
        IrisStructure structure = structure("start", 1);

        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED,
                fixture.assembler(structure, 0, 0, 0).assemble(new RNG(107L)).status());

        target.setChannel("Road");
        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED,
                fixture.assembler(structure, 0, 0, 0).assemble(new RNG(107L)).status());

        target.setChannel(" road");
        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED,
                fixture.assembler(structure, 0, 0, 0).assemble(new RNG(107L)).status());

        target.setChannel("road");
        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure, 0, 0, 0), new RNG(107L));

        assertEquals(2, placed.size());
    }

    @Test
    public void fallbackAtMaximumDepthDoesNotExpandAgain() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "branch", "start", "door"));
        IrisJigsawPiece branchPiece = piece("branch-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""),
                connector(IrisDirection.EAST_POSITIVE_X, "terminal", "branch", "door"));
        IrisJigsawPiece primaryTerminal = piece("primary-terminal-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        IrisJigsawPiece fallbackTerminal = piece("fallback-terminal-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""),
                connector(IrisDirection.EAST_POSITIVE_X, "terminal", "terminal", "door"));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("branch", pool("", "branch-piece"));
        fixture.pool("terminal", pool("terminal-fallback", "primary-terminal-piece"));
        fixture.pool("terminal-fallback", pool("", "fallback-terminal-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("branch-piece", branchPiece);
        fixture.piece("primary-terminal-piece", primaryTerminal);
        fixture.piece("fallback-terminal-piece", fallbackTerminal);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("branch-object", new IrisObject(1, 1, 1));
        fixture.object("primary-terminal-object", new IrisObject(1, 1, 1));
        fixture.object("fallback-terminal-object", new IrisObject(1, 1, 1));
        IrisStructure structure = structure("start", 1);
        structure.setMaxSizeChunks(1);

        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure, 0, 0, 0), new RNG(41L));

        assertEquals(3, placed.size());
        assertSame(branchPiece, placed.get(1).getPiece());
        assertSame(fallbackTerminal, placed.get(2).getPiece());
    }

    @Test
    public void fallbackOfFallbackIsNotSelected() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "branch", "start", "door"));
        IrisJigsawPiece incompatiblePiece = piece("incompatible-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "wrong", ""));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("branch", pool("terminators", "incompatible-piece"));
        fixture.pool("terminators", pool("empty", "incompatible-piece"));
        fixture.pool("empty", pool(""));
        fixture.piece("start-piece", startPiece);
        fixture.piece("incompatible-piece", incompatiblePiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("incompatible-object", new IrisObject(1, 1, 1));

        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED,
                fixture.assembler(structure("start", 2), 0, 0, 0)
                        .assemble(new RNG(31L)).status());
    }

    @Test
    public void evenSizedBoundsContainExactlyTheObjectFootprint() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false);
        fixture.pool("start", pool("", "start-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.object("start-object", new IrisObject(4, 2, 6));

        PlacedStructurePiece placed = completedPieces(
                fixture.assembler(structure("start", 1), 10, 20, 30), new RNG(53L)).getFirst();

        assertBounds(placed, 8, 19, 27, 11, 20, 32);
    }

    @Test
    public void spatialRotatedEvenSizedBoundsPreserveExistingAsymmetricCentering() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", true);
        fixture.pool("start", pool("", "start-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.object("start-object", new IrisObject(4, 2, 6));

        StructureAssembler assembler = fixture.assembler(structure("start", 1), 10, 20, 30);
        assertBounds(completedPieces(assembler, rotationIndex(1)).getFirst(), 7, 19, 29, 12, 20, 32);
        assertBounds(completedPieces(assembler, rotationIndex(2)).getFirst(), 9, 19, 28, 12, 20, 33);
        assertBounds(completedPieces(assembler, rotationIndex(3)).getFirst(), 8, 19, 28, 13, 20, 31);
    }

    @Test
    public void planarEvenRectangleQuarterTurnsKeepBoundsAndSocketsOnOneGrid() {
        for (int quarterTurn = 0; quarterTurn < 4; quarterTurn++) {
            Fixture fixture = new Fixture();
            IrisJigsawConnector source = connector(
                    IrisDirection.NORTH_NEGATIVE_Z, "target", "start", "door")
                    .setPosition(new IrisPosition(2, 1, 0))
                    .setJoint(JigsawJoint.ALIGNED);
            IrisJigsawConnector target = connector(
                    IrisDirection.SOUTH_POSITIVE_Z, "", "door", "unused")
                    .setPosition(new IrisPosition(2, 1, 5))
                    .setJoint(JigsawJoint.ALIGNED);
            IrisJigsawPiece startPiece = piece("start-object", true, source);
            IrisJigsawPiece targetPiece = piece("target-object", true, target);
            fixture.pool("start", pool("", "start-piece"));
            fixture.pool("target", pool("", "target-piece"));
            fixture.piece("start-piece", startPiece);
            fixture.piece("target-piece", targetPiece);
            fixture.object("start-object", new IrisObject(4, 2, 6));
            fixture.object("target-object", new IrisObject(4, 2, 6));
            IrisStructure structure = structure("start", 1)
                    .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                    .setCellSize(new IrisPosition(4, 2, 6));

            List<PlacedStructurePiece> pieces = completedPieces(
                    fixture.assembler(structure, 10, 20, 30), rotationIndex(quarterTurn));
            PlacedStructurePiece start = requirePlacedPiece(pieces, startPiece);
            PlacedStructurePiece neighbor = requirePlacedPiece(pieces, targetPiece);

            switch (quarterTurn) {
                case 0 -> {
                    assertBounds(start, 8, 19, 27, 11, 20, 32);
                    assertEquals(start.getMinZ(), neighbor.getMaxZ() + 1);
                    assertEquals(10, start.getX());
                    assertEquals(30, start.getZ());
                }
                case 1 -> {
                    assertBounds(start, 7, 19, 28, 12, 20, 31);
                    assertEquals(start.getMinX(), neighbor.getMaxX() + 1);
                    assertEquals(10, start.getX());
                    assertEquals(29, start.getZ());
                }
                case 2 -> {
                    assertBounds(start, 8, 19, 27, 11, 20, 32);
                    assertEquals(start.getMaxZ() + 1, neighbor.getMinZ());
                    assertEquals(9, start.getX());
                    assertEquals(29, start.getZ());
                }
                case 3 -> {
                    assertBounds(start, 7, 19, 28, 12, 20, 31);
                    assertEquals(start.getMaxX() + 1, neighbor.getMinX());
                    assertEquals(9, start.getX());
                    assertEquals(30, start.getZ());
                }
                default -> throw new AssertionError("Unexpected planar quarter turn");
            }
        }
    }

    @Test
    public void collisionUsesHalfOpenEdgesForInclusivePieceBounds() {
        PlacedStructurePiece origin = bounds(0, 0, 0, 0, 0, 0);
        PlacedStructurePiece overlap = bounds(0, 0, 0, 0, 0, 0);
        PlacedStructurePiece adjacent = bounds(1, 0, 0, 1, 0, 0);

        assertTrue(origin.intersects(overlap));
        assertFalse(origin.intersects(adjacent));
        assertFalse(adjacent.intersects(origin));
    }

    @Test
    public void collisionRequiresBothOverlappingPiecesToBeCollidable() {
        StructureAssemblyResult bothCollidable = overlappingPair(true, true);
        StructureAssemblyResult placedScaffold = overlappingPair(false, true);
        StructureAssemblyResult candidateScaffold = overlappingPair(true, false);

        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED, bothCollidable.status());
        assertEquals(1, bothCollidable.pieces().size());
        assertEquals(StructureAssemblyStatus.COMPLETE, placedScaffold.status());
        assertEquals(2, placedScaffold.pieces().size());
        assertEquals(StructureAssemblyStatus.COMPLETE, candidateScaffold.status());
        assertEquals(2, candidateScaffold.pieces().size());
    }

    @Test
    public void pillagerOutpostAirScaffoldAllowsTheWatchtowerToOverlapItsNativeBounds() {
        Fixture fixture = new Fixture();
        IrisJigsawConnector baseEntrance = connector(
                IrisDirection.NORTH_NEGATIVE_Z,
                "towers",
                "minecraft:entrance",
                "minecraft:entrance")
                .setPosition(new IrisPosition(8, 1, 14))
                .setJoint(JigsawJoint.ALIGNED);
        IrisJigsawConnector towerEntrance = connector(
                IrisDirection.SOUTH_POSITIVE_Z,
                "terminal",
                "minecraft:entrance",
                "minecraft:entrance")
                .setPosition(new IrisPosition(7, 1, 13))
                .setJoint(JigsawJoint.ALIGNED);
        IrisJigsawPiece basePlate = piece("base-plate-object", true, baseEntrance);
        IrisJigsawPiece watchtower = piece("watchtower-object", true, towerEntrance);
        fixture.pool("start", pool("terminal", "base-plate"));
        fixture.pool("towers", pool("terminal", "watchtower"));
        fixture.piece("base-plate", basePlate);
        fixture.piece("watchtower", watchtower);
        fixture.object("base-plate-object", new IrisObject(16, 30, 16));
        fixture.object("watchtower-object", new IrisObject(15, 21, 15));
        IrisStructure structure = structure("start", 1);

        StructureAssemblyResult collidable = fixture.assembler(structure, 0, 0, 0)
                .assemble(firstChoiceRng());
        basePlate.setCollidable(false);
        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure, 0, 0, 0), firstChoiceRng());
        PlacedStructurePiece placedBase = requirePlacedPiece(placed, basePlate);
        PlacedStructurePiece placedTower = requirePlacedPiece(placed, watchtower);

        assertEquals(StructureAssemblyStatus.COMPLETE, collidable.status());
        assertEquals(1, collidable.pieces().size());
        assertEquals(2, placed.size());
        assertTrue(placedBase.intersects(placedTower));
        assertEquals(-8, placedBase.getMinX());
        assertEquals(7, placedBase.getMaxX());
        assertEquals(-7, placedTower.getMinX());
        assertEquals(7, placedTower.getMaxX());
    }

    @Test
    public void maximumWeightsCannotOverflowRuntimeSelection() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece first = piece("first-object", false);
        IrisJigsawPiece second = piece("second-object", false);
        IrisJigsawPool start = new IrisJigsawPool();
        start.getPieces().add(new IrisJigsawPieceEntry("first-piece", Integer.MAX_VALUE));
        start.getPieces().add(new IrisJigsawPieceEntry("second-piece", Integer.MAX_VALUE));
        fixture.pool("start", start);
        fixture.piece("first-piece", first);
        fixture.piece("second-piece", second);
        fixture.object("first-object", new IrisObject(1, 1, 1));
        fixture.object("second-object", new IrisObject(1, 1, 1));

        for (long seed = 0; seed < 32; seed++) {
            List<PlacedStructurePiece> placed = completedPieces(
                    fixture.assembler(structure("start", 1), 0, 0, 0), new RNG(seed));
            assertEquals(1, placed.size());
        }
    }

    @Test
    public void malformedStartConnectorDataFailsLoudly() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false);
        startPiece.setConnectors(null);
        fixture.pool("start", pool("", "start-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));

        assertThrows(IllegalStateException.class,
                () -> fixture.assembler(structure("start", 1), 0, 0, 0).assemble(new RNG(3L)));
    }

    @Test
    public void corruptWeightedPeerFailsBeforeCandidateSelection() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "branch", "start", "door"));
        IrisJigsawPiece validPiece = piece("valid-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        IrisJigsawPiece fallbackPiece = piece("fallback-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("branch", pool("fallback", "valid-piece", "missing-piece"));
        fixture.pool("fallback", pool("", "fallback-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("valid-piece", validPiece);
        fixture.piece("fallback-piece", fallbackPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("valid-object", new IrisObject(1, 1, 1));
        fixture.object("fallback-object", new IrisObject(1, 1, 1));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> fixture.assembler(structure("start", 2), 0, 0, 0).assemble(new RNG(17L)));

        assertTrue(failure.getMessage().contains("branch"));
        assertTrue(failure.getMessage().contains("missing-piece"));
    }

    @Test
    public void corruptUnselectedStartEntryFailsBeforeCandidateSelection() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece validPiece = piece("valid-object", false);
        fixture.pool("start", pool("", "valid-piece", "missing-piece"));
        fixture.piece("valid-piece", validPiece);
        fixture.object("valid-object", new IrisObject(1, 1, 1));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> fixture.assembler(structure("start", 1), 0, 0, 0).assemble(new RNG(18L)));

        assertTrue(failure.getMessage().contains("start"));
        assertTrue(failure.getMessage().contains("missing-piece"));
    }

    @Test
    public void malformedNestedConnectorDoesNotFallThroughToAuthoredFallback() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "branch", "start", "door"));
        IrisJigsawPiece malformedPiece = piece("malformed-object", false);
        malformedPiece.getConnectors().add((IrisJigsawConnector) null);
        IrisJigsawPiece fallbackPiece = piece("fallback-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("branch", pool("fallback", "malformed-piece"));
        fixture.pool("fallback", pool("", "fallback-piece"));
        fixture.piece("start-piece", startPiece);
        fixture.piece("malformed-piece", malformedPiece);
        fixture.piece("fallback-piece", fallbackPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("malformed-object", new IrisObject(1, 1, 1));
        fixture.object("fallback-object", new IrisObject(1, 1, 1));

        assertThrows(IllegalStateException.class,
                () -> fixture.assembler(structure("start", 2), 0, 0, 0).assemble(new RNG(19L)));
    }

    @Test
    public void emptyPoolChoiceTerminatesTheBranchWithoutPlacingAir() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece startPiece = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "terminal", "start", "door"));
        fixture.pool("start", pool("", "start-piece"));
        IrisJigsawPool terminal = new IrisJigsawPool();
        terminal.getPieces().add(new IrisJigsawPieceEntry().setEmpty(true).setWeight(1));
        fixture.pool("terminal", terminal);
        fixture.piece("start-piece", startPiece);
        fixture.object("start-object", new IrisObject(1, 1, 1));

        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure("start", 2), 0, 0, 0), new RNG(7L));

        assertEquals(1, placed.size());
        assertSame(startPiece, placed.getFirst().getPiece());
    }

    @Test
    public void emptyStartChoiceProducesAnIntentionalEmptyAssembly() {
        Fixture fixture = new Fixture();
        IrisJigsawPool start = new IrisJigsawPool();
        start.getPieces().add(new IrisJigsawPieceEntry().setEmpty(true).setWeight(1));
        fixture.pool("start", start);

        StructureAssemblyResult result = fixture.assembler(structure("start", 1), 0, 0, 0)
                .assemble(new RNG(9L));

        assertEquals(StructureAssemblyStatus.INTENTIONAL_EMPTY, result.status());
        assertTrue(result.pieces().isEmpty());
    }

    @Test
    public void assemblerRejectsLimitsOutsideTheDeclaredContract() {
        Fixture fixture = new Fixture();
        IrisStructure deep = structure("start", 31);
        IrisStructure wide = structure("start", 1).setMaxSizeChunks(33);
        IrisStructure overflowing = structure("start", 1).setMaxSizeChunks(Integer.MAX_VALUE);

        assertThrows(IllegalStateException.class,
                () -> fixture.assembler(deep, 0, 0, 0).assemble(new RNG(1L)));
        assertThrows(IllegalStateException.class,
                () -> fixture.assembler(wide, 0, 0, 0).assemble(new RNG(1L)));
        assertThrows(IllegalStateException.class,
                () -> fixture.assembler(overflowing, 0, 0, 0).assemble(new RNG(1L)));
    }

    @Test
    public void oneThemeIsSelectedForTheWholeAssembly() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece first = piece("first-object", false);
        first.getThemes().add("variant-1");
        IrisJigsawPiece second = piece("second-object", false);
        second.getThemes().add("variant-2");
        fixture.pool("start", pool("", "first-piece", "second-piece"));
        fixture.piece("first-piece", first);
        fixture.piece("second-piece", second);
        fixture.object("first-object", new IrisObject(1, 1, 1));
        fixture.object("second-object", new IrisObject(1, 1, 1));
        IrisStructure structure = structure("start", 1);
        structure.getThemeSets().add(new IrisJigsawThemeSet("variant-1", 1));
        structure.getThemeSets().add(new IrisJigsawThemeSet("variant-2", 1));

        StructureAssemblyResult result = fixture.assembler(structure, 0, 0, 0)
                .assemble(firstChoiceRng());

        assertEquals(StructureAssemblyStatus.COMPLETE, result.status());
        assertEquals("variant-1", result.selectedTheme());
        assertSame(first, result.pieces().getFirst().getPiece());
    }

    @Test
    public void unmetMinimumPlacementsTakePriorityWithinTheirPool() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece start = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "branch", "start", "door"));
        IrisJigsawPiece required = piece("required-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        required.setRules(new IrisJigsawPieceRules().setMinimumPlacements(1));
        IrisJigsawPiece common = piece("common-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        fixture.pool("start", pool("", "start-piece"));
        IrisJigsawPool branch = new IrisJigsawPool();
        branch.getPieces().add(new IrisJigsawPieceEntry("common-piece", 100));
        branch.getPieces().add(new IrisJigsawPieceEntry("required-piece", 1));
        fixture.pool("branch", branch);
        fixture.piece("start-piece", start);
        fixture.piece("required-piece", required);
        fixture.piece("common-piece", common);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("required-object", new IrisObject(1, 1, 1));
        fixture.object("common-object", new IrisObject(1, 1, 1));

        StructureAssemblyResult result = fixture.assembler(structure("start", 2), 0, 0, 0)
                .assemble(new RNG(61L));

        assertEquals(StructureAssemblyStatus.COMPLETE, result.status());
        assertSame(required, result.pieces().get(1).getPiece());
    }

    @Test
    public void terminalPiecesNeverEnqueueTheirRemainingConnectors() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece terminal = piece("terminal-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "child", "start", "door"));
        terminal.setRules(new IrisJigsawPieceRules().setTerminal(true));
        IrisJigsawPiece child = piece("child-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        fixture.pool("start", pool("", "terminal-piece"));
        fixture.pool("child", pool("", "child-piece"));
        fixture.piece("terminal-piece", terminal);
        fixture.piece("child-piece", child);
        fixture.object("terminal-object", new IrisObject(1, 1, 1));
        fixture.object("child-object", new IrisObject(1, 1, 1));

        StructureAssemblyResult result = fixture.assembler(structure("start", 2), 0, 0, 0)
                .assemble(new RNG(67L));

        assertEquals(StructureAssemblyStatus.COMPLETE, result.status());
        assertEquals(1, result.pieces().size());
    }

    @Test
    public void requiredCapsResolveThroughACompatibleTerminalFallback() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece start = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "branch", "start", "door"));
        IrisJigsawPiece incompatible = piece("incompatible-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "wrong", ""));
        IrisJigsawPiece cap = piece("cap-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "", "door", ""));
        cap.setRules(new IrisJigsawPieceRules().setTerminal(true));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("branch", pool("caps", "incompatible-piece"));
        fixture.pool("caps", pool("", "cap-piece"));
        fixture.piece("start-piece", start);
        fixture.piece("incompatible-piece", incompatible);
        fixture.piece("cap-piece", cap);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("incompatible-object", new IrisObject(1, 1, 1));
        fixture.object("cap-object", new IrisObject(1, 1, 1));
        IrisStructure structure = structure("start", 2).setRequireCaps(true);

        StructureAssemblyResult result = fixture.assembler(structure, 0, 0, 0)
                .assemble(new RNG(71L));

        assertEquals(StructureAssemblyStatus.COMPLETE, result.status());
        assertEquals(2, result.pieces().size());
        assertSame(cap, result.pieces().get(1).getPiece());
    }

    @Test
    public void compatibleOpenConnectorsCloseAgainstAnAlreadyPlacedNeighbor() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece start = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "east", "start-east", "door"),
                connector(IrisDirection.SOUTH_POSITIVE_Z, "south", "start-south", "door"));
        IrisJigsawPiece east = piece("east-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "terminal", "door", "unused"),
                connector(IrisDirection.SOUTH_POSITIVE_Z, "diagonal", "east-south", "door"));
        IrisJigsawPiece south = piece("south-object", false,
                connector(IrisDirection.NORTH_NEGATIVE_Z, "terminal", "door", "unused"),
                connector(IrisDirection.EAST_POSITIVE_X, "diagonal", "south-east", "door"));
        IrisJigsawPiece diagonal = piece("diagonal-object", false,
                connector(IrisDirection.NORTH_NEGATIVE_Z, "loop", "door", "unused"),
                connector(IrisDirection.WEST_NEGATIVE_X, "loop", "door", "unused"));
        IrisJigsawPiece cap = piece("cap-object", true,
                connector(IrisDirection.WEST_NEGATIVE_X, "terminal", "door", "unused"),
                connector(IrisDirection.WEST_NEGATIVE_X, "terminal", "unused", "unused"));
        cap.setRules(new IrisJigsawPieceRules().setTerminal(true));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("east", pool("caps", "east-piece"));
        fixture.pool("south", pool("caps", "south-piece"));
        fixture.pool("diagonal", pool("caps", "diagonal-piece"));
        fixture.pool("loop", pool("caps"));
        fixture.pool("caps", pool("", "cap-piece"));
        fixture.piece("start-piece", start);
        fixture.piece("east-piece", east);
        fixture.piece("south-piece", south);
        fixture.piece("diagonal-piece", diagonal);
        fixture.piece("cap-piece", cap);
        fixture.object("start-object", new IrisObject(1, 1, 1));
        fixture.object("east-object", new IrisObject(1, 1, 1));
        fixture.object("south-object", new IrisObject(1, 1, 1));
        fixture.object("diagonal-object", new IrisObject(1, 1, 1));
        fixture.object("cap-object", new IrisObject(1, 1, 1));
        IrisStructure structure = structure("start", 2).setRequireCaps(true);

        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure, 0, 0, 0), firstChoiceRng());

        assertEquals(4, placed.size());
        assertFalse(placed.stream().anyMatch(piece -> piece.getPiece() == cap));
    }

    @Test
    public void planarRequiredCapsRejectASecondOpenReservationForTheSameEmptyCell() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece start = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "east", "start-east", "door")
                        .setPosition(new IrisPosition(2, 0, 1)),
                connector(IrisDirection.SOUTH_POSITIVE_Z, "south", "start-south", "door")
                        .setPosition(new IrisPosition(1, 0, 2)));
        IrisJigsawPiece east = piece("east-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "terminal", "door", "unused")
                        .setPosition(new IrisPosition(0, 0, 1)),
                connector(IrisDirection.SOUTH_POSITIVE_Z, "grow", "east-south", "door")
                        .setPosition(new IrisPosition(1, 0, 2)));
        IrisJigsawPiece conflicting = piece("conflicting-object", false,
                connector(IrisDirection.NORTH_NEGATIVE_Z, "terminal", "door", "unused")
                        .setPosition(new IrisPosition(1, 0, 0)),
                connector(IrisDirection.EAST_POSITIVE_X, "grow", "south-east", "door")
                        .setPosition(new IrisPosition(2, 0, 1)));
        IrisJigsawPiece compatible = piece("compatible-object", false,
                connector(IrisDirection.NORTH_NEGATIVE_Z, "terminal", "door", "unused")
                        .setPosition(new IrisPosition(1, 0, 0)));
        IrisJigsawPiece cap = piece("cap-object", true,
                connector(IrisDirection.NORTH_NEGATIVE_Z, "terminal", "door", "unused")
                        .setPosition(new IrisPosition(1, 0, 0)));
        cap.setRules(new IrisJigsawPieceRules().setTerminal(true));
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("east", pool("caps", "east-piece"));
        fixture.pool("south", pool("caps", "conflicting-piece", "compatible-piece"));
        fixture.pool("grow", pool("caps"));
        fixture.pool("caps", pool("", "cap-piece"));
        fixture.piece("start-piece", start);
        fixture.piece("east-piece", east);
        fixture.piece("conflicting-piece", conflicting);
        fixture.piece("compatible-piece", compatible);
        fixture.piece("cap-piece", cap);
        fixture.object("start-object", new IrisObject(3, 1, 3));
        fixture.object("east-object", new IrisObject(3, 1, 3));
        fixture.object("conflicting-object", new IrisObject(3, 1, 3));
        fixture.object("compatible-object", new IrisObject(3, 1, 3));
        fixture.object("cap-object", new IrisObject(3, 1, 3));
        IrisStructure structure = structure("start", 2)
                .setMode(IrisJigsawMode.PLANAR_JIGSAW)
                .setCellSize(new IrisPosition(3, 1, 3))
                .setRequireCaps(true);

        List<PlacedStructurePiece> placed = completedPieces(
                fixture.assembler(structure, 0, 0, 0), firstChoiceRng());

        assertEquals(4, placed.size());
        assertFalse(placed.stream().anyMatch(piece -> piece.getPiece() == conflicting));
        assertTrue(placed.stream().anyMatch(piece -> piece.getPiece() == compatible));
        assertTrue(placed.stream().anyMatch(piece -> piece.getPiece() == cap));
    }

    @Test
    public void startChanceCanIntentionallyProduceNoStructure() {
        Fixture fixture = new Fixture();
        IrisJigsawPiece start = piece("start-object", false);
        IrisJigsawPool startPool = pool("", "start-piece");
        startPool.getPieces().getFirst().setChance(0D);
        fixture.pool("start", startPool);
        fixture.piece("start-piece", start);
        fixture.object("start-object", new IrisObject(1, 1, 1));

        StructureAssemblyResult result = fixture.assembler(structure("start", 1), 0, 0, 0)
                .assemble(new RNG(73L));

        assertEquals(StructureAssemblyStatus.INTENTIONAL_EMPTY, result.status());
        assertTrue(result.pieces().isEmpty());
    }

    @Test
    public void branchingGeometryReturnsATypedHardCapFailure() {
        Fixture fixture = new Fixture();
        IrisJigsawConnector[] connectors = {
                connector(IrisDirection.NORTH_NEGATIVE_Z, "branch", "door", "door"),
                connector(IrisDirection.EAST_POSITIVE_X, "branch", "door", "door"),
                connector(IrisDirection.SOUTH_POSITIVE_Z, "branch", "door", "door"),
                connector(IrisDirection.WEST_NEGATIVE_X, "branch", "door", "door"),
                connector(IrisDirection.UP_POSITIVE_Y, "branch", "door", "door"),
                connector(IrisDirection.DOWN_NEGATIVE_Y, "branch", "door", "door")
        };
        IrisJigsawPiece node = piece("node-object", false, connectors);
        fixture.pool("start", pool("", "node-piece"));
        fixture.pool("branch", pool("terminal", "node-piece"));
        fixture.piece("node-piece", node);
        fixture.object("node-object", new IrisObject(1, 1, 1));

        StructureAssemblyResult result = fixture.assembler(structure("start", 10), 0, 0, 0)
                .assemble(new RNG(83L));

        assertEquals(StructureAssemblyStatus.HARD_CAP, result.status());
        assertEquals(512, result.pieces().size());
        assertFalse(result.hasOutput());
    }

    private static IrisJigsawConnector connector(IrisDirection direction, String pool, String name, String targetName) {
        return new IrisJigsawConnector()
                .setPosition(new IrisPosition())
                .setDirection(direction)
                .setPool(pool == null || pool.isBlank() ? "terminal" : pool)
                .setName(name)
                .setTargetName(targetName)
                .setJoint(JigsawJoint.ROLLABLE);
    }

    private static IrisJigsawPiece piece(String object, boolean rotatable, IrisJigsawConnector... connectors) {
        IrisJigsawPiece piece = new IrisJigsawPiece().setObject(object).setRotatable(rotatable);
        piece.getConnectors().add(connectors);
        return piece;
    }

    private static IrisJigsawPool pool(String fallback, String... pieces) {
        IrisJigsawPool pool = new IrisJigsawPool().setFallback(fallback);
        for (String piece : pieces) {
            pool.getPieces().add(new IrisJigsawPieceEntry().setPiece(piece).setWeight(1));
        }
        return pool;
    }

    private static StructureAssemblyResult overlappingPair(
            boolean placedCollidable,
            boolean candidateCollidable
    ) {
        Fixture fixture = new Fixture();
        IrisJigsawPiece start = piece("start-object", false,
                connector(IrisDirection.EAST_POSITIVE_X, "target", "start", "door"))
                .setCollidable(placedCollidable);
        IrisJigsawPiece candidate = piece("candidate-object", false,
                connector(IrisDirection.WEST_NEGATIVE_X, "terminal", "door", "unused"))
                .setCollidable(candidateCollidable);
        fixture.pool("start", pool("", "start-piece"));
        fixture.pool("target", pool("", "candidate-piece"));
        fixture.piece("start-piece", start);
        fixture.piece("candidate-piece", candidate);
        fixture.object("start-object", new IrisObject(3, 3, 3));
        fixture.object("candidate-object", new IrisObject(3, 3, 3));
        return fixture.assembler(structure("start", 1), 0, 0, 0).assemble(new RNG(137L));
    }

    private static IrisStructure structure(String startPool, int maxDepth) {
        return new IrisStructure().setStartPool(startPool).setMaxDepth(maxDepth).setMaxSizeChunks(8);
    }

    private static RNG rotationIndex(int index) {
        return new RNG(67L + index) {
            @Override
            public int i(int upperBound) {
                return upperBound == 4 ? index : 0;
            }
        };
    }

    private static RNG firstChoiceRng() {
        return new RNG(79L) {
            @Override
            public long nextLong(long bound) {
                return 0L;
            }

            @Override
            public int i(int upperBound) {
                return 0;
            }
        };
    }

    private static PlacedStructurePiece bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new PlacedStructurePiece(null, null, 0, 0, 0, null, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<PlacedStructurePiece> completedPieces(StructureAssembler assembler, RNG rng) {
        StructureAssemblyResult result = assembler.assemble(rng);
        assertEquals(result.detail(), StructureAssemblyStatus.COMPLETE, result.status());
        return result.pieces();
    }

    private static PlacedStructurePiece requirePlacedPiece(List<PlacedStructurePiece> placed,
                                                           IrisJigsawPiece piece) {
        for (PlacedStructurePiece placedPiece : placed) {
            if (placedPiece.getPiece() == piece) {
                return placedPiece;
            }
        }
        throw new AssertionError("Expected jigsaw piece was not placed");
    }

    private static void assertBounds(PlacedStructurePiece piece, int minX, int minY, int minZ,
                                     int maxX, int maxY, int maxZ) {
        assertEquals(minX, piece.getMinX());
        assertEquals(minY, piece.getMinY());
        assertEquals(minZ, piece.getMinZ());
        assertEquals(maxX, piece.getMaxX());
        assertEquals(maxY, piece.getMaxY());
        assertEquals(maxZ, piece.getMaxZ());
    }

    private static final class Fixture {
        private final IrisData data = mock(IrisData.class);

        private Fixture() {
            when(data.load(IrisJigsawPool.class, "terminal", false)).thenReturn(new IrisJigsawPool());
        }

        private void pool(String key, IrisJigsawPool pool) {
            when(data.load(IrisJigsawPool.class, key, false)).thenReturn(pool);
        }

        private void piece(String key, IrisJigsawPiece piece) {
            when(data.load(IrisJigsawPiece.class, key, false)).thenReturn(piece);
        }

        private void object(String key, IrisObject object) {
            when(data.load(IrisObject.class, key, false)).thenReturn(object);
        }

        private StructureAssembler assembler(IrisStructure structure, int x, int y, int z) {
            return StructureAssembler.forData(data, structure, new IrisPosition(x, y, z));
        }
    }
}
