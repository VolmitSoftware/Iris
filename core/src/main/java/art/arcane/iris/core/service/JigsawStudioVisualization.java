package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.object.IrisDirection;
import art.arcane.iris.engine.object.IrisJigsawConnector;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.iris.core.service.JigsawStudioService.message;

final class JigsawStudioVisualization {
    private static final int VISUAL_INTERVAL_TICKS = 8;
    private static final int PARTICLE_BUDGET = 384;
    private static final double VISUAL_RANGE = 96.0D;
    private static final double VISUAL_RANGE_SQUARED = VISUAL_RANGE * VISUAL_RANGE;
    private static final Color SELECTED_COLOR = Color.AQUA;
    private static final Color NEARBY_COLOR = Color.fromRGB(92, 102, 118);
    private static final Color INVALID_BAY_COLOR = Color.RED;
    private static final Color INVALID_CONNECTOR_COLOR = Color.RED;
    private static final Color VALID_CONNECTOR_COLOR = Color.LIME;
    private static final Color ASSEMBLY_PREVIEW_COLOR = Color.fromRGB(180, 90, 255);
    private static final Color LIVE_PREVIEW_WARNING_COLOR = Color.fromRGB(255, 180, 55);
    private static final long ASSEMBLY_PREVIEW_MILLIS = 10_000L;

    private final JigsawStudioService service;
    final Set<UUID> visualizationLoops = ConcurrentHashMap.newKeySet();
    final Map<UUID, AssemblyPreview> assemblyPreviews = new ConcurrentHashMap<>();

    JigsawStudioVisualization(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean showAssemblyPreview(Player player, List<PlacedStructurePiece> pieces) {
        if (player == null || pieces == null || pieces.isEmpty()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> showAssemblyPreview(player, pieces));
        }
        if (!service.studios.containsKey(player.getWorld().getUID())) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        List<JigsawStudioPreviewRenderer.PreviewBounds> bounds = new ArrayList<>(pieces.size());
        for (PlacedStructurePiece piece : pieces) {
            bounds.add(new JigsawStudioPreviewRenderer.PreviewBounds(
                    piece.getMinX(),
                    piece.getMinY(),
                    piece.getMinZ(),
                    piece.getMaxX(),
                    piece.getMaxY(),
                    piece.getMaxZ()));
        }
        assemblyPreviews.put(player.getUniqueId(), new AssemblyPreview(
                player.getWorld().getUID(),
                System.currentTimeMillis() + ASSEMBLY_PREVIEW_MILLIS,
                List.copyOf(bounds)));
        service.particlesDisabled.remove(player.getUniqueId());
        ensureVisualizationLoop(player);
        return true;
    }

    void ensureVisualizationLoop(Player player) {
        if (player == null) {
            return;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            J.runEntity(player, () -> ensureVisualizationLoop(player));
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!service.enabled || service.particlesDisabled.contains(playerId)
                || !service.studios.containsKey(player.getWorld().getUID())
                || !visualizationLoops.add(playerId)) {
            return;
        }
        visualizationTick(player);
    }

    private void visualizationTick(Player player) {
        UUID playerId = player.getUniqueId();
        if (!service.enabled || service.particlesDisabled.contains(playerId)) {
            visualizationLoops.remove(playerId);
            return;
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null) {
            visualizationLoops.remove(playerId);
            return;
        }
        try {
            renderVisualization(player, studio);
        } catch (Throwable exception) {
            visualizationLoops.remove(playerId);
            IrisLogging.reportError(exception);
            return;
        }
        boolean scheduled = J.runEntity(
                player,
                () -> visualizationTick(player),
                VISUAL_INTERVAL_TICKS,
                () -> visualizationLoops.remove(playerId));
        if (!scheduled) {
            visualizationLoops.remove(playerId);
        }
    }

    private void renderVisualization(Player player, ActiveStudio studio) {
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioLayout layout = session.layout();
        Location playerLocation = player.getLocation();
        JigsawStudioBay focused = layout.findAt(
                playerLocation.getBlockX(),
                playerLocation.getBlockY(),
                playerLocation.getBlockZ());
        ParticleBudget budget = new ParticleBudget(PARTICLE_BUDGET);
        if (focused != null) {
            Color focusedColor = bayValid(studio, focused) ? SELECTED_COLOR : INVALID_BAY_COLOR;
            drawConnectors(player, playerLocation, studio, focused, budget);
            drawBounds(player, playerLocation, focused.bounds(), focusedColor, 1.15F, 1.0D, budget);
        }
        drawLivePreview(player, playerLocation, studio, budget);
        drawAssemblyPreview(player, playerLocation, budget);
        for (JigsawStudioBay bay : layout.bays()) {
            if (bay == focused || !isNearby(playerLocation, bay.bounds())) {
                continue;
            }
            Color nearbyColor = bayValid(studio, bay) ? NEARBY_COLOR : INVALID_BAY_COLOR;
            drawBounds(player, playerLocation, bay.bounds(), nearbyColor, 0.55F, 4.0D, budget);
            if (budget.empty()) {
                return;
            }
        }
    }

