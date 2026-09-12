package art.arcane.iris.client;

import art.arcane.iris.modded.localization.ClientUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.volmlib.util.localization.MessageArgument;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLIENT DIST ONLY. See {@link IrisClientHud} for why the dist marker is a javadoc contract plus a bytecode
 * test rather than an @Environment annotation.
 */
public final class IrisVisionScreen extends Screen {
    private static final int TILE_PIXELS = 128;
    private static final int MIN_ZOOM = 0;
    private static final int MAX_ZOOM = 8;
    private static final int DEFAULT_ZOOM = 2;
    /** Floor for the GPU texture budget, so a small window still keeps a prefetch ring resident. */
    private static final int MIN_TEXTURES = 220;
    /** Ceiling, matching the tile cache: 4K at scale 1 draws ~660 tiles at once. */
    private static final int MAX_TEXTURES = IrisClientTileCache.ABSOLUTE_MAX_CACHED_TILES;
    /** Extra rings of tiles kept resident beyond what is on screen right now. */
    private static final int TEXTURE_SLACK_RINGS = 1;
    private static final int BACKGROUND_COLOR = 0xF00B0E14;
    private static final int PLACEHOLDER_COLOR = 0xFF161A22;
    private static final int GRID_COLOR = 0x33FFFFFF;
    private static final int HEADER_COLOR = 0xC0101010;
    private static final int TITLE_COLOR = 0xFF66BB6A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFB9B9B9;
    private static final int PLAYER_FILL_COLOR = 0xFFFFDD33;
    private static final int PLAYER_BORDER_COLOR = 0xFF101010;
    private static final int MARKER_COLOR = 0xFF4FC3F7;
    private static final int MARKER_LABEL_BG = 0xE0101010;

    private final Map<IrisTileKey, TileTexture> textures;
    private double centerBlockX;
    private double centerBlockZ;
    private int zoom;
    private boolean initialized;
    private IrisMessage.DimensionStatus renderedDimension;

    public IrisVisionScreen() {
        super(Component.literal(IrisLanguage.plain(ClientUiMessages.VISION_TITLE)));
        this.textures = new LinkedHashMap<>(64, 0.75f, true);
        this.centerBlockX = 0.0D;
        this.centerBlockZ = 0.0D;
        this.zoom = DEFAULT_ZOOM;
        this.initialized = false;
        this.renderedDimension = null;
    }

    @Override
    protected void init() {
        if (!initialized) {
            centerOnPlayer();
            initialized = true;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND_COLOR);
        IrisClientSession session = IrisClient.session();
        if (!session.isReady()) {
            drawCentered(graphics, handshakeMessage(session), MUTED_COLOR);
            drawHeader(graphics, IrisLanguage.plain(ClientUiMessages.VISION_TITLE), IrisLanguage.plain(ClientUiMessages.VISION_NOT_CONNECTED));
            return;
        }
        IrisMessage.DimensionStatus status = IrisClient.dimension().status();
        if (!IrisClient.visionAvailable() || status == null) {
            drawCentered(graphics, IrisLanguage.plain(ClientUiMessages.VISION_NOT_IRIS_WORLD), MUTED_COLOR);
            drawHeader(graphics, IrisLanguage.plain(ClientUiMessages.VISION_TITLE), status == null ? IrisLanguage.plain(ClientUiMessages.VISION_NO_DIMENSION_DATA) : label(status));
            return;
        }
        syncWorld(status);
        renderTiles(graphics, mouseX, mouseY, status);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The map itself has nothing clickable, but swallowing every click also swallows the ones widgets and
        // the parent screen need. Let Screen route it; drag and scroll are handled below.
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int blocksPerPixel = 1 << zoom;
        centerBlockX -= dragX * blocksPerPixel;
        centerBlockZ -= dragY * blocksPerPixel;
        return true;
    }

