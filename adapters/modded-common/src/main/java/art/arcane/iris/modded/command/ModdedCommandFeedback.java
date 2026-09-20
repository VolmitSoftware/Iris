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

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText.Format;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ModdedCommandFeedback {
    static final int IRIS = 0x1BB19E;
    static final int HEADER_A = 0x34EB6B;
    static final int HEADER_B = 0x32BFAD;
    static final int BACK = 0x6FE98F;
    static final int DARK_GREEN = 0x46826A;
    static final int DESCRIPTION_ICON = 0x3FE05A;
    static final int DESCRIPTION = 0x6AD97D;
    static final int USAGE_ICON = 0xBBE03F;
    static final int USAGE = 0xA8E0A2;
    static final int EXAMPLE_ICON = 0xC2F7D2;
    static final int PARAMETER = 0x5EF288;
    static final int PARAMETER_ALT = 0x32BFAD;
    static final int OPTIONAL = 0x4F4F4F;
    static final int CATEGORY = 0x9DE5B6;
    static final int REQUIRED = 0xDB4321;
    static final int REQUIRED_TEXT = 0xFAA796;
    static final int HOVER_TYPE = 0x8AD9AF;
    static final int VALUE = 0xC2F7D2;
    static final int PAGE_LINE_LENGTH = 75;
    private static final long MESSAGE_SOUND_COOLDOWN_MS = 650L;
    private static final long TAB_SOUND_COOLDOWN_MS = 175L;
    private static final Map<UUID, Long> MESSAGE_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TAB_SOUNDS = new ConcurrentHashMap<>();

    private ModdedCommandFeedback() {
    }

    static void ok(NativeCommandSource source, String message) {
        source.sendSuccess(() -> NativeCommandText.literal(message).withStyle(Format.GREEN), false);
        playSuccess(source);
    }

    static void ok(NativeCommandSource source, NativeCommandText component) {
        source.sendSuccess(() -> component, false);
        playSuccess(source);
    }

    static void fail(NativeCommandSource source, String message) {
        source.sendFailure(NativeCommandText.literal(message).withStyle(Format.RED));
        playFailure(source);
    }

    static void send(NativeCommandSource source, NativeCommandText component) {
        source.sendSuccess(() -> component, false);
    }

    static void clear(NativeCommandSource source) {
        if (source.playerId() != null) {
            send(source, NativeCommandText.literal("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n"));
        }
    }

    static NativeCommandText header(String title) {
        NativeCommandText header = NativeCommandText.empty();
        header.append(text(" ".repeat(18), HEADER_A, false, true));
        header.append(text(" " + title + " ", IRIS, true, false));
        header.append(text(" ".repeat(18), HEADER_B, false, true));
        return header;
    }

    static NativeCommandText banner(String title) {
        int pad = Math.max(1, 44 - (title.length() + 2) - 4);
        NativeCommandText banner = NativeCommandText.empty();
        banner.append(gradientText("[" + " ".repeat(pad) + "(((", HEADER_A, HEADER_B, true));
        banner.append(NativeCommandText.literal(" "));
        banner.append(gradientText(title, PARAMETER, PARAMETER_ALT, false));
        banner.append(NativeCommandText.literal(" "));
        banner.append(gradientText(")))" + " ".repeat(pad) + "]", HEADER_B, HEADER_A, true));
        return banner;
    }

    static NativeCommandText gradientText(String value, int from, int to, boolean strikethrough) {
        NativeCommandText out = NativeCommandText.empty();
        int steps = Math.max(1, value.length() - 1);
        for (int index = 0; index < value.length(); index++) {
            double t = index / (double) steps;
            int red = (int) Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
            int green = (int) Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
            int blue = (int) Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
            out.append(text(String.valueOf(value.charAt(index)), (red << 16) | (green << 8) | blue, false, strikethrough));
        }
        return out;
    }

    static NativeCommandText footer() {
        return text(" ".repeat(PAGE_LINE_LENGTH), HEADER_B, false, true);
    }

    static NativeCommandText text(String value, int color) {
        return text(value, color, false, false);
    }

    static NativeCommandText text(String value, int color, boolean bold, boolean strikethrough) {
        return NativeCommandText.literal(value).withStyle((NativeCommandText.TextStyle style) -> {
            NativeCommandText.TextStyle next = style.withColor(color);
            if (bold) {
                next = next.withBold(true);
            }
            if (strikethrough) {
                next = next.withStrikethrough(true);
            }
            return next;
        });
    }

    static NativeCommandText button(String label, String command, String hover, boolean runCommand) {
        NativeCommandText hoverText = text(hover, DESCRIPTION);
        return text(label, PARAMETER_ALT, true, false).withStyle((NativeCommandText.TextStyle style) -> style
                .hover(hoverText))
                .withStyle(style -> runCommand ? style.runCommand(command) : style.suggestCommand(command));
    }

    static NativeCommandText progressBar(double percent, int width) {
        double clamped = Math.max(0D, Math.min(100D, percent));
        int filled = (int) Math.round((clamped / 100D) * width);
        NativeCommandText bar = NativeCommandText.empty();
        bar.append(text("[", DARK_GREEN));
        for (int i = 0; i < width; i++) {
            bar.append(text(i < filled ? "|" : "·", i < filled ? PARAMETER : OPTIONAL));
        }
        bar.append(text("]", DARK_GREEN));
        return bar;
    }

    static void tab(NativeCommandSource source) {
        UUID player = source.playerId();
        if (player == null || !claim(TAB_SOUNDS, player, TAB_SOUND_COOLDOWN_MS)) {
            return;
        }

        source.playSound("minecraft:entity.item_frame.rotate_item", 0.25F, 1.7F);
    }

    private static void playSuccess(NativeCommandSource source) {
        UUID player = source.playerId();
        if (player == null || !claim(MESSAGE_SOUNDS, player, MESSAGE_SOUND_COOLDOWN_MS)) {
            return;
        }

        source.playSound("minecraft:block.amethyst_cluster.break", 0.77F, 1.65F);
        source.playSound("minecraft:block.respawn_anchor.charge", 0.125F, 2.99F);
    }

    private static void playFailure(NativeCommandSource source) {
        UUID player = source.playerId();
        if (player == null || !claim(MESSAGE_SOUNDS, player, MESSAGE_SOUND_COOLDOWN_MS)) {
            return;
        }

        source.playSound("minecraft:block.amethyst_cluster.break", 0.77F, 0.25F);
        source.playSound("minecraft:block.beacon.deactivate", 0.2F, 0.45F);
    }

    private static boolean claim(Map<UUID, Long> sounds, UUID uuid, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long previous = sounds.get(uuid);
        if (previous != null && now - previous.longValue() < cooldownMs) {
            return false;
        }

        sounds.put(uuid, now);
        return true;
    }
}
