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

package art.arcane.iris.core.toolworld;

import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.EnumMap;
import java.util.Map;

/** {@link ToolText} as the text Bukkit shows: an Adventure component, or the legacy string the platform's older calls take. */
final class BukkitToolText {
    private static final char SECTION = '§';
    private static final Map<TextColor, NamedTextColor> COLORS = new EnumMap<>(Map.ofEntries(
            Map.entry(TextColor.BLACK, NamedTextColor.BLACK),
            Map.entry(TextColor.DARK_BLUE, NamedTextColor.DARK_BLUE),
            Map.entry(TextColor.DARK_GREEN, NamedTextColor.DARK_GREEN),
            Map.entry(TextColor.DARK_AQUA, NamedTextColor.DARK_AQUA),
            Map.entry(TextColor.DARK_RED, NamedTextColor.DARK_RED),
            Map.entry(TextColor.DARK_PURPLE, NamedTextColor.DARK_PURPLE),
            Map.entry(TextColor.GOLD, NamedTextColor.GOLD),
            Map.entry(TextColor.GRAY, NamedTextColor.GRAY),
            Map.entry(TextColor.DARK_GRAY, NamedTextColor.DARK_GRAY),
            Map.entry(TextColor.BLUE, NamedTextColor.BLUE),
            Map.entry(TextColor.GREEN, NamedTextColor.GREEN),
            Map.entry(TextColor.AQUA, NamedTextColor.AQUA),
            Map.entry(TextColor.RED, NamedTextColor.RED),
            Map.entry(TextColor.LIGHT_PURPLE, NamedTextColor.LIGHT_PURPLE),
            Map.entry(TextColor.YELLOW, NamedTextColor.YELLOW),
            Map.entry(TextColor.WHITE, NamedTextColor.WHITE)));

    private BukkitToolText() {
    }

    static Component component(ToolText text) {
        TextComponent.Builder whole = Component.text();
        for (ToolText.Span span : text.spans()) {
            Component piece = Component.text(span.text());
            if (span.color() != null) {
                piece = piece.color(COLORS.get(span.color()));
            }
            piece = piece
                    .decoration(TextDecoration.BOLD, span.bold() ? TextDecoration.State.TRUE : TextDecoration.State.NOT_SET)
                    .decoration(TextDecoration.ITALIC, span.italic() ? TextDecoration.State.TRUE : TextDecoration.State.NOT_SET)
                    .decoration(TextDecoration.STRIKETHROUGH,
                            span.strikethrough() ? TextDecoration.State.TRUE : TextDecoration.State.NOT_SET);
            whole.append(piece);
        }
        return whole.build();
    }

    /** The legacy string of {@code text}: formatting signs for the colour and style of each span, a reset between uncoloured ones. */
    static String legacy(ToolText text) {
        StringBuilder legacy = new StringBuilder();
        for (ToolText.Span span : text.spans()) {
            if (span.color() != null) {
                legacy.append(SECTION).append(span.color().code());
            } else if (!legacy.isEmpty()) {
                legacy.append(SECTION).append('r');
            }
            if (span.bold()) {
                legacy.append(SECTION).append('l');
            }
            if (span.italic()) {
                legacy.append(SECTION).append('o');
            }
            if (span.strikethrough()) {
                legacy.append(SECTION).append('m');
            }
            legacy.append(span.text());
        }
        return legacy.toString();
    }
}
