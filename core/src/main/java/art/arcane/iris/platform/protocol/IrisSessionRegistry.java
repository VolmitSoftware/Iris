/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.protocol;

import java.util.concurrent.ConcurrentHashMap;

public final class IrisSessionRegistry {
    private final ConcurrentHashMap<String, IrisSession> sessions;

    public IrisSessionRegistry() {
        this.sessions = new ConcurrentHashMap<>();
    }

    public void register(IrisSession session) {
        sessions.put(session.id(), session);
    }

    public IrisSession unregister(String sessionId) {
        return sessions.remove(sessionId);
    }

    public IrisSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public int size() {
        return sessions.size();
    }

    public Iterable<IrisSession> all() {
        return sessions.values();
    }
}
