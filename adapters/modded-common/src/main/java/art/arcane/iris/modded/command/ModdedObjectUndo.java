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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditWorld;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModdedObjectUndo {
    private static final int MAX_ENTRIES_PER_OWNER = 32;
    private static final ConcurrentHashMap<UUID, Deque<Entry>> UNDOS = new ConcurrentHashMap<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    public static final UUID CONSOLE = new UUID(0L, 0L);

    private ModdedObjectUndo() {
    }

    private record Entry(NativeEditWorld.EditSession blocks) {
    }

    public static void init() {
        if (INITIALIZED.compareAndSet(false, true)) {
            ModdedIrisLog.info("Iris object undo service ready (bounded to {} paste(s) per player)", MAX_ENTRIES_PER_OWNER);
        }
    }

    public static void record(UUID owner, NativeEditWorld.EditSession oldBlocks) {
        if (oldBlocks == null || oldBlocks.empty()) {
            return;
        }
        Deque<Entry> queue = UNDOS.computeIfAbsent(owner, (UUID key) -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(new Entry(oldBlocks));
            while (queue.size() > MAX_ENTRIES_PER_OWNER) {
                queue.pollFirst();
            }
        }
    }

    public static int size(UUID owner) {
        Deque<Entry> queue = UNDOS.get(owner);
        if (queue == null) {
            return 0;
        }
        synchronized (queue) {
            return queue.size();
        }
    }

    public static int undo(UUID owner, int amount) {
        Deque<Entry> queue = UNDOS.get(owner);
        if (queue == null) {
            return 0;
        }
        int reverted = 0;
        while (reverted < amount) {
            Entry entry;
            synchronized (queue) {
                entry = queue.pollLast();
            }
            if (entry == null) {
                break;
            }
            // Identity check, not just null: a studio closed and reopened under the same
            // dimension id must never have blocks replayed into the dead ServerLevel.
            if (!entry.blocks().world().current()) {
                ModdedIrisLog.warn("Iris object undo: skipped a stale entry for removed dimension {}",
                        entry.blocks().world().key());
                continue;
            }
            int writes = entry.blocks().restore(failure -> ModdedIrisLog.error(
                    "Iris object undo: failed to revert a block at {}", failure.position(), failure.error()));
            ModdedIrisLog.info("Iris object undo: reverted {} block(s) in {}", writes, entry.blocks().world().key());
            reverted++;
        }
        return reverted;
    }

    /**
     * Drops every entry recorded against the given level. Called on dimension removal so a
     * closed studio releases its block snapshots and the ServerLevel reference.
     */
    public static void forget(NativeWorld level) {
        if (level == null) {
            return;
        }
        UNDOS.entrySet().removeIf((Map.Entry<UUID, Deque<Entry>> ownerEntry) -> {
            Deque<Entry> queue = ownerEntry.getValue();
            synchronized (queue) {
                queue.removeIf((Entry entry) -> entry.blocks().world().represents(level));
                return queue.isEmpty();
            }
        });
    }

    public static void clearAll() {
        UNDOS.clear();
    }
}
