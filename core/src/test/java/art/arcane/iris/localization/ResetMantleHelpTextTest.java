package art.arcane.iris.localization;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResetMantleHelpTextTest {
    @Test
    public void resetMantleParamDescribesTheWholeWorldMantle() {
        String english = DirectorCommandMessages
                .COMMAND_DEVELOPER_PARAM_DELETE_MANTLE_DATA_SCAN_AREA_FIRST_FULL_REGENERATION_FROM_SCRATCH
                .english().toLowerCase(Locale.ROOT);

        assertTrue("help text must say the entire world mantle is deleted", english.contains("entire"));
        assertFalse("help text must not claim the deletion is scoped to the scan area", english.contains("scan area"));
    }

    @Test
    public void goldenhashDirectorDescribesTheWholeWorldMantle() {
        String english = DirectorCommandMessages
                .COMMAND_DEVELOPER_DIRECTOR_GENERATE_CHUNKS_INTO_BUFFERS_NO_WORLD_WRITES_HASH_BLOCKS_BIOMES_CAPTURES_GOLDEN
                .english().toLowerCase(Locale.ROOT);

        assertFalse("director text must not claim the mantle reset is scoped to the scan area",
                english.contains("scanned area"));
        assertTrue("director text must say the entire world mantle is deleted", english.contains("entire"));
    }

    @Test
    public void keyIdsStayStableAcrossTheWordingFix() {
        assertTrue(DirectorCommandMessages
                .COMMAND_DEVELOPER_PARAM_DELETE_MANTLE_DATA_SCAN_AREA_FIRST_FULL_REGENERATION_FROM_SCRATCH
                .id().endsWith("delete_mantle_data_scan_area_first_full_regeneration_from_scratch"));
        assertTrue(DirectorCommandMessages
                .COMMAND_DEVELOPER_DIRECTOR_GENERATE_CHUNKS_INTO_BUFFERS_NO_WORLD_WRITES_HASH_BLOCKS_BIOMES_CAPTURES_GOLDEN
                .id().endsWith("generate_chunks_into_buffers_no_world_writes_hash_blocks_biomes_captures_golden"));
    }
}
