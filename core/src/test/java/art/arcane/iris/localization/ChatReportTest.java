package art.arcane.iris.localization;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatReportTest {
    private static final String REMOVED_FIELD_TAIL =
            " caveProfile.allowWater was removed; use caveProfile.allowFluid. Cave aquifers use the dimension fluidPalette.";

    @Test
    public void reasonsSharingAllButTheSubjectCollapseIntoOneEntry() {
        List<String> plain = plainLines(ChatReport.of(ChatReport.Tone.ERROR, "Can't open Studio")
                .items(List.of(
                        "Biome 'dev'" + REMOVED_FIELD_TAIL,
                        "Biome 'vanilla/grove'" + REMOVED_FIELD_TAIL,
                        "Biome 'vanilla/ice_spikes'" + REMOVED_FIELD_TAIL,
                        "Missing dimensions/ folder."))
                .build());

        assertEquals(List.of(
                "Can't open Studio",
                "  » 3 biomes: caveProfile.allowWater was removed; use caveProfile.allowFluid. Cave aquifers use the dimension fluidPalette.",
                "     dev, vanilla/grove, vanilla/ice_spikes",
                "  » Missing dimensions/ folder."), plain);
    }

    @Test
    public void groupedSubjectsAreCappedWithAMoreTail() {
        List<String> reasons = List.of("a", "b", "c", "d", "e", "f").stream()
                .map(subject -> "Biome '" + subject + "'" + REMOVED_FIELD_TAIL)
                .toList();

        List<String> plain = plainLines(ChatReport.of(ChatReport.Tone.ERROR, "t").items(reasons).build());

        assertEquals("     a, b, c, d +2 more", plain.get(2));
    }

    @Test
    public void aLoneSubjectReasonKeepsItsOriginalWording() {
        List<String> plain = plainLines(ChatReport.of(ChatReport.Tone.WARNING, "t")
                .items(List.of("Biome 'dev'" + REMOVED_FIELD_TAIL))
                .build());

        assertEquals("  » Biome 'dev'" + REMOVED_FIELD_TAIL, plain.get(1));
    }

    @Test
    public void bodyHighlightsNamesAndDimsTheExplanation() {
        String styled = ReportText.body("Biome 'dev' uses create:limestone at wall.palette[0]. Install the mod.");

        assertTrue(styled.contains(C.WHITE + "'dev'" + C.GRAY));
        assertTrue(styled.contains(C.AQUA + "create:limestone" + C.GRAY));
        assertTrue(styled.contains(C.AQUA + "wall.palette[0]" + C.GRAY));
        assertTrue(styled.endsWith(C.DARK_GRAY + "Install the mod."));
    }

    @Test
    public void apostrophesAreNotTreatedAsQuotedNames() {
        String styled = ReportText.title(ChatReport.Tone.ERROR, "Can't open Studio for 'uitest'");

        assertTrue(styled.contains("Can't open Studio for " + C.WHITE + "'uitest'"));
    }

    @Test
    public void titlesStayRegularWeight() {
        String styled = ReportText.title(ChatReport.Tone.ERROR, "Can't open Studio for 'uitest'");

        assertFalse(styled.contains(C.BOLD.toString()));
    }

    @Test
    public void boldCodesSurviveTheMarkupConversion() {
        String markup = C.mini(C.RED.toString() + C.BOLD + "Title" + C.GRAY + " rest");

        assertTrue(markup, markup.contains("<bold>Title<reset><gray> rest"));
    }

    @Test
    public void hintCanShowAShorterCommandThanItRuns() {
        ChatReport.Line hint = ChatReport.of(ChatReport.Tone.INFO, "t")
                .hint("Stop it with /iris std regen-stop", "/iris std regen-stop", "/iris std regen-stop world=w")
                .build()
                .lines()
                .getLast();

        assertEquals("Stop it with /iris std regen-stop", hint.text().replaceAll("§.", ""));
        assertEquals("/iris std regen-stop world=w", hint.command());
    }

    @Test
    public void chatBodySeparatesTheHintButTheCompactLayoutDoesNot() {
        ChatReport report = ChatReport.of(ChatReport.Tone.ERROR, "t")
                .item("Missing dimensions/ folder.")
                .hint("Run /iris pack validate p", "/iris pack validate p")
                .build();

        assertEquals(List.of(report.bodyLines(false).get(0), ChatReport.SPACER, report.bodyLines(false).get(1)),
                report.bodyLines(true));
        assertEquals(3, report.lines().size());
    }

    private static List<String> plainLines(ChatReport report) {
        return report.lines().stream().map(line -> line.text().replaceAll("§.", "")).toList();
    }
}
