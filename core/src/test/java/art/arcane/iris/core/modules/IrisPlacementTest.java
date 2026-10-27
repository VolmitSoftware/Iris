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
import art.arcane.iris.builtin.placement.CaveSpace;
import art.arcane.iris.builtin.placement.MarkerRule;
import art.arcane.iris.builtin.prepare.AxisRotationClamp;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.builtin.spawn.PlacementMarker;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.placement.IrisPlacementForm;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.BlockStateRotation;
import art.arcane.iris.engine.object.terrain.carving.CarveResult;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An Iris object placement as the built-in placement stages see it through the {@code ObjectPlacement} facade. */
public class IrisPlacementTest {
    private static final int X = 12;
    private static final int Y = 70;
    private static final int Z = -4;

    private IrisObject object;
    private ObjectPlacement.Builder config;
    private IrisData data;
    private IrisPlacement placement;
    private PlatformBlockStates states;
    private BlockStateRotation.StateRotator previousRotator;

    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
        config = ObjectPlacement.builder();
        data = mock(IrisData.class);
        when(data.getPackResources()).thenReturn(mock(IrisPackResources.class));
        IrisObject object = new IrisObject(3, 4, 5);
        object.setLoader(data);
        this.object = object;
        states = new PlatformBlockStates(UsableBlockPlatform::block);
        placement = placementOf(config);
    }

    /** The placement settings are read when the placement starts, so a test configures first. */
    private IrisPlacement placementOf(ObjectPlacement.Builder configured) {
        ObjectPlacement settings = configured.build();
        return new IrisPlacement(object, settings, IrisPlacementForm.of(settings, data), mock(IObjectPlacer.class), new CarveResult(70, 90), X, Y, Z, data, states,
                mock(ObjectPlacerEdit.class));
    }

    @After
    public void unbindPlatform() {
        if (previousRotator != null) {
            BlockStateRotation.restorePlatformRotator(previousRotator);
        }
        UsableBlockPlatform.unbind();
    }

    @Test
    public void thePositionAndTheObjectsSizeAreTheOnesItWasAskedFor() {
        assertEquals(X, placement.x());
        assertEquals(Y, placement.requestedY());
        assertEquals(Z, placement.z());
        assertEquals(3, placement.object().width());
        assertEquals(4, placement.object().height());
        assertEquals(5, placement.object().depth());
        assertEquals(new CaveSpace(70, 20), placement.cave().orElseThrow());
    }

    @Test
    public void settingsReadTheIrisPlacementAsItIs() {
        config.mode(ObjectPlaceMode.VACUUM).snow(0.5).bore(true).boreExtendMaxY(3);
        config.translate(ObjectTranslate.of(1, 2, 3, 4));
        config.stiltSettings(null);
        placement = placementOf(config);

        assertEquals(ObjectPlaceMode.VACUUM, placement.settings().mode());
        assertEquals(0.5, placement.settings().snow(), 0);
        assertTrue(placement.settings().bore());
        assertEquals(3, placement.settings().boreExtendMaxY());
        ObjectTranslate translate = placement.settings().translate();
        assertEquals(List.of(1, 2, 3, 4), List.of(translate.x(), translate.y(), translate.yRandom(), translate.z()));
        assertTrue(placement.settings().stilt().isEmpty());
        assertTrue(placement.settings().edit().isEmpty());
    }

    @Test
    public void settingsHandOutThePlacementsOwnRotation() {
        ObjectRotation model = ObjectRotation.of(0, 90, 0);
        config.rotation(model);
        placement = placementOf(config);

        ObjectRotation rotation = placement.settings().rotation();

        assertSame(model, rotation);
        assertTrue(rotation.canRotateY());
        assertFalse(rotation.canRotateX());
    }

    @Test
    public void aMarkerRuleHasTheStatesOfItsTargetsAsApiStates() {
        BlockSpec planks = BlockSpec.of("minecraft:oak_planks");
        PlacementMarker marker = mock(PlacementMarker.class);
        when(marker.marker()).thenReturn("spawn/zombie");
        when(marker.maximumMarkers()).thenReturn(5);
        when(marker.exact()).thenReturn(true);
        when(marker.mark()).thenReturn(List.of(planks));
        IrisPackResources resources = mock(IrisPackResources.class);
        BlockRegistry blocks = mock(BlockRegistry.class);
        when(data.getPackResources()).thenReturn(resources);
        when(resources.blocks()).thenReturn(blocks);
        when(blocks.resolve(planks)).thenReturn(BlockState.parse("minecraft:oak_planks"));
        config.markers(List.of(marker));
        placement = placementOf(config);

        MarkerRule rule = placement.settings().markers().getFirst();

        assertEquals("spawn/zombie", rule.marker());
        assertEquals(5, rule.maximum());
        assertTrue(rule.exact());
        assertEquals(List.of(BlockState.parse("minecraft:oak_planks")), rule.targets());
    }

    @Test
    public void aFlatWarpGivesNoSurfaceWarp() {
        assertTrue(placement.surfaceWarp(new RNG(1)).isEmpty());
    }

    @Test
    public void statesAreTurnedWithTheRotationTheStagesChose() {
        List<ObjectRotation> turnedWith = new ArrayList<>();
        previousRotator = BlockStateRotation.bindPlatformRotator((rotation, state, x, y, z) -> {
            turnedWith.add(rotation);
            return state;
        });
        BlockState stairs = BlockState.parse("minecraft:oak_stairs");

        placement.rotate(stairs, placement.settings().rotation(), 1, 2, 3);
        ObjectRotation derived = ObjectRotation.of(true, new AxisRotationClamp(), AxisRotationClamp.of(true, false, 90, 90, 90),
                new AxisRotationClamp());
        placement.rotate(stairs, derived, 1, 2, 3);
        placement.rotate(stairs, derived, 1, 2, 3);

        assertSame(placement.settings().rotation(), turnedWith.get(0));
        assertSame(derived, turnedWith.get(1));
        assertSame(derived, turnedWith.get(2));
    }

    @Test
    public void statesKeepTheirPlatformIdentityThroughTraitsAndRotation() {
        PlatformBlockState custom = mock(PlatformBlockState.class);
        when(custom.key()).thenReturn("minecraft:air");
        when(custom.isCustom()).thenReturn(true);
        BlockState api = states.toApi(custom);

        assertTrue(placement.hasTrait(api, com.volmit.block.BlockTrait.CUSTOM));
        assertFalse(placement.hasTrait(BlockState.parse("minecraft:air"), com.volmit.block.BlockTrait.CUSTOM));
    }

    @Test
    public void theAnchorIsKnownOnceTheStagesReportedIt() {
        placement.anchoredAt(64);

        assertEquals(64, placement.anchorY());
    }
}
