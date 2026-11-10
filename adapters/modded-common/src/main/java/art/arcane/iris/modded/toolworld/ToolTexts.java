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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.EnumMap;
import java.util.Map;

/** {@link ToolText} as the components Minecraft shows. */
final class ToolTexts {
    private static final Map<TextColor, ChatFormatting> COLORS = new EnumMap<>(Map.ofEntries(
            Map.entry(TextColor.BLACK, ChatFormatting.BLACK),
            Map.entry(TextColor.DARK_BLUE, ChatFormatting.DARK_BLUE),
            Map.entry(TextColor.DARK_GREEN, ChatFormatting.DARK_GREEN),
            Map.entry(TextColor.DARK_AQUA, ChatFormatting.DARK_AQUA),
            Map.entry(TextColor.DARK_RED, ChatFormatting.DARK_RED),
            Map.entry(TextColor.DARK_PURPLE, ChatFormatting.DARK_PURPLE),
            Map.entry(TextColor.GOLD, ChatFormatting.GOLD),
            Map.entry(TextColor.GRAY, ChatFormatting.GRAY),
            Map.entry(TextColor.DARK_GRAY, ChatFormatting.DARK_GRAY),
            Map.entry(TextColor.BLUE, ChatFormatting.BLUE),
            Map.entry(TextColor.GREEN, ChatFormatting.GREEN),
            Map.entry(TextColor.AQUA, ChatFormatting.AQUA),
            Map.entry(TextColor.RED, ChatFormatting.RED),
            Map.entry(TextColor.LIGHT_PURPLE, ChatFormatting.LIGHT_PURPLE),
            Map.entry(TextColor.YELLOW, ChatFormatting.YELLOW),
            Map.entry(TextColor.WHITE, ChatFormatting.WHITE)));

    private ToolTexts() {
    }

    /** Chat, action bar, titles and sidebar lines: unstyled spans keep the platform's own look. */
    static Component component(ToolText text) {
        return build(text, false);
    }

    /** Item names and lore: vanilla shows them in italics unless told not to, so a span is italic only when it asks to be. */
    static Component itemComponent(ToolText text) {
        return build(text, true);
    }

    private static Component build(ToolText text, boolean explicitItalics) {
        MutableComponent whole = Component.empty();
        for (ToolText.Span span : text.spans()) {
            whole.append(Component.literal(span.text()).setStyle(styleOf(span, explicitItalics)));
        }
        return whole;
    }

    private static Style styleOf(ToolText.Span span, boolean explicitItalics) {
        Style style = Style.EMPTY;
        if (span.color() != null) {
            style = style.withColor(COLORS.get(span.color()));
        }
        if (span.bold()) {
            style = style.withBold(true);
        }
        if (span.italic() || explicitItalics) {
            style = style.withItalic(span.italic());
        }
        if (span.strikethrough()) {
            style = style.withStrikethrough(true);
        }
        return style;
    }
}
