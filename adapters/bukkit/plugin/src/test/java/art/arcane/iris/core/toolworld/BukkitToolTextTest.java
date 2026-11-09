package art.arcane.iris.core.toolworld;

import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolText;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BukkitToolTextTest {
    private static final String SECTION = "§";

    @Test
    public void legacyTextHasTheColourCodeOfEachSpanAsTheOldMenusWroteThem() {
        ToolText text = ToolText.builder().aqua("Structure").gray(": village").build();

        assertEquals(SECTION + "bStructure" + SECTION + "7: village", BukkitToolText.legacy(text));
    }

    @Test
    public void anUncolouredSpanAfterAnotherResets() {
        ToolText text = ToolText.builder().yellow("Walk").append(" plain").build();

        assertEquals(SECTION + "eWalk" + SECTION + "r plain", BukkitToolText.legacy(text));
        assertEquals("plain", BukkitToolText.legacy(ToolText.plain("plain")));
        assertEquals("", BukkitToolText.legacy(ToolText.EMPTY));
    }

    @Test
    public void stylesFollowTheColour() {
        ToolText text = ToolText.builder()
                .append(new ToolText.Span("x", TextColor.GRAY, true, true, true)).build();

        assertEquals(SECTION + "7" + SECTION + "l" + SECTION + "o" + SECTION + "mx", BukkitToolText.legacy(text));
    }

    @Test
    public void aComponentCarriesTheColoursAndTheTextOfEverySpan() {
        ToolText text = ToolText.builder().red("a").append("b").build();

        Component component = BukkitToolText.component(text);

        assertEquals("ab", PlainTextComponentSerializer.plainText().serialize(component));
        assertEquals(NamedTextColor.RED, component.children().getFirst().color());
        assertEquals(null, component.children().getLast().color());
        assertEquals(TextDecoration.State.NOT_SET, component.children().getFirst().decoration(TextDecoration.ITALIC));
    }

    @Test
    public void aTitleTooLongForTheWindowIsCutWithoutLeavingHalfACode() {
        ToolText coloured = ToolText.colored(TextColor.GREEN, "x".repeat(40));

        String cut = BukkitToolMenus.shortened(coloured);

        assertEquals(32, cut.length());
        assertEquals(SECTION + "a", cut.substring(0, 2));
    }
}
