package art.arcane.iris.command;

import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.volmlib.util.director.compat.DirectorAnnotationCompatibility;
import art.arcane.volmlib.util.director.runtime.DirectorNodeDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandPackCompatTest {
    @Rule
    public final PlatformLeakGuard leakGuard = PlatformLeakGuard.clean();

    private static CompatFinding block(CompatAction action, String subjectKey) {
        return new CompatFinding(
                CompatRegistry.BLOCK, "minecraft:sulfur", action, "Biome", subjectKey, "layers[0].palette[1]");
    }

    private static PackValidationResult result(List<CompatFinding> findings) {
        return new PackValidationResult("overworld", List.of(), List.of(), 1L, findings, "26.1.2");
    }

    @Test
    public void unknownPackIsReportedWithoutAnyCompatLines() {
        List<String> lines = CommandPack.compatOutput("nope", null);

        assertEquals(1, lines.size());
        assertTrue(lines.getFirst(), lines.getFirst().contains("No validation result for 'nope'"));
    }

    @Test
    public void packWithoutFindingsSaysSoAndNamesTheVersion() {
        List<String> lines = CommandPack.compatOutput("overworld", result(List.of()));

        assertEquals(1, lines.size());
        assertTrue(lines.getFirst(), lines.getFirst().contains("Pack 'overworld' uses no content"));
        assertTrue(lines.getFirst(), lines.getFirst().contains("26.1.2"));
    }

    @Test
    public void findingsArePrintedUncappedWithTheFallbackRemedy() {
        List<String> lines = CommandPack.compatOutput("overworld", result(List.of(
                block(CompatAction.EXCLUDED, "cave/a"),
                block(CompatAction.EXCLUDED, "cave/b"),
                block(CompatAction.EXCLUDED, "cave/c"),
                block(CompatAction.EXCLUDED, "cave/d"))));

        assertEquals(3, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("Pack 'overworld': content unavailable on Minecraft 26.1.2"));
        assertTrue(lines.get(1), lines.get(1).contains("minecraft:sulfur (block)"));
        assertFalse("the command prints every subject", lines.get(1).contains("more"));
        assertTrue(lines.get(1), lines.get(1).contains("cave/d"));
        assertTrue(lines.get(2), lines.get(2).contains("blockFallbacks"));
    }

    @Test
    public void compatIsInvocableWithoutAPackArgument() throws Exception {
        DirectorNodeDescriptor node = DirectorAnnotationCompatibility
                .fromMethod(CommandPack.class.getDeclaredMethod("compat", String.class))
                .orElseThrow();
        DirectorParameterDescriptor pack = node.getParameters().getFirst();

        assertEquals("pack", pack.getName());
        assertFalse("compat must not require a pack", pack.isRequired());
        assertFalse("Director needs a non-blank default to treat the parameter as optional",
                pack.getDefaultValue().isBlank());
    }
}
