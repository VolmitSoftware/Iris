package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.CapturedConnector;
import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.ChunkCaptureArea;
import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.LocalPosition;
import art.arcane.iris.studio.jigsaw.JigsawStudioSaveLifecycle.BayReadiness;
import art.arcane.iris.studio.jigsaw.JigsawStudioService.ActiveStudio;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Jigsaw;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.iris.studio.jigsaw.JigsawStudioCapture.inversePosition;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.chunkKey;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.failureMessage;

final class JigsawStudioChunkWriter {
    private JigsawStudioChunkWriter() {
    }

    static void restoreConnectorChunk(
            World world,
            JigsawStudioBay workcell,
            List<JigsawStudioGenerator.RenderedConnector> connectors,
            Map<LocalPosition, JigsawStudioGenerator.RenderedBlock> renderedBlocks,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = workcell.bounds();
        for (JigsawStudioGenerator.RenderedConnector connector : connectors) {
            LocalPosition position = new LocalPosition(connector.x(), connector.y(), connector.z());
            Block target = world.getBlockAt(
                    bounds.originX() + connector.x(),
                    bounds.originY() + connector.y(),
                    bounds.originZ() + connector.z());
            if (connectorsVisible) {
                BlockData marker;
                try {
                    marker = Bukkit.createBlockData(
                            "minecraft:jigsaw[orientation=" + connector.orientation() + "]");
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid saved connector orientation '"
                            + connector.orientation() + "'", exception);
                }
                target.setBlockData(marker, false);
                BukkitPlatform.deserializeTile(markerNbt(connector.connector()), target.getLocation());
                continue;
            }
            JigsawStudioGenerator.RenderedBlock renderedBlock = renderedBlocks.get(position);
            BlockData restored;
            if (renderedBlock != null) {
                if (renderedBlock.state().isCustom()
                        || !(renderedBlock.state().nativeHandle() instanceof BlockData blockData)) {
                    throw new IOException("Saved connector block '" + renderedBlock.state().key()
                            + "' cannot be restored directly in Bukkit Studio");
                }
                restored = blockData;
            } else {
                try {
                    restored = Bukkit.createBlockData(connector.connector().getFinalState());
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid saved connector final state '"
                            + connector.connector().getFinalState() + "'", exception);
                }
            }
            target.setBlockData(restored, false);
            if (renderedBlock != null && renderedBlock.tileData() != null) {
                TileData tileData = renderedBlock.tileData();
                if (!tileData.isApplicable(target.getBlockData()) || !tileData.toBukkitTry(target)) {
                    throw new IOException("Saved connector tile data could not be restored at "
                            + target.getX() + "," + target.getY() + "," + target.getZ());
                }
            }
        }
    }

    static String validateMaterialization(JigsawStudioGenerator.RenderedBay rendered) {
        if (!rendered.valid()) {
            return rendered.failure();
        }
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            PlatformBlockState state = block.state();
            if (state.isCustom()) {
                return "custom block '" + state.key()
                        + "' requires provider-owned placement and cannot be swapped live in Studio";
            }
            if (!(state.nativeHandle() instanceof BlockData)) {
                return "block '" + state.key() + "' has no Bukkit block-data representation";
            }
        }
        for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
            try {
                BlockData data = Bukkit.createBlockData(
                        "minecraft:jigsaw[orientation=" + connector.orientation() + "]");
                if (!(data instanceof Jigsaw)) {
                    return "connector orientation '" + connector.orientation()
                            + "' did not resolve to a jigsaw marker";
                }
            } catch (IllegalArgumentException exception) {
                return "connector orientation '" + connector.orientation() + "' is invalid";
            }
        }
        return "";
    }

    static void writeMaterializedChunk(
            World world,
            JigsawStudioBay workcell,
            JigsawStudioGenerator.RenderedBay rendered,
            ChunkCaptureArea area,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = workcell.bounds();
        Map<LocalPosition, BlockData> expected = materializedBlockData(rendered, connectorsVisible);
        BlockData air = Material.AIR.createBlockData();
        for (int x = area.minimumX(); x < area.maximumX(); x++) {
            for (int y = 0; y < bounds.dimensions().height(); y++) {
                for (int z = area.minimumZ(); z < area.maximumZ(); z++) {
                    Block target = world.getBlockAt(
                            bounds.originX() + x,
                            bounds.originY() + y,
                            bounds.originZ() + z);
                    BlockData blockData = expected.getOrDefault(new LocalPosition(x, y, z), air);
                    target.setBlockData(blockData, false);
                }
            }
        }
        applyRenderedBayChunk(world, workcell, rendered, area.chunkX(), area.chunkZ(), connectorsVisible);
        verifyMaterializedChunk(world, workcell, rendered, area, expected, connectorsVisible);
    }

    private static Map<LocalPosition, BlockData> materializedBlockData(
            JigsawStudioGenerator.RenderedBay rendered,
            boolean connectorsVisible
    ) throws IOException {
        Map<LocalPosition, BlockData> expected = new HashMap<>();
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            if (block.state().isCustom() || !(block.state().nativeHandle() instanceof BlockData blockData)) {
                throw new IOException("Rendered block '" + block.state().key()
                        + "' cannot be placed directly in Bukkit Studio");
            }
            expected.put(new LocalPosition(block.x(), block.y(), block.z()), blockData);
        }
        for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
            if (!connectorsVisible && expected.containsKey(
                    new LocalPosition(connector.x(), connector.y(), connector.z()))) {
                continue;
            }
            BlockData marker;
            try {
                marker = Bukkit.createBlockData(connectorsVisible
                        ? "minecraft:jigsaw[orientation=" + connector.orientation() + "]"
                        : connector.connector().getFinalState());
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid rendered connector orientation '"
                        + connector.orientation() + "'", exception);
            }
            expected.put(new LocalPosition(connector.x(), connector.y(), connector.z()), marker);
        }
        return expected;
    }

    private static void verifyMaterializedChunk(
            World world,
            JigsawStudioBay workcell,
            JigsawStudioGenerator.RenderedBay rendered,
            ChunkCaptureArea area,
            Map<LocalPosition, BlockData> expected,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = workcell.bounds();
        BlockData air = Material.AIR.createBlockData();
        for (int x = area.minimumX(); x < area.maximumX(); x++) {
            for (int y = 0; y < bounds.dimensions().height(); y++) {
                for (int z = area.minimumZ(); z < area.maximumZ(); z++) {
                    Block target = world.getBlockAt(
                            bounds.originX() + x,
                            bounds.originY() + y,
                            bounds.originZ() + z);
                    BlockData expectedData = expected.getOrDefault(new LocalPosition(x, y, z), air);
                    if (!target.getBlockData().equals(expectedData)) {
                        throw new IOException("Workcell block did not materialize at "
                                + target.getX() + "," + target.getY() + "," + target.getZ());
                    }
                }
            }
        }
        verifyRenderedBayChunk(world, workcell, rendered, area.chunkX(), area.chunkZ(), connectorsVisible);
    }

    static boolean hydrateChunk(
            ActiveStudio studio,
            World world,
            int chunkX,
            int chunkZ
    ) {
        long key = chunkKey(chunkX, chunkZ);
        boolean verificationRequired = false;
        for (JigsawStudioBay bay : studio.generator().getLayout().bays()) {
            BayPopulation population = studio.population(bay);
            if (!population.needsApplication(key) && !population.needsVerification(key)) {
                continue;
            }
            JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderBay(bay);
            if (!rendered.valid()) {
                population.fail(rendered.failure());
                continue;
            }
            try {
                if (population.needsApplication(key)) {
                    applyRenderedBayChunk(
                            world,
                            bay,
                            rendered,
                            chunkX,
                            chunkZ,
                            studio.generator().getSession().workcellSnapshot(bay.stableId()).connectorsVisible());
                    population.markApplied(key);
                    verificationRequired = true;
                } else {
                    verifyRenderedBayChunk(
                            world,
                            bay,
                            rendered,
                            chunkX,
                            chunkZ,
                            studio.generator().getSession().workcellSnapshot(bay.stableId()).connectorsVisible());
                    population.markHydrated(key);
                }
            } catch (Throwable exception) {
                if (population.fail("chunk " + chunkX + "," + chunkZ
                        + " could not hydrate: " + failureMessage(exception))) {
                    IrisLogging.reportError(exception);
                }
            }
        }
        return verificationRequired;
    }

    static void applyRenderedBayChunk(
            World world,
            JigsawStudioBay bay,
            JigsawStudioGenerator.RenderedBay rendered,
            int chunkX,
            int chunkZ,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = bay.bounds();
        Set<LocalPosition> connectorPositions = new HashSet<>(rendered.connectors().size());
        if (connectorsVisible) {
            for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
                connectorPositions.add(new LocalPosition(connector.x(), connector.y(), connector.z()));
            }
        }
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            TileData tileData = block.tileData();
            if (tileData == null
                    || connectorPositions.contains(new LocalPosition(block.x(), block.y(), block.z()))) {
                continue;
            }
            int worldX = bounds.originX() + block.x();
            int worldZ = bounds.originZ() + block.z();
            if ((worldX >> 4) != chunkX || (worldZ >> 4) != chunkZ) {
                continue;
            }
            Block target = world.getBlockAt(worldX, bounds.originY() + block.y(), worldZ);
            if (!tileData.isApplicable(target.getBlockData())) {
                throw new IOException("source tile state does not match rendered block at "
                        + target.getX() + "," + target.getY() + "," + target.getZ());
            }
            if (!tileData.toBukkitTry(target)) {
                throw new IOException("source tile state could not hydrate at "
                        + target.getX() + "," + target.getY() + "," + target.getZ());
            }
        }
        if (!connectorsVisible) {
            return;
        }
        for (JigsawStudioGenerator.RenderedConnector renderedConnector : rendered.connectors()) {
            int worldX = bounds.originX() + renderedConnector.x();
            int worldZ = bounds.originZ() + renderedConnector.z();
            if ((worldX >> 4) != chunkX || (worldZ >> 4) != chunkZ) {
                continue;
            }
            Block target = world.getBlockAt(
                    worldX,
                    bounds.originY() + renderedConnector.y(),
                    worldZ);
            BlockData blockData = target.getBlockData();
            if (!(blockData instanceof Jigsaw jigsaw)) {
                throw new IOException("expected a jigsaw marker at "
                        + target.getX() + "," + target.getY() + "," + target.getZ());
            }
            String actualOrientation = jigsaw.getOrientation().name().toLowerCase(Locale.ROOT);
            if (!actualOrientation.equals(renderedConnector.orientation())) {
                throw new IOException("jigsaw marker orientation at "
                        + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " is " + actualOrientation + " instead of " + renderedConnector.orientation());
            }
            KMap<String, Object> expected = markerNbt(renderedConnector.connector());
            BukkitPlatform.deserializeTile(expected, target.getLocation());
        }
    }

    static void verifyRenderedBayChunk(
            World world,
            JigsawStudioBay bay,
            JigsawStudioGenerator.RenderedBay rendered,
            int chunkX,
            int chunkZ,
            boolean connectorsVisible
    ) throws IOException {
        JigsawStudioBounds bounds = bay.bounds();
        Set<LocalPosition> connectorPositions = new HashSet<>(rendered.connectors().size());
        if (connectorsVisible) {
            for (JigsawStudioGenerator.RenderedConnector connector : rendered.connectors()) {
                connectorPositions.add(new LocalPosition(connector.x(), connector.y(), connector.z()));
            }
        }
        for (JigsawStudioGenerator.RenderedBlock block : rendered.blocks()) {
            TileData tileData = block.tileData();
            if (tileData == null
                    || connectorPositions.contains(new LocalPosition(block.x(), block.y(), block.z()))) {
                continue;
            }
            int worldX = bounds.originX() + block.x();
            int worldZ = bounds.originZ() + block.z();
            if ((worldX >> 4) != chunkX || (worldZ >> 4) != chunkZ) {
                continue;
            }
            Block target = world.getBlockAt(worldX, bounds.originY() + block.y(), worldZ);
            if (!tileData.isApplicable(target.getBlockData())) {
                throw new IOException("hydrated tile state no longer matches its block at "
                        + target.getX() + "," + target.getY() + "," + target.getZ());
            }
            KMap<String, Object> hydrated = BukkitPlatform.serializeTile(target.getLocation());
            if (hydrated == null
                    || tileData.getProperties() != null
                    && !nbtContains(tileData.getProperties(), hydrated)) {
                throw new IOException("the active NMS binding did not preserve source tile NBT at "
                        + target.getX() + "," + target.getY() + "," + target.getZ());
            }
        }
        if (!connectorsVisible) {
            return;
        }
        for (JigsawStudioGenerator.RenderedConnector renderedConnector : rendered.connectors()) {
            int worldX = bounds.originX() + renderedConnector.x();
            int worldZ = bounds.originZ() + renderedConnector.z();
            if ((worldX >> 4) != chunkX || (worldZ >> 4) != chunkZ) {
                continue;
            }
            Block target = world.getBlockAt(
                    worldX,
                    bounds.originY() + renderedConnector.y(),
                    worldZ);
            BlockData blockData = target.getBlockData();
            if (!(blockData instanceof Jigsaw jigsaw)
                    || !jigsaw.getOrientation().name().toLowerCase(Locale.ROOT)
                    .equals(renderedConnector.orientation())) {
                throw new IOException("jigsaw marker changed before hydration completed at "
                        + target.getX() + "," + target.getY() + "," + target.getZ());
            }
            KMap<String, Object> expected = markerNbt(renderedConnector.connector());
            KMap<String, Object> hydrated = BukkitPlatform.serializeTile(target.getLocation());
            if (!markerNbtMatches(expected, hydrated)) {
                throw new IOException("the active NMS binding did not preserve jigsaw marker NBT at "
                        + target.getX() + "," + target.getY() + "," + target.getZ());
            }
        }
    }

    static KMap<String, Object> markerNbt(IrisJigsawConnector connector) {
        IrisJigsawConnector activeConnector = Objects.requireNonNull(connector, "Jigsaw Studio connector");
        KMap<String, Object> nbt = new KMap<>();
        nbt.put("name", studioIdentifier(activeConnector.getName()));
        nbt.put("target", studioIdentifier(activeConnector.getTargetName()));
        nbt.put("pool", JigsawStudioMarkerKeyCodec.encodePool(activeConnector.getPool()));
        nbt.put("final_state", activeConnector.getFinalState());
        nbt.put("joint", activeConnector.getJoint().name().toLowerCase(Locale.ROOT));
        nbt.put("selection_priority", activeConnector.getSelectionPriority());
        nbt.put("placement_priority", activeConnector.getPlacementPriority());
        return nbt;
    }

    private static boolean markerNbtMatches(
            Map<String, Object> expected,
            Map<String, Object> hydrated
    ) {
        if (hydrated == null) {
            return false;
        }
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object actual = hydrated.get(entry.getKey());
            if (entry.getValue() instanceof Number expectedNumber) {
                if (!(actual instanceof Number actualNumber)
                        || expectedNumber.longValue() != actualNumber.longValue()) {
                    return false;
                }
            } else if (!Objects.equals(entry.getValue(), actual)) {
                return false;
            }
        }
        return true;
    }

    private static boolean nbtContains(Object expected, Object actual) {
        if (expected instanceof Number expectedNumber) {
            if (!(actual instanceof Number actualNumber)) {
                return false;
            }
            if (isIntegral(expectedNumber) && isIntegral(actualNumber)) {
                return expectedNumber.longValue() == actualNumber.longValue();
            }
            return Double.compare(expectedNumber.doubleValue(), actualNumber.doubleValue()) == 0;
        }
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)) {
                return false;
            }
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                if (!actualMap.containsKey(entry.getKey())
                        || !nbtContains(entry.getValue(), actualMap.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof List<?> expectedList) {
            if (!(actual instanceof List<?> actualList) || expectedList.size() != actualList.size()) {
                return false;
            }
            for (int index = 0; index < expectedList.size(); index++) {
                if (!nbtContains(expectedList.get(index), actualList.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(expected, actual);
    }

    private static boolean isIntegral(Number number) {
        return number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long;
    }

    static void restoreCapturedMetadata(
            IrisJigsawConnector captured,
            IrisJigsawPiece sourcePiece
    ) {
        IrisJigsawConnector activeCaptured = Objects.requireNonNull(captured, "Captured jigsaw connector");
        IrisJigsawConnector original = connectorAt(sourcePiece, activeCaptured.getPosition());
        if (original == null) {
            return;
        }
        activeCaptured.setName(restoreIdentifier(activeCaptured.getName(), original.getName()));
        activeCaptured.setTargetName(restoreIdentifier(
                activeCaptured.getTargetName(),
                original.getTargetName()));
        activeCaptured.setChannel(original.getChannel() == null ? "" : original.getChannel());
    }

    static void restoreCapturedMetadataForDisplay(
            IrisJigsawConnector captured,
            IrisJigsawPiece sourcePiece,
            JigsawStudioCellDimensions displayDimensions,
            int displayRotationQuarterTurns
    ) {
        if (Math.floorMod(displayRotationQuarterTurns, 4) == 0) {
            restoreCapturedMetadata(captured, sourcePiece);
            return;
        }
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        int sourceWidth = (quarterTurns & 1) == 0
                ? displayDimensions.width()
                : displayDimensions.depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? displayDimensions.depth()
                : displayDimensions.width();
        LocalPosition position = inversePosition(
                captured.getPosition().getX(),
                captured.getPosition().getY(),
                captured.getPosition().getZ(),
                sourceWidth,
                sourceDepth,
                quarterTurns);
        IrisObjectRotation inverseRotation = IrisObjectRotation.of(0, 90.0D * quarterTurns, 0);
        IrisJigsawConnector sourceOriented = CapturedConnector.from(captured).toConnector()
                .setPosition(new IrisPosition(position.x(), position.y(), position.z()))
                .setDirection(inverseRotation.rotate(captured.getDirection()))
                .setTop(inverseRotation.rotate(captured.getTop()));
        restoreCapturedMetadata(sourceOriented, sourcePiece);
        captured.setName(sourceOriented.getName());
        captured.setTargetName(sourceOriented.getTargetName());
        captured.setChannel(sourceOriented.getChannel());
    }

    private static IrisJigsawConnector connectorAt(IrisJigsawPiece piece, IrisPosition position) {
        if (piece == null || piece.getConnectors() == null || position == null) {
            return null;
        }
        for (IrisJigsawConnector connector : piece.getConnectors()) {
            if (connector != null && position.equals(connector.getPosition())) {
                return connector;
            }
        }
        return null;
    }

    private static String restoreIdentifier(String captured, String original) {
        if (original != null && studioIdentifier(original).equals(captured)) {
            return original;
        }
        return captured;
    }

    private static String studioIdentifier(String value) {
        String normalized = Objects.requireNonNull(value, "Jigsaw Studio marker identifier").trim();
        return normalized.indexOf(':') < 0 ? "iris:" + normalized : normalized;
    }

    static final class BayPopulation {
        private final Set<Long> requiredChunks;
        private final Set<Long> generatedChunks = ConcurrentHashMap.newKeySet();
        private final Set<Long> appliedChunks = ConcurrentHashMap.newKeySet();
        private final Set<Long> hydratedChunks = ConcurrentHashMap.newKeySet();
        private volatile String failure;

        BayPopulation(Set<Long> requiredChunks, String failure) {
            this.requiredChunks = Set.copyOf(requiredChunks);
            this.failure = failure == null ? "" : failure;
        }

        boolean markGenerated(long chunk) {
            if (!requiredChunks.contains(chunk)) {
                return false;
            }
            generatedChunks.add(chunk);
            return !hydratedChunks.contains(chunk) && failure.isEmpty();
        }

        boolean needsApplication(long chunk) {
            return failure.isEmpty()
                    && requiredChunks.contains(chunk)
                    && generatedChunks.contains(chunk)
                    && !appliedChunks.contains(chunk);
        }

        boolean needsVerification(long chunk) {
            return failure.isEmpty()
                    && requiredChunks.contains(chunk)
                    && generatedChunks.contains(chunk)
                    && appliedChunks.contains(chunk)
                    && !hydratedChunks.contains(chunk);
        }

        void markApplied(long chunk) {
            if (requiredChunks.contains(chunk)) {
                appliedChunks.add(chunk);
            }
        }

        void markHydrated(long chunk) {
            if (requiredChunks.contains(chunk)) {
                hydratedChunks.add(chunk);
            }
        }

        void markFullyReady() {
            generatedChunks.addAll(requiredChunks);
            appliedChunks.addAll(requiredChunks);
            hydratedChunks.addAll(requiredChunks);
        }

        synchronized boolean fail(String reason) {
            if (!failure.isEmpty()) {
                return false;
            }
            failure = reason == null || reason.isBlank() ? "unknown hydration failure" : reason;
            return true;
        }

        BayReadiness readiness() {
            boolean ready = failure.isEmpty()
                    && !requiredChunks.isEmpty()
                    && generatedChunks.containsAll(requiredChunks)
                    && hydratedChunks.containsAll(requiredChunks);
            return new BayReadiness(
                    ready,
                    failure,
                    requiredChunks.size(),
                    generatedChunks.size(),
                    hydratedChunks.size());
        }
    }
}
