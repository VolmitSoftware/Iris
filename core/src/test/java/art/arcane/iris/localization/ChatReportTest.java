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

    @Test
    public void chatShowsANextStepAsOneClickableLine() {
        ChatReport.Line step = ChatReport.of(ChatReport.Tone.ERROR, "t")
                .nextStep("See every issue", "/iris pack validate p")
                .build()
                .lines()
                .getLast();

        assertEquals("See every issue: /iris pack validate p", step.text().replaceAll("§.", ""));
        assertEquals("/iris pack validate p", step.command());
    }

    @Test
    public void consoleLayoutDrawsTheBodyAsATreeWithNextStepsOnTheirOwnLines() {
        List<String> console = plainConsoleLines(ChatReport.of(ChatReport.Tone.ERROR, "Pack 'uitest' failed validation")
                .items(List.of(
                        "Biome 'dev'" + REMOVED_FIELD_TAIL,
                        "Biome 'vanilla/grove'" + REMOVED_FIELD_TAIL,
                        "Missing dimensions/ folder."))
                .nextStep("See every issue", "/iris pack validate uitest")
                .build());

        assertEquals(List.of(
                ConsoleGlyphs.CROSS + " Pack 'uitest' failed validation",
                ConsoleGlyphs.BRANCH + " 2 biomes: caveProfile.allowWater was removed; use caveProfile.allowFluid. Cave aquifers use the dimension fluidPalette.",
                ConsoleGlyphs.BAR + "   dev, vanilla/grove",
                ConsoleGlyphs.BRANCH + " Missing dimensions/ folder.",
                ConsoleGlyphs.LAST_BRANCH + " See every issue:",
                "    /iris pack validate uitest"), console);
    }

    @Test
    public void consoleLayoutKeepsTheReportHighlights() {
        List<String> console = ChatReport.of(ChatReport.Tone.WARNING, "Pack 'uitest' has 1 warning")
                .item("Biome 'dev' uses create:limestone at wall.palette[0]. Install the mod.")
                .build()
                .consoleLines();

        assertTrue(console.get(0), console.get(0).contains(C.WHITE + "'uitest'" + C.GOLD));
        assertTrue(console.get(1), console.get(1).contains(C.WHITE + "'dev'" + C.GRAY));
        assertTrue(console.get(1), console.get(1).contains(C.AQUA + "create:limestone" + C.GRAY));
        assertTrue(console.get(1), console.get(1).endsWith(C.DARK_GRAY + "Install the mod."));
    }

    @Test
    public void consoleLayoutKeepsInfoTitlesOnABlankGutter() {
        List<String> console = plainConsoleLines(ChatReport.of(ChatReport.Tone.INFO, "Worlds").item("world").build());

        assertEquals(List.of("  Worlds", ConsoleGlyphs.LAST_BRANCH + " world"), console);
    }

    @Test
    public void consoleLayoutAlignsLabelledEntries() {
        List<String> console = plainConsoleLines(ChatReport.of(ChatReport.Tone.SUCCESS, "Exported pack 'uitest'")
                .entry("Folder", "exports/uitest")
                .entry("Datapack Folder Path", "world/datapacks/iris")
                .build());

        assertEquals(List.of(
                ConsoleGlyphs.CHECK + " Exported pack 'uitest'",
                ConsoleGlyphs.BRANCH + " Folder          exports/uitest",
                ConsoleGlyphs.LAST_BRANCH + " Datapack Folder Path",
                "                  world/datapacks/iris"), console);
    }

    @Test
    public void consoleHintEndingInItsCommandBecomesANextStep() {
        List<String> console = plainConsoleLines(ChatReport.of(ChatReport.Tone.SUCCESS, "Created Studio project 'uitest'")
                .hint("Restart the server, then run /iris studio open uitest", "/iris studio open uitest")
                .build());

        assertEquals(List.of(
                ConsoleGlyphs.CHECK + " Created Studio project 'uitest'",
                ConsoleGlyphs.LAST_BRANCH + " Restart the server, then run:",
                "    /iris studio open uitest"), console);
    }

    @Test
    public void consoleNextStepShowsTheCommandAClickWouldRun() {
        List<String> console = plainConsoleLines(ChatReport.of(ChatReport.Tone.INFO, "Regen started")
                .hint("Stop it with /iris std regen-stop", "/iris std regen-stop", "/iris std regen-stop world=w")
                .build());

        assertEquals(List.of("  Regen started", ConsoleGlyphs.LAST_BRANCH + " Stop it with:",
                "    /iris std regen-stop world=w"), console);
    }

    @Test
    public void consoleHintWithoutACommandStaysOneBranch() {
        List<String> console = plainConsoleLines(ChatReport.of(ChatReport.Tone.WARNING, "Regen is running")
                .hint("The console has the full refusal notice", null)
                .build());

        assertEquals(List.of(ConsoleGlyphs.WARNING + " Regen is running", ConsoleGlyphs.LAST_BRANCH + " The console has the full refusal notice"), console);
    }

    @Test
    public void logLinesWithoutAGutterGetTheirTonesMark() {
        assertEquals(ConsoleGlyphs.WARNING + " Default pack is missing",
                ConsoleLayout.onGutter(ChatReport.Tone.WARNING, "Default pack is missing").replaceAll("§.", ""));
        assertEquals("  Loading packs",
                ConsoleLayout.onGutter(ChatReport.Tone.INFO, "Loading packs").replaceAll("§.", ""));
    }

    @Test
    public void logLinesAlreadyOnTheGutterStayAsTheyAre() {
        String marked = ChatReport.Tone.ERROR.marked("Pack failed");
        String row = ConsoleLayout.row("Fix the errors above.");
        String branch = C.RED + ConsoleGlyphs.BRANCH + " " + C.GRAY + "Missing dimensions/ folder.";

        assertEquals(marked, ConsoleLayout.onGutter(ChatReport.Tone.ERROR, marked));
        assertEquals(row, ConsoleLayout.onGutter(ChatReport.Tone.ERROR, row));
        assertEquals(branch, ConsoleLayout.onGutter(ChatReport.Tone.ERROR, branch));
    }

    private static List<String> plainLines(ChatReport report) {
        return report.lines().stream().map(line -> line.text().replaceAll("§.", "")).toList();
    }

    private static List<String> plainConsoleLines(ChatReport report) {
        return report.consoleLines().stream().map(line -> line.replaceAll("§.", "")).toList();
    }
}
