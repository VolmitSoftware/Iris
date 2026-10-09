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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.config.Feature;
import com.volmit.host.LoadedPack;
import com.volmit.placement.PlacedBlock;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Module settings that object placements declare in pack JSON, decoded and activating placement stages. */
public class ObjectPlacementModuleSettingsTest {
    private static final int SURFACE_Y = 80;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private IrisData data;
    private LoadedPack pack;
    private Gson gson;

    @Feature(id = "example:glow", name = "Glow", key = "glow", targets = Target.OBJECT_PLACEMENT)
    public static final class GlowSettings implements IrisFeature {
        private String block = "minecraft:glowstone";
    }

    /** Puts the configured block on the object once it is written, where placements declare glow. */
    public static final class GlowModule implements IrisModule {
        private static final PlacementStage<Void> TOP = Stage.placement("example:glow/top");

        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("example:glow", "Glow", "1");
        }

        @Override
        public void install(StageGraph graph) {
            graph.define(TOP).when(GlowSettings.class).requires(IrisStages.Blocks.COMPLETE, IrisStages.Anchor.FINAL).run((context, glow) -> {
                int y = context.output(IrisStages.Anchor.FINAL).y() + 1;
                context.access().set(new PlacedBlock(new BlockPosition(0, y, 0), com.volmit.block.BlockState.parse(glow.getFirst().value().block), Optional.empty()));
            });
        }
    }

    /** Holds placements the way a biome does. */
    private static final class Holder {
        private List<IrisObjectPlacement> objects;
    }

    @Before
    public void loadPack() throws IOException {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        Map<String, PlatformBlockState> states = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(call -> states.computeIfAbsent(
                PlatformBlockStates.namespaced(call.<String>getArgument(0).toLowerCase()), ObjectPlacementModuleSettingsTest::state));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        data = mock(IrisData.class);
        pack = BuiltInPack.serve(data, folder, List.of(new GlowModule()));
        ModuleSettingsDecoder decoder = new ModuleSettingsDecoder(pack.catalog(), folder.getRoot().toPath(), Gson::new);
        gson = new GsonBuilder().registerTypeAdapterFactory(new ObjectPlacementSettingsReader(() -> decoder)).create();
    }

    @After
    public void closePack() {
        pack.close();
        IrisPlatforms.unbind();
    }

    @Test
    public void decodesTheSettingsOfEveryNestedPlacement() {
        Holder holder = gson.fromJson("{\"objects\": [{\"place\": [\"tree\"], \"glow\": {\"block\": \"minecraft:sea_lantern\"}},"
                + " {\"place\": [\"rock\"]}]}", Holder.class);

        assertEquals("minecraft:sea_lantern", holder.objects.get(0).getModuleSettings().find(GlowSettings.class).orElseThrow().block);
        assertSame(ModuleSettings.NONE, holder.objects.get(1).getModuleSettings());
    }

    @Test
    public void copiesOfAPlacementKeepItsSettings() {
        IrisObjectPlacement placement = gson.fromJson("{\"place\": [\"tree\"], \"glow\": {}}", IrisObjectPlacement.class);

        assertSame(placement.getModuleSettings(), placement.toPlacement("rock").getModuleSettings());
    }

    @Test
    public void aPlacementsSettingsActivateItsStages() {
        IObjectPlacer declaring = placer();
        IObjectPlacer silent = placer();

        place(gson.fromJson("{\"glow\": {}}", IrisObjectPlacement.class), declaring);
        place(gson.fromJson("{}", IrisObjectPlacement.class), silent);

        verify(declaring).set(eq(0), eq(SURFACE_Y + 1), eq(0), any());
        verify(silent, never()).set(eq(0), eq(SURFACE_Y + 1), eq(0), any());
    }

    private void place(IrisObjectPlacement placement, IObjectPlacer placer) {
        placement.setMode(ObjectPlaceMode.CENTER_HEIGHT);
        placement.setRequireSurfaceSupport(false);
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, IrisPlatforms.get().registries().block("minecraft:stone"));
        ModuleObjectPlacement.place(object, 0, SURFACE_Y, 0, placer, placement, new RNG(1), null, null, data);
    }

    private static IObjectPlacer placer() {
        IObjectPlacer placer = mock(IObjectPlacer.class);
        when(placer.getHighest(anyInt(), anyInt(), any(), anyBoolean())).thenReturn(SURFACE_Y);
        return placer;
    }

    private static PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isSolid()).thenReturn(!key.contains("air"));
        when(state.isOccluding()).thenReturn(!key.contains("air"));
        return state;
    }
}
