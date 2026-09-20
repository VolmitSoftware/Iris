package art.arcane.iris.client;

import art.arcane.volmlib.nativelib.client.ClientGraphics;
import art.arcane.volmlib.nativelib.minecraft26_2.client.NativeClientAccess;

import art.arcane.iris.modded.localization.ClientUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.ArrayList;
import java.util.List;

/**
 * CLIENT DIST ONLY. See {@link IrisClientHud} for why the dist marker is a javadoc contract plus a bytecode
 * test rather than an @Environment annotation.
 */
public final class IrisWhatOverlay {
    private static final int PANEL_COLOR = 0xC0101010;
    private static final int TITLE_COLOR = 0xFF66BB6A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFC7C7C7;
    private static final int PADDING = 4;
    private static final int ROW_GAP = 2;
    private static final int CURSOR_OFFSET = 12;

    private IrisWhatOverlay() {
    }

    public static void render(ClientGraphics graphics) {
        if (!IrisClient.whatVisible() || !IrisClient.cursorAvailable()) {
            return;
        }
        if (!NativeClientAccess.playerPresent()) {
            return;
        }
        int blockX = NativeClientAccess.targetBlockX();
        int blockZ = NativeClientAccess.targetBlockZ();
        IrisClient.cursor().requestFor(blockX, blockZ);

        IrisMessage.CursorInfo info = IrisClient.cursor().latest();
        List<OverlayLine> lines = new ArrayList<>();
        if (info == null) {
            lines.add(new OverlayLine(IrisLanguage.plain(ClientUiMessages.WHAT_QUERYING, MessageArgument.trusted("x", blockX), MessageArgument.trusted("z", blockZ)), false));
        } else {
            lines.add(new OverlayLine(IrisLanguage.plain(ClientUiMessages.WHAT_BIOME, MessageArgument.untrusted("biome", display(info.biomeKey()))), false));
            lines.add(new OverlayLine(IrisLanguage.plain(ClientUiMessages.WHAT_REGION, MessageArgument.untrusted("region", display(info.regionKey()))), false));
            if (info.caveBiomeKey() != null && !info.caveBiomeKey().isEmpty()) {
                lines.add(new OverlayLine(IrisLanguage.plain(ClientUiMessages.WHAT_CAVE, MessageArgument.untrusted("cave", display(info.caveBiomeKey()))), false));
            }
            lines.add(new OverlayLine(IrisLanguage.plain(
                    ClientUiMessages.WHAT_HEIGHT,
                    MessageArgument.trusted("height", info.height()),
                    MessageArgument.trusted("x", info.blockX()),
                    MessageArgument.trusted("z", info.blockZ())
            ), true));
        }
        draw(graphics, lines);
    }

    private static void draw(ClientGraphics graphics, List<OverlayLine> lines) {
        int lineHeight = graphics.lineHeight();
        String title = IrisLanguage.plain(ClientUiMessages.WHAT_TITLE);
        int contentWidth = graphics.textWidth(title);
        for (OverlayLine line : lines) {
            contentWidth = Math.max(contentWidth, graphics.textWidth(line.text()));
        }
        int originX = graphics.guiWidth() / 2 + CURSOR_OFFSET;
        int originY = graphics.guiHeight() / 2 + CURSOR_OFFSET;
        int rows = lines.size() + 1;
        int contentHeight = lineHeight * rows + ROW_GAP * rows;
        graphics.fill(originX - PADDING, originY - PADDING, originX + contentWidth + PADDING, originY + contentHeight + PADDING, PANEL_COLOR);

        int cursorY = originY;
        graphics.text(title, originX, cursorY, TITLE_COLOR);
        cursorY += lineHeight + ROW_GAP;
        for (OverlayLine line : lines) {
            graphics.text(line.text(), originX, cursorY, line.muted() ? MUTED_COLOR : TEXT_COLOR);
            cursorY += lineHeight + ROW_GAP;
        }
    }

    private static String display(String key) {
        return key == null || key.isEmpty() ? "-" : key;
    }

    private record OverlayLine(String text, boolean muted) {
    }
}
