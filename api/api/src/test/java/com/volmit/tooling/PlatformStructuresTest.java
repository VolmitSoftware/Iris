package com.volmit.tooling;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformStructuresTest {
    @Test
    void aTemplateKeepsItsOwnCopyOfItsBlocks() {
        var blocks = new ArrayList<>(List.of(TemplateBlock.of(
                new BlockPosition(0, 0, 0), BlockState.parse("minecraft:stone"), Optional.empty())));
        var template = new StructureTemplate(new BlockPosition(1, 1, 1), 1, 0, blocks);

        blocks.clear();

        assertEquals(1, template.blocks().size());
        assertThrows(UnsupportedOperationException.class, () -> template.blocks().clear());
    }

    @Test
    void aPoolListsEveryMemberWhetherTheToolCanReadItOrNot() {
        var unreadable = new TemplatePool.Unreadable(TemplatePool.Part.ENTRY, "no weight");
        var element = new TemplatePool.Element(3, "SinglePoolElement",
                Optional.of(ResourceId.parse("minecraft:village/house")), 0, 0,
                seeds -> new TemplatePool.Connectors(TemplatePool.Availability.READ, List.of(
                        new TemplatePool.JigsawConnector(new BlockPosition(1, 2, 3), BlockFace.NORTH, BlockFace.UP,
                                Optional.empty(), "minecraft:door", "minecraft:door", false, Optional.empty(), 0, 0))));
        var pool = new TemplatePool(List.of(new TemplatePool.Absent(), unreadable, element), Optional.empty(), Optional.empty());

        assertEquals(3, pool.members().size());
        assertEquals(1, element.connectors().read(() -> 7L).connectors().size());
    }

    @Test
    void anAccessFailureSaysWhetherAskingAgainMayHelp() {
        assertTrue(new StructureAccessException("registry not ready", true).retryable());
        assertEquals("no such member", new StructureAccessException("no such member", false).getMessage());
    }
}
