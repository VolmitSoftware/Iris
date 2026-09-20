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

import art.arcane.iris.studio.view.GuiHost;
import art.arcane.volmlib.nativelib.view.WorldMarker;
import art.arcane.iris.studio.view.GuiOverlay;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.modded.ModdedIrisLog;

import java.awt.Desktop;
import java.io.File;
import java.util.List;
import java.util.Optional;
import art.arcane.volmlib.nativelib.view.WorldView;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ModdedVisionOverlay implements GuiOverlay {
    private final WorldView world;
    private final Engine engine;
    private final UUID opener;

    public ModdedVisionOverlay(Context context) {
        world = context.world();
        engine = context.engine();
        opener = context.opener();
    }

    @Override
    public List<WorldMarker> players() {
        return world.players();
    }

    @Override
    public void requestEntities(Consumer<List<WorldMarker>> sink) {
        world.requestEntities(sink);
    }

    @Override
    public void teleport(double worldX, double worldZ) {
        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);
        world.execute(() -> {
            int surfaceY = engine.getMinHeight() + engine.getHeight(blockX, blockZ, false) + 2;
            Optional<WorldView.TeleportOperation> operation = world.teleport(opener,
                    new WorldView.Destination(blockX + 0.5D, surfaceY, blockZ + 0.5D,
                            System.nanoTime() + TimeUnit.SECONDS.toNanos(10L)));
            if (operation.isEmpty()) {
                return;
            }
            WorldView.TeleportOperation teleport = operation.get();
            teleport.result().whenComplete((success, failure) -> {
                if (failure != null) {
                    ModdedIrisLog.error("Iris Vision teleport failed for {} at {},{}",
                            teleport.playerId(), blockX, blockZ, failure);
                } else if (!Boolean.TRUE.equals(success)) {
                    ModdedIrisLog.warn("Iris Vision teleport did not complete for {} at {},{}",
                            teleport.playerId(), blockX, blockZ);
                }
            });
        });
    }

    @Override
    public String openInEditor(double worldX, double worldZ, RenderType type) {
        if (!GuiHost.isAvailable() || !Desktop.isDesktopSupported()) {
            return null;
        }
        IrisComplex complex = engine.getComplex();
        File file = switch (type) {
            case BIOME, LAYER_LOAD, DECORATOR_LOAD, OBJECT_LOAD, HEIGHT, RIVER ->
                    complex.getTrueBiomeStream().get(worldX, worldZ).openInVSCode();
            case BIOME_LAND -> complex.getLandBiomeStream().get(worldX, worldZ).openInVSCode();
            case BIOME_SEA -> complex.getSeaBiomeStream().get(worldX, worldZ).openInVSCode();
            case REGION -> complex.getRegionStream().get(worldX, worldZ).openInVSCode();
            case CAVE_LAND -> complex.getCaveBiomeStream().get(worldX, worldZ).openInVSCode();
            default -> null;
        };
        return file == null ? null : file.getName();
    }
    public record Context(WorldView world, Engine engine, UUID opener) {
    }

}
