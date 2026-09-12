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

import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;

import java.util.Objects;

public final class IrisSession {
    private final String id;
    private final IrisServerTransport transport;
    private volatile State state;
    private volatile long capabilities;
    private volatile int remoteProtocolVersion;
    private long inboundWindowStartMillis;
    private int inboundFramesInWindow;
    private long visionTileWindowStartMillis;
    private int visionTileRequestsInWindow;
    private long cursorInfoWindowStartMillis;
    private int cursorInfoRequestsInWindow;

    public IrisSession(String id, IrisServerTransport transport) {
        this.id = Objects.requireNonNull(id, "session id");
        this.transport = Objects.requireNonNull(transport, "session transport");
        this.state = State.AWAITING_HELLO;
        this.capabilities = 0L;
        this.remoteProtocolVersion = 0;
        this.inboundWindowStartMillis = 0L;
        this.inboundFramesInWindow = 0;
        this.visionTileWindowStartMillis = 0L;
        this.visionTileRequestsInWindow = 0;
        this.cursorInfoWindowStartMillis = 0L;
        this.cursorInfoRequestsInWindow = 0;
    }

    public String id() {
        return id;
    }

    public State state() {
        return state;
    }

    public long capabilities() {
        return capabilities;
    }

    public int remoteProtocolVersion() {
        return remoteProtocolVersion;
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public boolean hasCapability(long capabilityMask) {
        return (capabilities & capabilityMask) != 0L;
    }

    public void markReady(int protocolVersion, long negotiatedCapabilities) {
        this.remoteProtocolVersion = protocolVersion;
        this.capabilities = negotiatedCapabilities;
        this.state = State.READY;
    }

    public synchronized boolean allowInbound(long nowMillis) {
        if (nowMillis - inboundWindowStartMillis >= 1000L) {
            inboundWindowStartMillis = nowMillis;
            inboundFramesInWindow = 0;
        }
        if (inboundFramesInWindow >= IrisProtocol.MAX_INBOUND_FRAMES_PER_SECOND) {
            return false;
        }
        inboundFramesInWindow++;
        return true;
    }

    public synchronized boolean allowVisionTile(long nowMillis) {
        if (nowMillis - visionTileWindowStartMillis >= 1000L) {
            visionTileWindowStartMillis = nowMillis;
            visionTileRequestsInWindow = 0;
        }
        if (visionTileRequestsInWindow >= IrisProtocol.MAX_VISION_TILE_REQUESTS_PER_SECOND) {
            return false;
        }
        visionTileRequestsInWindow++;
        return true;
    }

    /**
     * Cursor lookups get their own second-window budget instead of sharing
     * {@link #allowInbound(long)}: a client is free to spend its whole frame budget on cursors otherwise, and
     * each lookup costs three engine column resolves.
     */
    public synchronized boolean allowCursorInfo(long nowMillis) {
        if (nowMillis - cursorInfoWindowStartMillis >= 1000L) {
            cursorInfoWindowStartMillis = nowMillis;
            cursorInfoRequestsInWindow = 0;
        }
        if (cursorInfoRequestsInWindow >= IrisProtocol.MAX_CURSOR_INFO_REQUESTS_PER_SECOND) {
            return false;
        }
        cursorInfoRequestsInWindow++;
        return true;
    }

    public void send(IrisMessage message) {
        sendRaw(IrisMessageCodec.encode(message));
    }

    public void sendRaw(byte[] frame) {
        transport.sendToClient(id, frame);
    }

    public enum State {
        AWAITING_HELLO,
        READY
    }
}
