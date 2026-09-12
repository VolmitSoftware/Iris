package art.arcane.iris.pack.validation;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PackCompatReportTest {
    private static CompatFinding finding(CompatAction action, String subjectKey, String detail) {
        return new CompatFinding(CompatRegistry.BLOCK, "minecraft:sulfur", action, "Biome", subjectKey, detail);
    }

    @Test
    public void recordDedupsOnRegistryKeyActionAndSubject() {
        PackCompatReport report = new PackCompatReport();
        report.record(finding(CompatAction.EXCLUDED, "cave/sulfur", "layers[0].palette[1]"));
        report.record(finding(CompatAction.EXCLUDED, "cave/sulfur", "layers[2].palette[0]"));
        report.record(finding(CompatAction.SUBSTITUTED, "cave/sulfur", "layers[1].palette[0] (backup minecraft:sand)"));
        report.record(finding(CompatAction.EXCLUDED, "desert/flats", "layers[0].palette[0]"));
        report.record(null);
        assertEquals(3, report.size());
        List<CompatFinding> findings = report.findings();
        assertEquals("layers[0].palette[1]", findings.get(0).detail());
        assertEquals(CompatAction.SUBSTITUTED, findings.get(1).action());
        assertEquals("desert/flats", findings.get(2).subjectKey());
        Map<CompatAction, Integer> counts = report.countsByAction();
        assertEquals(Integer.valueOf(2), counts.get(CompatAction.EXCLUDED));
        assertEquals(Integer.valueOf(1), counts.get(CompatAction.SUBSTITUTED));
        assertEquals(1, report.distinctKeys());
        assertEquals(3, report.byKey().get("BLOCK:minecraft:sulfur").size());
    }

    @Test
    public void markIncompleteKeepsFirstReason() {
        PackCompatReport report = new PackCompatReport();
        assertFalse(report.isIncomplete());
        report.markIncomplete("biome registry not ready");
        report.markIncomplete("structure registry not ready");
        assertTrue(report.isIncomplete());
        assertEquals("biome registry not ready", report.incompleteReason());
        report.clear();
        assertFalse(report.isIncomplete());
        assertNull(report.incompleteReason());
    }

    @Test
    public void clearForgetsFindingsForHotload() {
        PackCompatReport report = new PackCompatReport();
        report.record(finding(CompatAction.DROPPED, "cave/sulfur", "x"));
        assertFalse(report.isEmpty());
        report.clear();
        assertTrue(report.isEmpty());
        assertEquals(0, report.size());
        assertTrue(report.bootLines("overworld", "26.1.2", 3).isEmpty());
        assertEquals("", report.summaryLine("26.1.2"));
    }

    @Test
    public void findingLineAndDedupKey() {
        CompatFinding finding = finding(CompatAction.EXCLUDED, "cave/sulfur", "layers[0].palette[1]");
        assertEquals("minecraft:sulfur (block): excluded Biome cave/sulfur at layers[0].palette[1]", finding.line());
        assertEquals("excluded Biome cave/sulfur at layers[0].palette[1]", finding.subjectLine());
        assertEquals("BLOCK:minecraft:sulfur|EXCLUDED|Biome|cave/sulfur", finding.dedupKey());
        CompatFinding blank = new CompatFinding(CompatRegistry.ENTITY, "minecraft:camel", CompatAction.DROPPED, null, null, null);
        assertEquals("minecraft:camel (entity): dropped", blank.line());
        assertEquals("dropped", blank.subjectLine());
    }

    @Test
    public void findingRendersEachOptionalSubjectFieldIndependently() {
        CompatFinding typeOnly = new CompatFinding(
                CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "Biome", "", "");
        CompatFinding keyOnly = new CompatFinding(
                CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "", "cave/sulfur", "");
        CompatFinding detailOnly = new CompatFinding(
                CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED, "", "", "layers[0]");

        assertEquals("excluded Biome", typeOnly.subjectLine());
        assertEquals("excluded cave/sulfur", keyOnly.subjectLine());
        assertEquals("excluded at layers[0]", detailOnly.subjectLine());
        assertEquals("minecraft:sulfur (block): excluded Biome", typeOnly.line());
        assertEquals("minecraft:sulfur (block): excluded cave/sulfur", keyOnly.line());
        assertEquals("minecraft:sulfur (block): excluded at layers[0]", detailOnly.line());
    }
}