    /**
     * Known limitation, not a bug to chase: a zoom change makes every cached tile the wrong scale, because the
     * zoom level is part of the tile key. The new level's tiles are re-requested at 8/s, so the map repaints
     * from placeholders. Rendering the old level scaled would need a second texture path for a transient view.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0D) {
            return false;
        }
        int previousBlocksPerPixel = 1 << zoom;
        double anchorBlockX = centerBlockX + (mouseX - width / 2.0D) * previousBlocksPerPixel;
        double anchorBlockZ = centerBlockZ + (mouseY - height / 2.0D) * previousBlocksPerPixel;
        int updated = zoom + (scrollY > 0.0D ? -1 : 1);
        zoom = Math.max(MIN_ZOOM, Math.min(updated, MAX_ZOOM));
        int newBlocksPerPixel = 1 << zoom;
        centerBlockX = anchorBlockX - (mouseX - width / 2.0D) * newBlocksPerPixel;
        centerBlockZ = anchorBlockZ - (mouseY - height / 2.0D) * newBlocksPerPixel;
        return true;
    }

    @Override
    public void removed() {
        releaseTextures();
        super.removed();
    }

    private void renderTiles(GuiGraphicsExtractor graphics, int mouseX, int mouseY, IrisMessage.DimensionStatus status) {
        int blocksPerPixel = 1 << zoom;
        int tileSpanBlocks = TILE_PIXELS * blocksPerPixel;
        int originX = (int) Math.floor(width / 2.0D - centerBlockX / blocksPerPixel);
        int originY = (int) Math.floor(height / 2.0D - centerBlockZ / blocksPerPixel);
        int minTileX = (int) Math.floor((0.0D - originX) / TILE_PIXELS) - 1;
        int maxTileX = (int) Math.floor((width - originX) / (double) TILE_PIXELS) + 1;
        int minTileZ = (int) Math.floor((0.0D - originY) / TILE_PIXELS) - 1;
        int maxTileZ = (int) Math.floor((height - originY) / (double) TILE_PIXELS) + 1;
        int centerTileX = Math.floorDiv((int) Math.floor(centerBlockX), tileSpanBlocks);
        int centerTileZ = Math.floorDiv((int) Math.floor(centerBlockZ), tileSpanBlocks);

        int visibleTiles = (maxTileX - minTileX + 1) * (maxTileZ - minTileZ + 1);
        IrisClientTileCache cache = IrisClient.tiles();
        cache.ensureCapacity(visibleTiles);
        cache.resetRequestQueue();
        List<IrisTileKey> missing = new ArrayList<>();

        // Evict before the blit loop, never after. A DynamicTexture released mid-frame is a closed GPU handle
        // that the already-recorded blit still points at, which is a use-after-close on the render thread.
        evictTextures(textureCapacity(visibleTiles));

        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                int screenX = originX + tileX * TILE_PIXELS;
                int screenY = originY + tileZ * TILE_PIXELS;
                IrisTileKey key = new IrisTileKey(tileX, tileZ, zoom);
                IrisTileImage image = cache.get(key);
                if (image == null) {
                    graphics.fill(screenX, screenY, screenX + TILE_PIXELS, screenY + TILE_PIXELS, PLACEHOLDER_COLOR);
                    missing.add(key);
                    continue;
                }
                Identifier texture = ensureTexture(key, image);
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, screenX, screenY, 0.0F, 0.0F, TILE_PIXELS, TILE_PIXELS, TILE_PIXELS, TILE_PIXELS);
            }
        }

        requestMissing(cache, missing, centerTileX, centerTileZ);
        cache.pump();

        drawMarkers(graphics, mouseX, mouseY, minTileX, maxTileX, minTileZ, maxTileZ, originX, originY, blocksPerPixel);
        drawPlayer(graphics, blocksPerPixel);
        drawHeader(
                graphics,
                IrisLanguage.plain(ClientUiMessages.VISION_TITLE),
                IrisLanguage.plain(
                        ClientUiMessages.VISION_HEADER_DETAIL,
                        MessageArgument.trusted("status", label(status)),
                        MessageArgument.trusted("zoom", zoom),
                        MessageArgument.trusted("x", (long) centerBlockX),
                        MessageArgument.trusted("z", (long) centerBlockZ)
                )
        );
        drawFooter(graphics, IrisLanguage.plain(ClientUiMessages.VISION_FOOTER_HINT));
    }

    private void requestMissing(IrisClientTileCache cache, List<IrisTileKey> missing, int centerTileX, int centerTileZ) {
        missing.sort((left, right) -> Long.compare(distanceSquared(left, centerTileX, centerTileZ), distanceSquared(right, centerTileX, centerTileZ)));
        for (IrisTileKey key : missing) {
            cache.request(key);
        }
    }

    private void drawMarkers(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int minTileX, int maxTileX, int minTileZ, int maxTileZ, int originX, int originY, int blocksPerPixel) {
        IrisClientMarkers markers = IrisClient.markers();
        String hoverLabel = null;
        int hoverX = 0;
        int hoverY = 0;
        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                List<IrisMessage.VisionMarkers.Marker> tileMarkers = markers.forTile(new IrisTileKey(tileX, tileZ, zoom));
                if (tileMarkers == null) {
                    continue;
                }
                for (IrisMessage.VisionMarkers.Marker marker : tileMarkers) {
                    int screenX = originX + (int) Math.floor((double) marker.blockX() / blocksPerPixel);
                    int screenY = originY + (int) Math.floor((double) marker.blockZ() / blocksPerPixel);
                    graphics.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, MARKER_COLOR);
                    if (Math.abs(mouseX - screenX) <= 4 && Math.abs(mouseY - screenY) <= 4 && marker.label() != null && !marker.label().isEmpty()) {
                        hoverLabel = marker.label();
                        hoverX = screenX;
                        hoverY = screenY;
                    }
                }
            }
        }
        if (hoverLabel != null) {
            int labelWidth = font.width(hoverLabel);
            graphics.fill(hoverX + 6, hoverY - 6, hoverX + 12 + labelWidth, hoverY + font.lineHeight, MARKER_LABEL_BG);
            graphics.text(font, hoverLabel, hoverX + 9, hoverY - 4, TEXT_COLOR);
        }
    }

    private void drawPlayer(GuiGraphicsExtractor graphics, int blocksPerPixel) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int screenX = (int) Math.round(width / 2.0D + (player.getBlockX() - centerBlockX) / blocksPerPixel);
        int screenY = (int) Math.round(height / 2.0D + (player.getBlockZ() - centerBlockZ) / blocksPerPixel);
        graphics.fill(screenX - 4, screenY - 4, screenX + 4, screenY + 4, PLAYER_BORDER_COLOR);
        graphics.fill(screenX - 3, screenY - 3, screenX + 3, screenY + 3, PLAYER_FILL_COLOR);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, String title, String detail) {
        int lineHeight = font.lineHeight;
        int headerHeight = lineHeight + 8;
        graphics.fill(0, 0, width, headerHeight, HEADER_COLOR);
        graphics.text(font, title, 8, 4, TITLE_COLOR);
        graphics.text(font, detail, 12 + font.width(title), 4, MUTED_COLOR);
    }

    private void drawFooter(GuiGraphicsExtractor graphics, String hint) {
        int lineHeight = font.lineHeight;
        int footerTop = height - lineHeight - 8;
        graphics.fill(0, footerTop, width, height, HEADER_COLOR);
        graphics.text(font, hint, 8, footerTop + 4, MUTED_COLOR);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int color) {
        graphics.text(font, text, (width - font.width(text)) / 2, height / 2 - font.lineHeight / 2, color);
    }

    private Identifier ensureTexture(IrisTileKey key, IrisTileImage image) {
        TileTexture existing = textures.get(key);
        if (existing != null) {
            if (existing.image() == image) {
                return existing.id();
            }
            // IrisClientTileCache replaced the tile with a freshly decoded image (new sequence from the
            // server). Identity is the generation counter: a re-decode is always a new record instance, so an
            // upload keyed only on the tile coordinates would show the stale render forever.
            Minecraft.getInstance().getTextureManager().release(existing.id());
            textures.remove(key);
        }
        int tileWidth = image.width();
        int tileHeight = image.height();
        int[] argb = image.argb();
        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, tileWidth, tileHeight, false);
        for (int y = 0; y < tileHeight; y++) {
            int row = y * tileWidth;
            for (int x = 0; x < tileWidth; x++) {
                nativeImage.setPixelABGR(x, y, toAbgr(argb[row + x]));
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> "iris_vision_tile", nativeImage);
        Identifier id = Identifier.fromNamespaceAndPath("irisworldgen", texturePath(key));
        Minecraft.getInstance().getTextureManager().register(id, texture);
        textures.put(key, new TileTexture(id, image));
        return id;
    }

    /**
     * Texture budget for the current viewport: everything on screen plus a slack ring, clamped to sane bounds.
     * A fixed 220 is under half of what 4K at scale 1 draws, so the eviction pass used to fight the blit loop.
     */
    private static int textureCapacity(int visibleTiles) {
        long required = (long) visibleTiles * (1 + 2 * TEXTURE_SLACK_RINGS);
        return (int) Math.max(MIN_TEXTURES, Math.min(MAX_TEXTURES, required));
    }

