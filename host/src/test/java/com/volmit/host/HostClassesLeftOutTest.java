package com.volmit.host;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HostClassesLeftOutTest {
    @Test
    void nothingIsLeftOutWithoutEntries() {
        assertFalse(HostClassesLeftOut.none().leavesOut("a.B"));
        assertFalse(HostClassesLeftOut.parse(List.of("", "  # only a comment")).leavesOut("a.B"));
    }

    @Test
    void anEntryWithoutSuffixOrWildcardNamesAClassAndItsNestedClasses() {
        var leftOut = HostClassesLeftOut.parse(List.of("a/b/C"));

        assertTrue(leftOut.leavesOut("a.b.C"));
        assertTrue(leftOut.leavesOut("a.b.C$Inner"));
        assertFalse(leftOut.leavesOut("a.b.Cat"));
        assertFalse(leftOut.leavesOut("a.b.sub.C"));
    }

    @Test
    void doubleStarCrossesPackagesAndSingleStarDoesNot() {
        var leftOut = HostClassesLeftOut.parse(List.of("a/noise/**", "a/stream/Basic*.class"));

        assertTrue(leftOut.leavesOut("a.noise.Simplex"));
        assertTrue(leftOut.leavesOut("a.noise.deep.Cell"));
        assertTrue(leftOut.leavesOut("a.stream.BasicStream"));
        assertFalse(leftOut.leavesOut("a.stream.sub.BasicStream"));
        assertFalse(leftOut.leavesOut("a.stream.Other"));
    }

    @Test
    void commentsAndBlankLinesAreIgnored() {
        var leftOut = HostClassesLeftOut.parse(List.of("# header", "", "  a/b/C   # why", "a/D"));

        assertTrue(leftOut.leavesOut("a.b.C"));
        assertTrue(leftOut.leavesOut("a.D"));
    }
}
