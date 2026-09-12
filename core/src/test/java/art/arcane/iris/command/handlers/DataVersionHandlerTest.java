package art.arcane.iris.command.handlers;

import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DataVersionHandlerTest {
    private final DataVersionHandler handler = new DataVersionHandler();

    @Test
    public void latestResolvesToTheNewestShippedDatapackVersion() throws DirectorParsingException {
        assertSame(DataVersion.getLatest(), handler.parse("latest", false));
        assertSame(DataVersion.getLatest(), handler.parse("LATEST", false));
    }

    @Test
    public void anExactVersionStringResolvesRegardlessOfCase() throws DirectorParsingException {
        assertSame(DataVersion.V26_2, handler.parse("26.2", false));
        assertSame(DataVersion.V26_1_2, handler.parse("26.1.2", false));
    }

    @Test
    public void theUnsupportedSentinelIsHiddenFromSuggestionsButStillParses() throws DirectorParsingException {
        KList<DataVersion> possibilities = handler.getPossibilities();

        assertFalse(possibilities.contains(DataVersion.UNSUPPORTED));
        assertTrue(possibilities.contains(DataVersion.getLatest()));
        assertSame(DataVersion.UNSUPPORTED, handler.parse(DataVersion.UNSUPPORTED.getVersion(), false));
    }

    @Test
    public void anUnknownVersionNamesTheOffendingInput() {
        DirectorParsingException failure =
                assertThrows(DirectorParsingException.class, () -> handler.parse("nope", false));

        assertTrue(failure.getMessage(), failure.getMessage().contains("nope"));
    }

    @Test
    public void theRenderedFormIsTheVersionString() {
        assertSame(DataVersion.V26_2.getVersion(), handler.toString(DataVersion.V26_2));
    }

    @Test
    public void onlyDataVersionParametersAreClaimed() {
        assertTrue(handler.supports(DataVersion.class));
        assertFalse(handler.supports(String.class));
        assertFalse(handler.supports(Integer.class));
    }
}
