package art.arcane.iris.client;

import art.arcane.iris.modded.localization.ClientUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * CLIENT DIST ONLY. See {@link IrisClientHud} for why the dist marker is a javadoc contract plus a bytecode
 * test rather than an @Environment annotation.
 */
public final class IrisPregenHud {
    private static final int PANEL_COLOR = 0xC0101010;
    private static final int TITLE_COLOR = 0xFF66BB6A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFD7D7D7;
    private static final int BAR_BACK_COLOR = 0xFF2B2B2B;
    private static final int BAR_RUNNING_COLOR = 0xFF66BB6A;
    private static final int PAUSED_COLOR = 0xFFFFD54F;
    private static final int STALE_COLOR = 0xFF8A8A8A;
    private static final int GRID_BACK_COLOR = 0xFF161616;
    private static final int CELL_PENDING_COLOR = 0xFF3A3A3A;
    private static final int CELL_GENERATING_COLOR = 0xFFFFD54F;
    private static final int CELL_DONE_COLOR = 0xFF66BB6A;
    private static final int ORIGIN_X = 6;
    private static final int ORIGIN_Y = 6;
    private static final int PADDING = 4;
    private static final int ROW_GAP = 2;
    private static final int BAR_HEIGHT = 6;
    private static final int MIN_WIDTH = 130;
    private static final int MINIMAP_MAX_PX = 64;
    private static final int MINIMAP_MAX_CELL = 6;
    private static final int MINIMAP_GAP = 4;

