package art.arcane.iris.command.handlers;

import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.volmlib.util.director.handlers.base.BooleanHandlerBase;
import art.arcane.volmlib.util.director.handlers.base.ByteHandlerBase;
import art.arcane.volmlib.util.director.handlers.base.DoubleHandlerBase;
import art.arcane.volmlib.util.director.handlers.base.FloatHandlerBase;
import art.arcane.volmlib.util.director.handlers.base.IntegerHandlerBase;
import art.arcane.volmlib.util.director.handlers.base.LongHandlerBase;
import art.arcane.volmlib.util.director.handlers.base.ShortHandlerBase;
import art.arcane.volmlib.util.director.handlers.StringHandlerBase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PrimitiveHandlerParsingTest {
    @Test
    public void integerSuffixesScaleTheParsedValue() throws DirectorParsingException {
        IntegerHandlerBase handler = new IntegerHandlerBase();

        assertEquals(Integer.valueOf(5), handler.parse("5", false));
        assertEquals(Integer.valueOf(64), handler.parse("4c", false));
        assertEquals(Integer.valueOf(1_000), handler.parse("1k", false));
        assertEquals(Integer.valueOf(200), handler.parse("2h", false));
        assertEquals(Integer.valueOf(512), handler.parse("1r", false));
    }

    @Test
    public void stackedIntegerSuffixesMultiplyTogether() throws DirectorParsingException {
        assertEquals(Integer.valueOf(2_000_000), new IntegerHandlerBase().parse("2kk", false));
    }

    @Test
    public void anUnparseableIntegerNamesTheOffendingInput() {
        DirectorParsingException failure =
                assertThrows(DirectorParsingException.class, () -> new IntegerHandlerBase().parse("abc", false));

        assertTrue(failure.getMessage(), failure.getMessage().contains("abc"));
    }

    @Test
    public void anUnscaledLongKeepsFullPrecision() throws DirectorParsingException {
        LongHandlerBase handler = new LongHandlerBase();

        assertEquals(Long.valueOf(9_007_199_254_740_993L), handler.parse("9007199254740993", false));
        assertEquals(Long.valueOf(1_000L), handler.parse("1k", false));
    }

    @Test
    public void shortAndFloatingPointHandlersShareTheSuffixTable() throws DirectorParsingException {
        assertEquals(Short.valueOf((short) 32), new ShortHandlerBase().parse("2c", false));
        assertEquals(1_500F, new FloatHandlerBase().parse("1.5k", false), 0F);
        assertEquals(1_500D, new DoubleHandlerBase().parse("1.5k", false), 0D);
    }

    @Test
    public void byteParsingHasNoSuffixTable() throws DirectorParsingException {
        ByteHandlerBase handler = new ByteHandlerBase();

        assertEquals(Byte.valueOf((byte) 12), handler.parse("12", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("1k", false));
    }

    @Test
    public void booleanSentinelsAreCaseSensitiveAndEverythingElseFallsBackToFalse() throws DirectorParsingException {
        BooleanHandlerBase handler = new BooleanHandlerBase();

        assertNull(handler.parse("null", false));
        assertNull(handler.parse("other", false));
        assertNull(handler.parse("flip", false));
        assertEquals(Boolean.TRUE, handler.parse("TRUE", false));
        assertEquals(Boolean.FALSE, handler.parse("yes", false));
        assertEquals(Boolean.FALSE, handler.parse("NULL", false));
    }

    @Test
    public void stringParsingIsVerbatim() throws DirectorParsingException {
        StringHandlerBase handler = new StringHandlerBase();

        assertEquals("  spaced  ", handler.parse("  spaced  ", false));
        assertEquals("", handler.parse("", false));
    }

    @Test
    public void eachHandlerClaimsOnlyItsOwnParameterType() {
        assertTrue(new IntegerHandlerBase().supports(int.class));
        assertTrue(new IntegerHandlerBase().supports(Integer.class));
        assertFalse(new IntegerHandlerBase().supports(Long.class));
        assertTrue(new LongHandlerBase().supports(long.class));
        assertTrue(new ShortHandlerBase().supports(short.class));
        assertTrue(new ByteHandlerBase().supports(byte.class));
        assertTrue(new FloatHandlerBase().supports(float.class));
        assertTrue(new DoubleHandlerBase().supports(double.class));
        assertTrue(new BooleanHandlerBase().supports(boolean.class));
        assertTrue(new StringHandlerBase().supports(String.class));
        assertFalse(new StringHandlerBase().supports(Object.class));
    }
}
