/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.modules.floating.FloatingChildBiome;
import art.arcane.iris.modules.floating.FloatingChildSettings;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.geometry.BlockBox;
import com.volmit.object.PackObjects;
import com.volmit.resource.PackResources;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.host.ModuleLogs;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FloatingObjectPlacingBoundaryRadiusTest {
    private static final ResourceId BIOME = ResourceId.parse("test:biome/floating");

    @Test
    public void transformedFloatingPlacementExpandsOwnerRadius() {
        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class, "{\"place\":[\"test/floating\"],\"translate\":{\"x\":32}}");
        FloatingChildBiome floatingChild = TestSettings.with(new FloatingChildBiome(), "floatingObjects", List.of(placement));
        PackResources resources = mock(PackResources.class);
        PackObjects objects = mock(PackObjects.class);
        ResourceId floating = ResourceId.parse("test:test/floating");
        when(resources.objects()).thenReturn(objects);
        when(resources.idOf("test/floating")).thenReturn(floating);
        when(objects.sizeOf(floating)).thenReturn(Optional.of(new BlockBox(0, 0, 0, 1, 1, 1)));

        assertEquals(33, FloatingObjectPlacing.reach(pack(new FloatingChildSettings(List.of(floatingChild)), resources), ModuleLogs.discarding()));
    }

    private static PackSettings pack(FloatingChildSettings declared, PackResources resources) {
        return new PackSettings() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends IrisFeature> SettingsStack<T> declared(Class<T> featureType) {
                return featureType == FloatingChildSettings.class ? SettingsStack.of(List.of(new Entry<>(Level.BIOME, BIOME, (T) declared)))
                        : SettingsStack.empty();
            }

            @Override
            public <T> Optional<T> extension(Class<T> type) {
                return Optional.empty();
            }

            @Override
            public PackResources resources() {
                return resources;
            }
        };
    }
}