    private void drawLivePreview(
            Player player,
            Location playerLocation,
            ActiveStudio studio,
            ParticleBudget budget
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        JigsawStudioGraphEvaluation evaluation = service.evaluator.evaluations.get(requestId);
        if (evaluation == null || evaluation.previewBounds().isEmpty()) {
            return;
        }
        JigsawStudioPreviewRenderer.PreviewBounds preview = evaluation.previewBounds();
        Color color = switch (evaluation.state()) {
            case VALID -> ASSEMBLY_PREVIEW_COLOR;
            case PENDING, WARNING, STALE -> LIVE_PREVIEW_WARNING_COLOR;
            case INVALID -> INVALID_BAY_COLOR;
        };
        drawBounds(
                player,
                playerLocation,
                preview,
                color,
                0.85F,
                2.0D,
                budget);
    }

    private void drawAssemblyPreview(
            Player player,
            Location playerLocation,
            ParticleBudget budget
    ) {
        AssemblyPreview preview = assemblyPreviews.get(player.getUniqueId());
        if (preview == null) {
            return;
        }
        if (!preview.worldId().equals(player.getWorld().getUID())
                || preview.expiresAtMillis() < System.currentTimeMillis()) {
            assemblyPreviews.remove(player.getUniqueId(), preview);
            return;
        }
        for (JigsawStudioPreviewRenderer.PreviewBounds bounds : preview.bounds()) {
            drawBounds(player, playerLocation, bounds, ASSEMBLY_PREVIEW_COLOR, 0.85F, 2.0D, budget);
            if (budget.empty()) {
                return;
            }
        }
    }

    private static void drawConnectors(
            Player player,
            Location playerLocation,
            ActiveStudio studio,
            JigsawStudioBay bay,
            ParticleBudget budget
    ) {
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(bay);
        if (!rendered.valid()) {
            return;
        }
        for (JigsawStudioGenerator.RenderedConnector renderedConnector : rendered.connectors()) {
            IrisJigsawConnector connector = renderedConnector.connector();
            IrisDirection direction = connector.getDirection();
            drawConnectorLine(
                    player,
                    playerLocation,
                    bay.bounds().originX() + renderedConnector.x() + 0.5D,
                    bay.bounds().originY() + renderedConnector.y() + 0.5D,
                    bay.bounds().originZ() + renderedConnector.z() + 0.5D,
                    direction,
                    connectorColor(connector),
                    budget);
            if (budget.empty()) {
                return;
            }
        }
    }

    private static void drawConnectorLine(
            Player player,
            Location playerLocation,
            double startX,
            double startY,
            double startZ,
            IrisDirection direction,
            Color color,
            ParticleBudget budget
    ) {
        drawLine(
                player,
                playerLocation,
                startX,
                startY,
                startZ,
                startX + direction.x() * 1.75D,
                startY + direction.y() * 1.75D,
                startZ + direction.z() * 1.75D,
                color,
                1.05F,
                0.35D,
                budget);
    }

    private static boolean bayValid(ActiveStudio studio, JigsawStudioBay bay) {
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(bay);
        return rendered.valid() && studio.population(bay).readiness().failure().isEmpty();
    }

    private static Color connectorColor(IrisJigsawConnector connector) {
        if (connector.getPool() == null || connector.getPool().isBlank()
                || connector.getName() == null || connector.getName().isBlank()
                || connector.getTargetName() == null || connector.getTargetName().isBlank()) {
            return INVALID_CONNECTOR_COLOR;
        }
        String channel = connector.getChannel();
        if (channel == null || channel.isBlank()) {
            return VALID_CONNECTOR_COLOR;
        }
        int hash = channel.toLowerCase(Locale.ROOT).hashCode();
        int red = 80 + (hash & 127);
        int green = 80 + ((hash >>> 8) & 127);
        int blue = 80 + ((hash >>> 16) & 127);
        return Color.fromRGB(red, green, blue);
    }

