package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NativeServerLogMessagesTest {
    @Test
    public void recognizesNativeCategoriesWithoutChoosingSuppressionPolicy() {
        assertEquals(NativeServerLogMessages.Kind.IGNORED_HEIGHTMAP,
                NativeServerLogMessages.classify("net.minecraft.chunk", "Ignoring heightmap data for chunk [2, -3]"));
        assertEquals(NativeServerLogMessages.Kind.RAID_PERSISTENCE,
                NativeServerLogMessages.classify("net.minecraft.storage", "Could not save data net.minecraft.world.entity.raid.PersistentRaid"));
        assertEquals(NativeServerLogMessages.Kind.DUPLICATE_ENTITY,
                NativeServerLogMessages.classify("net.minecraft.world", "UUID of added entity already exists: example"));
    }

    @Test
    public void leavesOtherLoggersAndMessagesUnclassified() {
        assertEquals(NativeServerLogMessages.Kind.OTHER,
                NativeServerLogMessages.classify("example.plugin", "UUID of added entity already exists"));
        assertEquals(NativeServerLogMessages.Kind.OTHER,
                NativeServerLogMessages.classify("net.minecraft.world", "Could not load chunk"));
        assertEquals(NativeServerLogMessages.Kind.OTHER, NativeServerLogMessages.classify(null, "message"));
        assertEquals(NativeServerLogMessages.Kind.OTHER, NativeServerLogMessages.classify("net.minecraft.world", null));
    }
}
