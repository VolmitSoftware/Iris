package art.arcane.iris.core.studio.scene;

import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StudioSuggestionsTest {
    @Test
    public void thePacksOwnResourcesAreOfferedByPathAndOthersInFull() {
        List<ResourceId> ids = List.of(ResourceId.parse("overworld:birch"), ResourceId.parse("other:oak"), ResourceId.parse("overworld:conifer/pine"));

        assertEquals(List.of("birch", "conifer/pine", "other:oak"), StudioSuggestions.sortedKeys(ids, "overworld"));
    }

    @Test
    public void platformEntriesAreOfferedInFull() {
        List<ResourceId> ids = List.of(ResourceId.parse("minecraft:stone"), ResourceId.parse("minecraft:dirt"));

        assertEquals(List.of("minecraft:dirt", "minecraft:stone"), StudioSuggestions.sortedKeys(ids, null));
    }

    @Test
    public void aReferenceThatDoesNotExistIsRefused() {
        assertThrows(NoSuchElementException.class, () -> StudioSuggestions.ofReference("NOT_A_REFERENCE"));
    }
}
