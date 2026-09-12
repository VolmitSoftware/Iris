package art.arcane.iris.structure;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A bulk import walks every registered datapack structure, so one broken server-side assumption
 * fails hundreds of times in a row from the same throw site. Printing a full stack trace per
 * structure buried a Leaf 26.2 boot under ~4,700 identical lines. The first occurrence of a failure
 * signature prints in full; repeats are counted by the per-structure "[fail] key: message" line
 * instead of re-printing.
 */
public class VillageImporterFailureLogTest {
    private static Throwable raise(boolean illegalArgument, String message) {
        return illegalArgument ? new IllegalArgumentException(message) : new IllegalStateException(message);
    }

    @Test
    public void repeatedIdenticalFailuresPrintOnce() {
        VillageImporter.resetFailureLogState();

        assertTrue(VillageImporter.shouldPrintFullTrace(
                raise(true, "illegal data type conversion to int")));
        for (int i = 0; i < 200; i++) {
            assertFalse(VillageImporter.shouldPrintFullTrace(
                    raise(true, "illegal data type conversion to int")));
        }
    }

    @Test
    public void distinctFailureMessagesAndCategoriesEachPrintOnce() {
        VillageImporter.resetFailureLogState();

        assertTrue(VillageImporter.shouldPrintFullTrace(raise(true, "a")));
        assertTrue(VillageImporter.shouldPrintFullTrace(raise(false, "a")));
        assertTrue(VillageImporter.shouldPrintFullTrace(raise(true, "b")));
        assertFalse(VillageImporter.shouldPrintFullTrace(raise(true, "b")));
    }

    @Test
    public void nullFailureIsNotPrinted() {
        VillageImporter.resetFailureLogState();
        assertFalse(VillageImporter.shouldPrintFullTrace(null));
    }
}
