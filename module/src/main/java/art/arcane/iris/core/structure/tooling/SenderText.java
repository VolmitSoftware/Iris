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

package art.arcane.iris.core.structure.tooling;

import com.volmit.tooling.Sender;
import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolText;

import java.util.Optional;

/**
 * Sends a sender a line in colour: a player sees the colours as they are, and a sender that is not a player gets the
 * line as an error, a success or plain information, by the colour the line starts with.
 */
public final class SenderText {
    private SenderText() {
    }

    public static void send(Sender sender, ToolText text) {
        Optional<ToolPlayer> player = sender.player();
        if (player.isPresent()) {
            player.get().message(text);
            return;
        }
        String plain = text.plainText();
        switch (leadingColor(text)) {
            case RED -> sender.error(plain);
            case GREEN -> sender.success(plain);
            default -> sender.info(plain);
        }
    }

    /** A whole line in {@code color}. */
    public static void send(Sender sender, TextColor color, String line) {
        send(sender, ToolText.colored(color, line));
    }

    private static TextColor leadingColor(ToolText text) {
        if (text.isEmpty() || text.spans().getFirst().color() == null) {
            return TextColor.WHITE;
        }
        return text.spans().getFirst().color();
    }
}
