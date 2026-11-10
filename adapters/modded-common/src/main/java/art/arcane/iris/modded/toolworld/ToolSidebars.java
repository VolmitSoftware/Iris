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

import com.volmit.tooling.world.ToolText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sidebar a tool shows one player: a scoreboard objective in the sidebar slot that only that player's client is told
 * about, so the server's own scoreboard and other players are left alone.
 */
final class ToolSidebars {
    private static final String OBJECTIVE_NAME = "iris_tool";
    private static final String LINE_OWNER_PREFIX = "iris_tool_line_";

    private final Map<UUID, Integer> shownLines = new ConcurrentHashMap<>();

    /** Shows {@code title} over {@code lines}, replacing the sidebar the player had. Runs on the server thread. */
    void show(ServerPlayer player, ToolText title, List<ToolText> lines) {
        Objective objective = objective(ToolTexts.component(title));
        boolean shownBefore = shownLines.containsKey(player.getUUID());
        player.connection.send(new ClientboundSetObjectivePacket(objective,
                shownBefore ? ClientboundSetObjectivePacket.METHOD_CHANGE : ClientboundSetObjectivePacket.METHOD_ADD));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        for (int index = 0; index < lines.size(); index++) {
            player.connection.send(new ClientboundSetScorePacket(LINE_OWNER_PREFIX + index, OBJECTIVE_NAME,
                    lines.size() - index, Optional.of(ToolTexts.component(lines.get(index))), Optional.of(BlankFormat.INSTANCE)));
        }
        for (int stale = lines.size(); stale < shownLines.getOrDefault(player.getUUID(), 0); stale++) {
            player.connection.send(new ClientboundResetScorePacket(LINE_OWNER_PREFIX + stale, OBJECTIVE_NAME));
        }
        shownLines.put(player.getUUID(), lines.size());
    }

    /** Takes the sidebar away; does nothing when none is shown. Runs on the server thread. */
    void hide(ServerPlayer player) {
        if (shownLines.remove(player.getUUID()) == null) {
            return;
        }
        player.connection.send(new ClientboundSetObjectivePacket(objective(Component.empty()),
                ClientboundSetObjectivePacket.METHOD_REMOVE));
    }

    void forget(UUID playerId) {
        shownLines.remove(playerId);
    }

    private static Objective objective(Component title) {
        return new Objective(new Scoreboard(), OBJECTIVE_NAME, ObjectiveCriteria.DUMMY, title,
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
    }
}
