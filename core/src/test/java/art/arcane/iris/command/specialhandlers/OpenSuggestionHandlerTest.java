package art.arcane.iris.command.specialhandlers;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class OpenSuggestionHandlerTest {
    @Test
    public void subterrainSuggestsFamiliesButAcceptsFeatureIds() {
        SubterrainFeatureHandler handler = new SubterrainFeatureHandler();
        assertEquals(List.of("tectonic_fault", "cenote", "lava_tube", "travertine_terraces"), handler.getPossibilities());
        assertEquals("my_feature:room", handler.parse(" my_feature:room ", false));
        assertEquals("", handler.parse(null, false));
    }
}