    private static void drawBounds(
            Player player,
            Location playerLocation,
            JigsawStudioBounds bounds,
            Color color,
            float size,
            double step,
            ParticleBudget budget
    ) {
        drawBounds(player, playerLocation, new JigsawStudioPreviewRenderer.PreviewBounds(
                bounds.originX(), bounds.originY(), bounds.originZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                color, size, step, budget);
    }

    private static void drawBounds(
            Player player,
            Location playerLocation,
            JigsawStudioPreviewRenderer.PreviewBounds bounds,
            Color color,
            float size,
            double step,
            ParticleBudget budget
    ) {
        double minX = bounds.minimumX();
        double minY = bounds.minimumY();
        double minZ = bounds.minimumZ();
        double maxX = bounds.maximumX() + 1.0D;
        double maxY = bounds.maximumY() + 1.0D;
        double maxZ = bounds.maximumZ() + 1.0D;
        drawLine(player, playerLocation, minX, minY, minZ, maxX, minY, minZ, color, size, step, budget);
        drawLine(player, playerLocation, minX, minY, maxZ, maxX, minY, maxZ, color, size, step, budget);
        drawLine(player, playerLocation, minX, maxY, minZ, maxX, maxY, minZ, color, size, step, budget);
        drawLine(player, playerLocation, minX, maxY, maxZ, maxX, maxY, maxZ, color, size, step, budget);
        drawLine(player, playerLocation, minX, minY, minZ, minX, maxY, minZ, color, size, step, budget);
        drawLine(player, playerLocation, maxX, minY, minZ, maxX, maxY, minZ, color, size, step, budget);
        drawLine(player, playerLocation, minX, minY, maxZ, minX, maxY, maxZ, color, size, step, budget);
        drawLine(player, playerLocation, maxX, minY, maxZ, maxX, maxY, maxZ, color, size, step, budget);
        drawLine(player, playerLocation, minX, minY, minZ, minX, minY, maxZ, color, size, step, budget);
        drawLine(player, playerLocation, maxX, minY, minZ, maxX, minY, maxZ, color, size, step, budget);
        drawLine(player, playerLocation, minX, maxY, minZ, minX, maxY, maxZ, color, size, step, budget);
        drawLine(player, playerLocation, maxX, maxY, minZ, maxX, maxY, maxZ, color, size, step, budget);
    }

    private static void drawLine(
            Player player,
            Location playerLocation,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            Color color,
            float size,
            double step,
            ParticleBudget budget
    ) {
        if (budget.empty()) {
            return;
        }
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double deltaZ = endZ - startZ;
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        int samples = Math.max(1, (int) Math.ceil(length / step));
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int index = 0; index <= samples; index++) {
            double progress = (double) index / samples;
            double x = startX + deltaX * progress;
            double y = startY + deltaY * progress;
            double z = startZ + deltaZ * progress;
            double distanceX = playerLocation.getX() - x;
            double distanceY = playerLocation.getY() - y;
            double distanceZ = playerLocation.getZ() - z;
            if (distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ > VISUAL_RANGE_SQUARED) {
                continue;
            }
            if (!budget.consume()) {
                return;
            }
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
        }
    }

    private static boolean isNearby(Location location, JigsawStudioBounds bounds) {
        double centerX = bounds.originX() + bounds.dimensions().width() / 2.0D;
        double centerY = bounds.originY() + bounds.dimensions().height() / 2.0D;
        double centerZ = bounds.originZ() + bounds.dimensions().depth() / 2.0D;
        double deltaX = location.getX() - centerX;
        double deltaY = location.getY() - centerY;
        double deltaZ = location.getZ() - centerZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= VISUAL_RANGE_SQUARED;
    }

    private record AssemblyPreview(
            UUID worldId,
            long expiresAtMillis,
            List<JigsawStudioPreviewRenderer.PreviewBounds> bounds
    ) {
        AssemblyPreview {
            Objects.requireNonNull(worldId, "Jigsaw Studio preview world");
            bounds = List.copyOf(bounds);
        }
    }

    private static final class ParticleBudget {
        private int remaining;

        private ParticleBudget(int remaining) {
            this.remaining = remaining;
        }

        private boolean consume() {
            if (remaining < 1) {
                return false;
            }
            remaining--;
            return true;
        }

        private boolean empty() {
            return remaining < 1;
        }
    }
}
