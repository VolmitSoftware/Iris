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

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.service.JigsawStudioRegistry;
import art.arcane.iris.core.service.JigsawStudioRequest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The studio each player of the commands has open: which pack's studio, and which request opened it. */
final class StudioSessions {
    private static final Map<UUID, SessionBinding> PLAYER_PACKS = new ConcurrentHashMap<>();

    private StudioSessions() {
    }

    /** The studio of the pack {@code packKey}, opened by one request, with its session. */
    public record ActiveContext(String packKey, JigsawStudioRequest request, JigsawStudioSession session) {
    }

    record SessionBinding(String packKey, UUID requestId) {
    }

    static void bind(UUID playerId, SessionBinding binding) {
        PLAYER_PACKS.put(playerId, binding);
    }

    static void unbind(UUID playerId, SessionBinding binding) {
        PLAYER_PACKS.remove(playerId, binding);
    }

    /** The studio the player has open, or null when they have none or it has since been replaced. */
    static ActiveContext active(JigsawStudioRegistry registry, UUID playerId) {
        if (playerId == null) {
            return null;
        }
        SessionBinding binding = PLAYER_PACKS.get(playerId);
        if (binding == null) {
            return null;
        }
        ActiveContext context = context(registry, binding);
        if (context == null) {
            PLAYER_PACKS.remove(playerId, binding);
            return null;
        }
        return context;
    }

    private static ActiveContext context(JigsawStudioRegistry registry, SessionBinding binding) {
        JigsawStudioRequest request = registry.getRequest(binding.packKey());
        JigsawStudioSession session = registry.getSession(binding.packKey());
        if (request == null || session == null
                || !binding.requestId().equals(request.requestId())
                || !request.requestId().equals(session.sessionId())) {
            return null;
        }
        return new ActiveContext(binding.packKey(), request, session);
    }

    /** A studio of any player that still has unsaved edits, or null. */
    static ActiveContext findDirtyContext(JigsawStudioRegistry registry) {
        for (SessionBinding binding : PLAYER_PACKS.values()) {
            JigsawStudioRequest request = registry.getRequest(binding.packKey());
            JigsawStudioSession session = registry.getSession(binding.packKey());
            if (request != null && session != null
                    && binding.requestId().equals(request.requestId())
                    && session.isDirty()) {
                return new ActiveContext(binding.packKey(), request, session);
            }
        }
        return null;
    }
}
