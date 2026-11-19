/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.map;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.studio.map.MapHoverProbe.Details;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** What the Iris Studio app's hover card shows about one spot, from the same probe the Swing map's card uses. */
public final class StudioHover {
    private static final int RGB = 0xFFFFFF;

    private StudioHover() {
    }

    /** @param previewRegionKey the region the region view previews everywhere, or null for the world as it is */
    public static Hover sample(Engine engine, double worldX, double worldZ, String previewRegionKey, boolean explainsBiome) {
        Details details = MapHoverProbe.sample(engine, worldX, worldZ, previewRegionKey, explainsBiome);
        return new Hover(
                details.worldX(),
                details.worldZ(),
                label(engine, details.surfaceBiome()),
                label(engine, details.caveBiome()),
                label(engine, details.region()),
                details.vanillaBiome(),
                details.height(),
                details.fluidHeight(),
                details.slopeDegrees(),
                packPath(engine, details.surfaceBiome()),
                packPath(engine, details.region()),
                steps(details.pickPath())
        );
    }

    /** The regions the world picks biomes for, which the region view can preview. */
    public static List<Label> previewableRegions(Engine engine) {
        return engine.getComplex().noisePickedRegions().stream().map(region -> label(engine, region)).toList();
    }

    private static Label label(Engine engine, IrisBiome biome) {
        if (biome == null) {
            return null;
        }
        NoisePickSpread.Option option = PickMeasuring.biomeOption(engine, biome);
        return new Label("BIOME", option.key(), option.name(), option.color() & RGB);
    }

    private static Label label(Engine engine, IrisRegion region) {
        if (region == null) {
            return null;
        }
        NoisePickSpread.Option option = PickMeasuring.regionOption(engine, region);
        return new Label("REGION", option.key(), option.name(), option.color() & RGB);
    }

    private static Label label(String kind, NoisePickSpread.Option option) {
        return new Label(kind, option.key(), option.name(), option.color() & RGB);
    }

    private static List<Step> steps(List<MapHoverProbe.PickStep> path) {
        List<Step> steps = new ArrayList<>();
        String previous = null;
        for (MapHoverProbe.PickStep step : path) {
            boolean regionStep = step.kind() == MapHoverProbe.StepKind.REGION_NOISE || step.kind() == MapHoverProbe.StepKind.PREVIEWED_REGION;
            String kind = regionStep ? "REGION" : "BIOME";
            List<Band> bands = step.bands().stream()
                    .map(band -> new Band(label(kind, band.option()), band.fromNoise(), band.toNoise()))
                    .toList();
            steps.add(new Step(wireKind(step.kind()), label(kind, step.picked()), step.detail(),
                    step.kind() == MapHoverProbe.StepKind.CHILD ? previous : null,
                    Double.isNaN(step.noise()) ? null : step.noise(), bands));
            previous = step.picked().name();
        }
        return steps;
    }

    private static String wireKind(MapHoverProbe.StepKind kind) {
        return switch (kind) {
            case REGION_NOISE -> "REGION";
            case PREVIEWED_REGION -> "PREVIEW";
            case BIOME_NOISE -> "BIOME";
            case CHILD -> "CHILD";
        };
    }

    private static String packPath(Engine engine, IrisRegistrant registrant) {
        if (registrant == null || registrant.getLoadFile() == null || engine.getData() == null) {
            return null;
        }
        Path pack = engine.getData().getDataFolder().toPath().toAbsolutePath();
        Path file = registrant.getLoadFile().toPath().toAbsolutePath();
        return file.startsWith(pack) ? pack.relativize(file).toString().replace('\\', '/') : null;
    }

    public record Label(String kind, String key, String name, int color) {
    }

    public record Band(Label option, double from, double to) {
    }

    public record Step(String kind, Label picked, String list, String replaced, Double noise, List<Band> bands) {
    }

    public record Hover(double worldX, double worldZ, Label biome, Label caveBiome, Label region, String vanillaBiome,
                        double height, double fluidHeight, double slopeDegrees, String biomeFile, String regionFile,
                        List<Step> pickPath) {
    }
}
