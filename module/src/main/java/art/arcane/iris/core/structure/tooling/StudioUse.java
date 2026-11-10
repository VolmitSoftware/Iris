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

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioRegistry;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.structure.tooling.StudioSessions.ActiveContext;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.Sender;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolPlayer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * One run of a command that acts on a player's studio: the pack's studio, the player who ran the command and what they say
 * back. A command that needs a player and has none has told the sender when {@link #begin} answers empty.
 */
final class StudioUse {
    static final String PLAYERS_ONLY = "This command can only be used by players.";
    static final String NO_ACTIVE_STUDIO = "Open a Jigsaw Studio project first.";
    private static final long PLAYER_THREAD_TIMEOUT_SECONDS = 10L;

    private final JigsawStudios studios;
    private final CommandContext context;
    private final ToolPlayer player;
    private final ToolPack pack;

    private StudioUse(JigsawStudios studios, CommandContext context, ToolPlayer player, ToolPack pack) {
        this.studios = studios;
        this.context = context;
        this.player = player;
        this.pack = pack;
    }

    /** This use on another pack, such as the one an earlier command prepared a change for. */
    StudioUse onPack(ToolPack other) {
        return new StudioUse(studios, context, player, other);
    }

    /** The key the studio files {@code pack} under: the name of the pack's folder. */
    static String packKey(ToolPack pack) {
        return pack.outputFolder().getFileName().toString();
    }

    ToolPack pack() {
        return pack;
    }

    /** The use for a command that needs the pack's studio and the player who ran it. */
    static Optional<StudioUse> begin(JigsawStudios studios, CommandContext context) {
        Optional<ToolPlayer> player = context.sender().player();
        if (player.isEmpty()) {
            context.sender().error(PLAYERS_ONLY);
            return Optional.empty();
        }
        return Optional.of(new StudioUse(studios, context, player.get(), context.pack()));
    }

    CommandContext context() {
        return context;
    }

    ToolPlayer player() {
        return player;
    }

    Sender sender() {
        return context.sender();
    }

    JigsawStudioService studio() {
        return studios.of(pack);
    }

    JigsawStudioRegistry registry() {
        return studio().registry();
    }

    void say(TextColor color, String message) {
        SenderText.send(context.sender(), color, message);
    }

    void error(String message) {
        say(TextColor.RED, message);
    }

    /** The studio this player has open, after telling them to open one when they have none. */
    ActiveContext requireActive() {
        ActiveContext active = StudioSessions.active(registry(), player.id());
        if (active == null) {
            error(NO_ACTIVE_STUDIO);
        }
        return active;
    }

    /** Runs {@code task} on the thread that owns the player, as the studio's calls about a player expect. */
    void onPlayer(Runnable task) {
        player.run(task);
    }

    /** What {@code reader} reads from the player, on the thread that owns them; waits for it. */
    <T> T readFromPlayer(Supplier<T> reader) {
        CompletableFuture<T> result = new CompletableFuture<>();
        player.run(() -> {
            try {
                result.complete(reader.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        try {
            return result.get(PLAYER_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading from the player", interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Could not read from the player: " + failure.getMessage(), failure);
        }
    }

    static JigsawStudioBay selectedBay(ActiveContext context) {
        String selected = context.session().selectedBayId().orElse(null);
        return selected == null ? null : context.session().layout().get(selected);
    }

    static JigsawStudioVariant selectedVariant(ActiveContext context) {
        JigsawStudioBay selected = selectedBay(context);
        return selected == null
                ? null
                : context.session().activeVariant(selected.stableId()).orElse(null);
    }
}
