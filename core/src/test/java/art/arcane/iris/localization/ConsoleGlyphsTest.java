package art.arcane.iris.localization;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class ConsoleGlyphsTest {
    private static final String REPORT_LINE = "  \u00bb Biome \u2039caves\u203a";

    @Test
    public void utf8ConsoleKeepsGlyphs() {
        assertEquals(REPORT_LINE, ConsoleGlyphs.forCharset(StandardCharsets.UTF_8).fit(REPORT_LINE));
    }

    @Test
    public void asciiConsoleGetsAsciiGlyphs() {
        assertEquals("  > Biome [caves]", ConsoleGlyphs.forCharset(StandardCharsets.US_ASCII).fit(REPORT_LINE));
    }

    @Test
    public void asciiConsoleGetsAsciiStatusGlyphs() {
        String gutter = ConsoleGlyphs.BAR + " " + ConsoleGlyphs.CHECK + " done\n" + ConsoleGlyphs.CROSS + " failed\n"
                + ConsoleGlyphs.WARNING + " Loading" + ConsoleGlyphs.ELLIPSIS;

        assertEquals("| + done\nx failed\n! Loading...", ConsoleGlyphs.forCharset(StandardCharsets.US_ASCII).fit(gutter));
    }

    @Test
    public void latin1ConsoleKeepsWhatItCanEncode() {
        assertEquals("  \u00bb Biome [caves]", ConsoleGlyphs.forCharset(StandardCharsets.ISO_8859_1).fit(REPORT_LINE));
    }
}
