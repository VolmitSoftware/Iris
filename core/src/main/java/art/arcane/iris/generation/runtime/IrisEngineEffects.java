/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.math.M;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class IrisEngineEffects extends EngineAssignedComponent implements EngineEffects {
    private static final long EFFECT_BUDGET_NANOS = 1_500_000L;
    private static final long EMPTY_PLAYER_REFRESH_NANOS = 1_000_000_000L;
    private static final EnginePlayer[] NO_PLAYERS = new EnginePlayer[0];

    private final ConcurrentHashMap<UUID, EnginePlayer> players;
    private final Semaphore limit;
    private final AtomicBoolean playerMapUpdateQueued;
    private final AtomicLong nextEmptyRefresh;
    private volatile EnginePlayer[] playerSnapshot;

    public IrisEngineEffects(Engine engine) {
        super(engine, "FX");
        players = new ConcurrentHashMap<>();
        limit = new Semaphore(1);
        playerMapUpdateQueued = new AtomicBoolean(false);
        nextEmptyRefresh = new AtomicLong(0L);
        playerSnapshot = NO_PLAYERS;
    }

    @Override
    public void updatePlayerMap() {
        if (!playerMapUpdateQueued.compareAndSet(false, true)) {
            return;
        }
        if (!J.runGlobal(() -> {
            try {
                syncPlayerMap();
            } finally {
                playerMapUpdateQueued.set(false);
            }
        })) {
            playerMapUpdateQueued.set(false);
        }
    }

    private void syncPlayerMap() {
        List<Player> pr = BukkitWorldBinding.players(getEngine().getWorld());

        if (pr == null) {
            return;
        }

        Set<UUID> activeIds = new HashSet<>(Math.max(16, pr.size() * 2));
        for (Player player : pr) {
            UUID playerId = player.getUniqueId();
            activeIds.add(playerId);
            EnginePlayer existing = players.get(playerId);
            if (existing == null || existing.getPlayer() != player) {
                players.put(playerId, new EnginePlayer(getEngine(), player));
            }
        }

        players.keySet().removeIf((UUID playerId) -> !activeIds.contains(playerId));
        playerSnapshot = players.values().toArray(EnginePlayer[]::new);
    }

    @Override
    public void tickRandomPlayer() {
        if (!limit.tryAcquire()) {
            return;
        }
        try {
            EnginePlayer[] snapshot = playerSnapshot;
            if (snapshot.length == 0) {
                long now = System.nanoTime();
                long next = nextEmptyRefresh.get();
                if (now >= next && nextEmptyRefresh.compareAndSet(next, now + EMPTY_PLAYER_REFRESH_NANOS)) {
                    updatePlayerMap();
                }
                return;
            }
            if (M.r(0.02)) {
                updatePlayerMap();
                return;
            }

            long started = System.nanoTime();
            int index = ThreadLocalRandom.current().nextInt(snapshot.length);
            int remaining = snapshot.length;
            while (remaining-- > 0 && System.nanoTime() - started < EFFECT_BUDGET_NANOS) {
                snapshot[index].tick();
                if (++index == snapshot.length) {
                    index = 0;
                }
            }
        } finally {
            limit.release();
        }
    }
}
