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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.modules.carve.CaveProfile;
import art.arcane.iris.builtin.objects.ObjectPlacement;

/** What a cave object placement takes from the cave profile that applies to it. */
final class CaveSettings {
    private static final CaveProfile DEFAULT_PROFILE = new CaveProfile();
    private static final int DEFAULT_SCAN_STEP = 1;
    private static final int DEFAULT_MIN_DEPTH_BELOW_SURFACE = 6;
    private static final int DEFAULT_SEARCH_ATTEMPTS = 6;

    private final CaveProfile dimensionProfile;

    CaveSettings(CaveProfile dimensionProfile) {
        this.dimensionProfile = dimensionProfile;
    }

    /** The profile of the first of {@code preferred} and {@code secondary} that is enabled, else the dimension's, else the default one. */
    CaveProfile resolve(CaveProfile preferred, CaveProfile secondary) {
        if (preferred != null && preferred.enabled()) {
            return preferred;
        }
        if (secondary != null && secondary.enabled()) {
            return secondary;
        }
        if (dimensionProfile != null) {
            return dimensionProfile;
        }
        return DEFAULT_PROFILE;
    }

    /** How the placement looks for its anchor in caves: its own mode, else the profile's, else the floor; turned upside down, the ceiling. */
    CaveAnchors.Search searchFor(ObjectPlacement placement, CaveProfile profile) {
        CaveAnchorMode mode = anchorMode(placement, profile);
        if (placement.mode().placeMode().has(PlaceTrait.UPSIDE_DOWN)) {
            mode = CaveAnchorMode.CEILING;
        }
        return new CaveAnchors.Search(mode, scanStep(profile), minDepthBelowSurface(profile), searchAttempts(profile));
    }

    private static CaveAnchorMode anchorMode(ObjectPlacement placement, CaveProfile profile) {
        CaveAnchorMode placementMode = placement.caveAnchorMode();
        if (placementMode != null && !placementMode.equals(CaveAnchorMode.PROFILE_DEFAULT)) {
            return placementMode;
        }
        if (profile == null) {
            return CaveAnchorMode.FLOOR;
        }
        CaveAnchorMode profileMode = profile.defaultObjectAnchor();
        if (profileMode == null || profileMode.equals(CaveAnchorMode.PROFILE_DEFAULT)) {
            return CaveAnchorMode.FLOOR;
        }
        return profileMode;
    }

    private static int scanStep(CaveProfile profile) {
        return profile == null ? DEFAULT_SCAN_STEP : Math.max(1, profile.anchorScanStep());
    }

    private static int minDepthBelowSurface(CaveProfile profile) {
        return profile == null ? DEFAULT_MIN_DEPTH_BELOW_SURFACE : Math.max(0, profile.objectMinDepthBelowSurface());
    }

    private static int searchAttempts(CaveProfile profile) {
        return profile == null ? DEFAULT_SEARCH_ATTEMPTS : Math.max(1, profile.anchorSearchAttempts());
    }
}
