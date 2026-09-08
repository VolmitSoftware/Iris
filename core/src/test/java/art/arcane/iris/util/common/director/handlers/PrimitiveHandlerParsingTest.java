package art.arcane.iris.util.common.director.handlers;

import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PrimitiveHandlerParsingTest {
    @Test
    public void integerSuffixesScaleTheParsedValue() throws DirectorParsingException {
        IntegerHandler handler = new IntegerHandler();

        assertEquals(Integer.valueOf(5), handler.parse("5", false));
        assertEquals(Integer.valueOf(64), handler.parse("4c", false));
        assertEquals(Integer.valueOf(1_000), handler.parse("1k", false));
        assertEquals(Integer.valueOf(200), handler.parse("2h", false));
        assertEquals(Integer.valueOf(512), handler.parse("1r", false));
    }

    @Test
    public void stackedIntegerSuffixesMultiplyTogether() throws DirectorParsingException {
        assertEquals(Integer.valueOf(2_000_000), new IntegerHandler().parse("2kk", false));
    }

    @Test
    public void anUnparseableIntegerNamesTheOffendingInput() {
        DirectorParsingException failure =
                assertThrows(DirectorParsingException.class, () -> new IntegerHandler().parse("abc", false));

        assertTrue(failure.getMessage(), failure.getMessage().contains("abc"));
    }

    @Test
    public void anUnscaledLongKeepsFullPrecision() throws DirectorParsingException {
        LongHandler handler = new LongHandler();

        assertEquals(Long.valueOf(9_007_199_254_740_993L), handler.parse("9007199254740993", false));
        assertEquals(Long.valueOf(1_000L), handler.parse("1k", false));
    }

    @Test
    public void shortAndFloatingPointHandlersShareTheSuffixTable() throws DirectorParsingException {
        assertEquals(Short.valueOf((short) 32), new ShortHandler().parse("2c", false));
        assertEquals(1_500F, new FloatHandler().parse("1.5k", false), 0F);
        assertEquals(1_500D, new DoubleHandler().parse("1.5k", false), 0D);
    }

    @Test
    public void byteParsingHasNoSuffixTable() throws DirectorParsingException {
        ByteHandler handler = new ByteHandler();

        assertEquals(Byte.valueOf((byte) 12), handler.parse("12", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("1k", false));
    }

    @Test
    public void booleanSentinelsAreCaseSensitiveAndEverythingElseFallsBackToFalse() throws DirectorParsingException {
        BooleanHandler handler = new BooleanHandler();

        assertNull(handler.parse("null", false));
        assertNull(handler.parse("other", false));
        assertNull(handler.parse("flip", false));
        assertEquals(Boolean.TRUE, handler.parse("TRUE", false));
        assertEquals(Boolean.FALSE, handler.parse("yes", false));
        assertEquals(Boolean.FALSE, handler.parse("NULL", false));
    }

    @Test
    public void stringParsingIsVerbatim() throws DirectorParsingException {
        StringHandler handler = new StringHandler();

        assertEquals("  spaced  ", handler.parse("  spaced  ", false));
        assertEquals("", handler.parse("", false));
    }

    @Test
    public void eachHandlerClaimsOnlyItsOwnParameterType() {
        assertTrue(new IntegerHandler().supports(int.class));
        assertTrue(new IntegerHandler().supports(Integer.class));
        assertFalse(new IntegerHandler().supports(Long.class));
        assertTrue(new LongHandler().supports(long.class));
        assertTrue(new ShortHandler().supports(short.class));
        assertTrue(new ByteHandler().supports(byte.class));
        assertTrue(new FloatHandler().supports(float.class));
        assertTrue(new DoubleHandler().supports(double.class));
        assertTrue(new BooleanHandler().supports(boolean.class));
        assertTrue(new StringHandler().supports(String.class));
        assertFalse(new StringHandler().supports(Object.class));
    }
}