    private IrisPregenHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!IrisClient.hudVisible()) {
            return;
        }
        IrisClientPregenState pregen = IrisClient.pregen();
        IrisMessage.PregenProgress progress = pregen.active();
        if (progress == null || pregen.activeExpired()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        Font font = minecraft.font;
        boolean stale = pregen.activeStale();
        boolean paused = progress.state() == IrisMessage.PregenProgress.STATE_PAUSED;
        double percent = progress.chunksTotal() > 0L
                ? clampPercent((double) progress.chunksDone() / (double) progress.chunksTotal() * 100.0D)
                : 0.0D;
        String title = IrisLanguage.plain(RuntimeUiMessages.PREGEN_HEADER);
        String stats = IrisLanguage.plain(
                ClientUiMessages.PREGEN_STATS,
                MessageArgument.trusted("done", String.format("%,d", progress.chunksDone())),
                MessageArgument.trusted("total", String.format("%,d", progress.chunksTotal())),
                MessageArgument.trusted("percent", String.format("%.1f", percent))
        );
        String tail = tail(progress, pregen, stale, paused);
        int accent = stale ? STALE_COLOR : paused ? PAUSED_COLOR : BAR_RUNNING_COLOR;

        int lineHeight = font.lineHeight;
        int contentWidth = Math.max(MIN_WIDTH, Math.max(font.width(title), Math.max(font.width(stats), font.width(tail))));
        int contentHeight = lineHeight * 3 + ROW_GAP * 3 + BAR_HEIGHT;

        IrisClientRegionMap regionMap = IrisClient.regions();
        IrisClientRegionMap.Bounds bounds = regionMap.hasData() ? regionMap.bounds() : null;
        boolean showMap = bounds != null;
        int cellPx = 0;
        int gridWidth = 0;
        int gridHeight = 0;
        if (showMap) {
            int span = Math.max(bounds.regionsWide(), bounds.regionsTall());
            cellPx = Math.max(1, Math.min(MINIMAP_MAX_CELL, MINIMAP_MAX_PX / span));
            gridWidth = Math.min(MINIMAP_MAX_PX, bounds.regionsWide() * cellPx);
            gridHeight = Math.min(MINIMAP_MAX_PX, bounds.regionsTall() * cellPx);
        }

        int panelWidth = showMap ? Math.max(contentWidth, gridWidth) : contentWidth;
        int panelHeight = showMap ? contentHeight + MINIMAP_GAP + gridHeight : contentHeight;

        graphics.fill(ORIGIN_X - PADDING, ORIGIN_Y - PADDING, ORIGIN_X + panelWidth + PADDING, ORIGIN_Y + panelHeight + PADDING, PANEL_COLOR);

        int cursorY = ORIGIN_Y;
        graphics.text(font, title, ORIGIN_X, cursorY, stale ? STALE_COLOR : TITLE_COLOR);
        cursorY += lineHeight + ROW_GAP;
        graphics.text(font, stats, ORIGIN_X, cursorY, stale ? STALE_COLOR : TEXT_COLOR);
        cursorY += lineHeight + ROW_GAP;

        int fillWidth = (int) Math.round(contentWidth * (percent / 100.0D));
        graphics.fill(ORIGIN_X, cursorY, ORIGIN_X + contentWidth, cursorY + BAR_HEIGHT, BAR_BACK_COLOR);
        if (fillWidth > 0) {
            graphics.fill(ORIGIN_X, cursorY, ORIGIN_X + fillWidth, cursorY + BAR_HEIGHT, accent);
        }
        cursorY += BAR_HEIGHT + ROW_GAP;

        graphics.text(font, tail, ORIGIN_X, cursorY, stale ? STALE_COLOR : paused ? PAUSED_COLOR : MUTED_COLOR);

        if (showMap) {
            renderMinimap(graphics, regionMap, bounds, cellPx, gridWidth, gridHeight, ORIGIN_Y + contentHeight + MINIMAP_GAP);
        }
    }

    private static void renderMinimap(GuiGraphicsExtractor graphics, IrisClientRegionMap regionMap, IrisClientRegionMap.Bounds bounds, int cellPx, int gridWidth, int gridHeight, int gridTop) {
        int mapLeft = ORIGIN_X;
        int mapRight = mapLeft + gridWidth;
        int mapBottom = gridTop + gridHeight;
        graphics.fill(mapLeft, gridTop, mapRight, mapBottom, GRID_BACK_COLOR);
        int minRegionX = bounds.minRegionX();
        int minRegionZ = bounds.minRegionZ();
        regionMap.forEachCell((regionX, regionZ, state) -> {
            int px = mapLeft + (regionX - minRegionX) * cellPx;
            int py = gridTop + (regionZ - minRegionZ) * cellPx;
            if (px + cellPx > mapRight || py + cellPx > mapBottom) {
                return;
            }
            graphics.fill(px, py, px + cellPx, py + cellPx, cellColor(state));
        });
    }

    private static int cellColor(int state) {
        return switch (state) {
            case IrisMessage.PregenRegionDelta.STATE_DONE -> CELL_DONE_COLOR;
            case IrisMessage.PregenRegionDelta.STATE_GENERATING -> CELL_GENERATING_COLOR;
            default -> CELL_PENDING_COLOR;
        };
    }

    private static String tail(IrisMessage.PregenProgress progress, IrisClientPregenState pregen, boolean stale, boolean paused) {
        if (stale) {
            return IrisLanguage.plain(
                    ClientUiMessages.PREGEN_STALE,
                    MessageArgument.trusted("seconds", pregen.activeAgeMillis() / 1000L));
        }
        return paused ? IrisLanguage.plain(ClientUiMessages.PREGEN_PAUSED) : rateAndEta(progress);
    }

    private static String rateAndEta(IrisMessage.PregenProgress progress) {
        String rate = String.format("%,.0f", progress.chunksPerSecond());
        if (progress.etaMillis() > 0L) {
            return IrisLanguage.plain(
                    ClientUiMessages.PREGEN_RATE_ETA,
                    MessageArgument.trusted("rate", rate),
                    MessageArgument.trusted("eta", formatDuration(progress.etaMillis()))
            );
        }
        return IrisLanguage.plain(ClientUiMessages.PREGEN_RATE, MessageArgument.trusted("rate", rate));
    }

    private static String formatDuration(long etaMillis) {
        long totalSeconds = etaMillis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return IrisLanguage.plain(ClientUiMessages.DURATION_HOURS_MINUTES, MessageArgument.trusted("hours", hours), MessageArgument.trusted("minutes", minutes));
        }
        if (minutes > 0L) {
            return IrisLanguage.plain(ClientUiMessages.DURATION_MINUTES_SECONDS, MessageArgument.trusted("minutes", minutes), MessageArgument.trusted("seconds", seconds));
        }
        return IrisLanguage.plain(ClientUiMessages.DURATION_SECONDS, MessageArgument.trusted("seconds", seconds));
    }

    private static double clampPercent(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 100.0D) {
            return 100.0D;
        }
        return value;
    }
}