    private void evictTextures(int capacity) {
        if (textures.size() <= capacity) {
            return;
        }
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        Iterator<Map.Entry<IrisTileKey, TileTexture>> iterator = textures.entrySet().iterator();
        while (textures.size() > capacity && iterator.hasNext()) {
            Map.Entry<IrisTileKey, TileTexture> entry = iterator.next();
            manager.release(entry.getValue().id());
            iterator.remove();
        }
    }

    private void releaseTextures() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            TextureManager manager = minecraft.getTextureManager();
            for (TileTexture texture : textures.values()) {
                manager.release(texture.id());
            }
        }
        textures.clear();
    }

    private void syncWorld(IrisMessage.DimensionStatus status) {
        if (renderedDimension == null) {
            renderedDimension = status;
            return;
        }
        if (!renderedDimension.equals(status)) {
            renderedDimension = status;
            releaseTextures();
            centerOnPlayer();
        }
    }

    private void centerOnPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            centerBlockX = player.getBlockX();
            centerBlockZ = player.getBlockZ();
        }
    }

    /**
     * Distinct copy per terminal handshake state. UNSUPPORTED means the hello retries ran out with no answer -
     * no Iris on the server. INCOMPATIBLE means Iris answered with a different wire version. Reporting either
     * as "connecting" left the screen sitting on a spinner that would never resolve.
     */
    private static String handshakeMessage(IrisClientSession session) {
        return switch (session.state()) {
            case UNSUPPORTED -> IrisLanguage.plain(ClientUiMessages.VISION_SERVER_WITHOUT_IRIS);
            case INCOMPATIBLE -> IrisLanguage.plain(ClientUiMessages.VISION_VERSION_MISMATCH);
            default -> IrisLanguage.plain(ClientUiMessages.VISION_CONNECTING);
        };
    }

    private static long distanceSquared(IrisTileKey key, int centerTileX, int centerTileZ) {
        long deltaX = (long) key.tileX() - centerTileX;
        long deltaZ = (long) key.tileZ() - centerTileZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static int toAbgr(int argb) {
        int alpha = argb >>> 24 & 0xFF;
        int red = argb >> 16 & 0xFF;
        int green = argb >> 8 & 0xFF;
        int blue = argb & 0xFF;
        return alpha << 24 | blue << 16 | green << 8 | red;
    }

    private static String texturePath(IrisTileKey key) {
        return "vision/t_" + safe(key.tileX()) + "_" + safe(key.tileZ()) + "_" + key.zoom();
    }

    private static String safe(int value) {
        return value < 0 ? "m" + (-(long) value) : Integer.toString(value);
    }

    private static String label(IrisMessage.DimensionStatus status) {
        if (status.packKey() == null || status.packKey().isEmpty()) {
            return String.valueOf(status.dimensionKey());
        }
        return IrisLanguage.plain(
                ClientUiMessages.VISION_DIMENSION_PACK,
                MessageArgument.untrusted("dimension", String.valueOf(status.dimensionKey())),
                MessageArgument.untrusted("pack", status.packKey())
        );
    }

    /**
     * The registered texture id plus the exact image instance uploaded into it. The DynamicTexture itself is
     * not retained: TextureManager owns it after register, and release(id) is the only handle needed. Holding
     * the source image is what lets {@link #ensureTexture(IrisTileKey, IrisTileImage)} notice a re-decode.
     */
    private record TileTexture(Identifier id, IrisTileImage image) {
    }
}
