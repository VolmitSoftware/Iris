/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded.toolworld;

import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ToolTextsTest {
    private static final ToolText STYLED = ToolText.builder()
            .gray("Size: ")
            .append(new ToolText.Span("16", TextColor.WHITE, true, false, true))
            .append("plain")
            .build();

    @Test
    public void chatKeepsTheColourAndStylesOfEachSpanAndNothingElse() {
        List<Component> parts = ToolTexts.component(STYLED).getSiblings();

        assertEquals(3, parts.size());
        assertEquals(Style.EMPTY.withColor(ChatFormatting.GRAY), parts.get(0).getStyle());
        assertEquals(Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(true).withStrikethrough(true), parts.get(1).getStyle());
        assertEquals(Style.EMPTY, parts.get(2).getStyle());
        assertEquals("Size: 16plain", ToolTexts.component(STYLED).getString());
    }

    @Test
    public void itemTextIsNeverItalicUnlessTheSpanAsksForIt() {
        ToolText text = ToolText.builder()
                .gold("Wand")
                .append(new ToolText.Span("slanted", null, false, true, false))
                .build();

        List<Component> parts = ToolTexts.itemComponent(text).getSiblings();

        assertEquals(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false), parts.get(0).getStyle());
        assertEquals(Style.EMPTY.withItalic(true), parts.get(1).getStyle());
    }

    @Test
    public void emptyTextIsAnEmptyComponent() {
        assertEquals("", ToolTexts.component(ToolText.EMPTY).getString());
    }
}
