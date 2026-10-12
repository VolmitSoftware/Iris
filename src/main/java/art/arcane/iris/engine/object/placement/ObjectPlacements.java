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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.RequireSurfaceSupportSettings;
import art.arcane.iris.builtin.objects.SurfaceSupportBufferSettings;
import art.arcane.iris.builtin.objects.TreeGrowthSettings;
import art.arcane.iris.builtin.objects.UpperObjectsForceSettings;
import art.arcane.iris.builtin.objects.UpperObjectsSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.procedural.ResolvedProceduralObjects;
import art.arcane.volmlib.util.collection.KList;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The object placements and procedural objects a region or biome declares, as Iris places them: the module settings of
 * {@code iris:objects}, made into Iris's own placements once and kept for as long as the settings live.
 */
public final class ObjectPlacements {
    private static final Map<ObjectSettings, Placements> PLACEMENTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ProceduralObjectSettings, ResolvedProceduralObjects> PROCEDURAL = Collections.synchronizedMap(new WeakHashMap<>());

    private ObjectPlacements() {
    }

    /** Every placement {@code owner} declares. */
    public static KList<IrisObjectPlacement> all(IrisRegistrant owner) {
        return placementsOf(owner).all;
    }

    /** The placements of {@code owner} that place on the surface. */
    public static KList<IrisObjectPlacement> surface(IrisRegistrant owner) {
        return placementsOf(owner).surface;
    }

    /** The placements of {@code owner} that place in carvings. */
    public static KList<IrisObjectPlacement> carving(IrisRegistrant owner) {
        return placementsOf(owner).carving;
    }

    /** The procedural objects {@code owner} declares; none when it declares none. */
    public static ResolvedProceduralObjects procedural(IrisRegistrant owner) {
        Optional<ProceduralObjectSettings> settings = owner.getModuleSettings().find(ProceduralObjectSettings.class);
        return procedural(settings.orElse(NO_PROCEDURAL_OBJECTS));
    }

    /** The procedural objects {@code settings} declare. */
    public static ResolvedProceduralObjects procedural(ProceduralObjectSettings settings) {
        return PROCEDURAL.computeIfAbsent(settings, ResolvedProceduralObjects::new);
    }

    /** Whether {@code dimension} places its objects in the terrain stacked above it too. */
    public static boolean upperObjects(IrisRegistrant dimension) {
        return dimension.getModuleSettings().find(UpperObjectsSettings.class).map(UpperObjectsSettings::enabled).orElse(false);
    }

    /** Whether the objects placed in the terrain stacked above ignore placement restrictions. */
    public static boolean upperObjectsForced(IrisRegistrant dimension) {
        return dimension.getModuleSettings().find(UpperObjectsForceSettings.class).map(UpperObjectsForceSettings::force).orElse(false);
    }

    /** Whether {@code dimension} refuses surface objects over carved openings. */
    public static boolean requiresSurfaceSupport(IrisRegistrant dimension) {
        return dimension.getModuleSettings().find(RequireSurfaceSupportSettings.class).map(RequireSurfaceSupportSettings::require)
                .orElse(NO_SURFACE_SUPPORT_REQUIREMENT.require());
    }

    /** The least surface-support buffer {@code dimension} gives its surface objects. */
    public static int surfaceSupportBuffer(IrisRegistrant dimension) {
        return dimension.getModuleSettings().find(SurfaceSupportBufferSettings.class).map(SurfaceSupportBufferSettings::buffer)
                .orElse(NO_SURFACE_SUPPORT_BUFFER.buffer());
    }

    /** The tree growth settings of {@code dimension}. */
    public static TreeGrowthSettings treeGrowth(IrisRegistrant dimension) {
        return dimension.getModuleSettings().find(TreeGrowthSettings.class).orElse(NO_TREE_GROWTH);
    }

    /** Iris's placement for the settings {@code placement}, read from the JSON it was written as. */
    public static IrisObjectPlacement host(ObjectPlacement placement, IrisData data) {
        Object kept = placement.hostForm();
        if (kept instanceof IrisObjectPlacement hostPlacement) {
            return hostPlacement;
        }
        IrisObjectPlacement made = data.getGson().fromJson(placement.json(), IrisObjectPlacement.class);
        placement.hostForm(made);
        return made;
    }

    private static final ProceduralObjectSettings NO_PROCEDURAL_OBJECTS = new ProceduralObjectSettings();
    private static final RequireSurfaceSupportSettings NO_SURFACE_SUPPORT_REQUIREMENT = new RequireSurfaceSupportSettings();
    private static final SurfaceSupportBufferSettings NO_SURFACE_SUPPORT_BUFFER = new SurfaceSupportBufferSettings();
    private static final TreeGrowthSettings NO_TREE_GROWTH = new TreeGrowthSettings();
    private static final ObjectSettings NO_OBJECTS = new ObjectSettings();

    private static Placements placementsOf(IrisRegistrant owner) {
        ObjectSettings settings = owner.getModuleSettings().find(ObjectSettings.class).orElse(NO_OBJECTS);
        Placements found = PLACEMENTS.get(settings);
        if (found != null) {
            return found;
        }
        return PLACEMENTS.computeIfAbsent(settings, declared -> new Placements(declared, owner.getLoader()));
    }

    private record Placements(KList<IrisObjectPlacement> all, KList<IrisObjectPlacement> surface, KList<IrisObjectPlacement> carving) {
        Placements(ObjectSettings settings, IrisData data) {
            this(new KList<>(), new KList<>(), new KList<>());
            for (ObjectPlacement declared : settings.objects()) {
                IrisObjectPlacement placement = host(declared, data);
                all.add(placement);
                if (placement.getCarvingSupport().supportsSurface()) {
                    surface.add(placement);
                }
                if (placement.getCarvingSupport().supportsCarving()) {
                    carving.add(placement);
                }
            }
        }
    }
}
