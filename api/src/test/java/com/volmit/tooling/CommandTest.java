package com.volmit.tooling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {
    @Test
    void namesAreLowerCaseWordsOfLettersDigitsAndDashes() {
        assertEquals("verify-all", Command.named("verify-all").name());
        assertThrows(IllegalArgumentException.class, () -> Command.named("Verify"));
        assertThrows(IllegalArgumentException.class, () -> Command.named("two words"));
        assertThrows(IllegalArgumentException.class, () -> Command.named(""));
        assertThrows(IllegalArgumentException.class, () -> Command.named("a").alias("B"));
    }

    @Test
    void aCommandAnswersToItsNameAndAliasesButNotToDuplicatesOfThem() {
        var command = Command.named("structure").alias("struct", "str");
        assertTrue(command.answersTo("structure") && command.answersTo("str"));
        assertFalse(command.answersTo("list"));
        assertEquals(List.of("structure", "struct", "str"), command.words());
        assertThrows(IllegalArgumentException.class, () -> command.alias("structure"));
        assertThrows(IllegalArgumentException.class, () -> command.alias("str"));
    }

    @Test
    void methodsReturnChangedCopies() {
        var plain = Command.named("list");
        var described = plain.describe("Lists structures").openToEveryone().runs(context -> { });
        assertEquals("", plain.description());
        assertFalse(plain.isOpenToEveryone());
        assertTrue(plain.action().isEmpty());
        assertEquals("Lists structures", described.description());
        assertTrue(described.isOpenToEveryone());
        assertTrue(described.action().isPresent());
    }

    @Test
    void childrenMayNotShareANameOrAliasWithTheirSiblings() {
        var parent = Command.named("structure").child(Command.named("list").alias("ls"));
        var clash = assertThrows(IllegalArgumentException.class, () -> parent.child(Command.named("ls")));
        assertTrue(clash.getMessage().contains("ls"), clash.getMessage());
        assertThrows(IllegalArgumentException.class, () -> parent.child(Command.named("other").alias("list")));
        assertEquals(2, parent.child(Command.named("info")).children().size());
        assertEquals(1, parent.children().size());
    }

    @Test
    void anOptionalArgumentMayNotComeBeforeARequiredOne() {
        var optional = Command.named("verify").argument(Argument.integer("radius", 1, 10).orElse(5L));
        var late = assertThrows(IllegalArgumentException.class, () -> optional.argument(Argument.word("structure")));
        assertTrue(late.getMessage().contains("follows the optional argument radius"), late.getMessage());
        assertEquals(2, Command.named("verify").argument(Argument.word("structure"))
                .argument(Argument.integer("radius", 1, 10).orElse(5L)).arguments().size());
    }

    @Test
    void nothingFollowsTheArgumentThatTakesTheRestOfTheLine() {
        var text = Command.named("say").argument(Argument.text("message"));
        assertThrows(IllegalArgumentException.class, () -> text.argument(Argument.word("after").orElse("x")));
    }

    @Test
    void argumentNamesAreUniqueWithinACommand() {
        var one = Command.named("copy").argument(Argument.word("name"));
        assertThrows(IllegalArgumentException.class, () -> one.argument(Argument.word("name")));
    }
}
