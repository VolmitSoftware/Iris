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

package art.arcane.iris.studio.scene;

import art.arcane.iris.generation.decoration.formation.FormationProfileSceneView;
import art.arcane.iris.generation.decoration.tree.TreeProfileSceneView;
import art.arcane.iris.generation.hydrology.config.FluidProfileSceneView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/** The scene views Iris's features have, in the order the studio lists them. A feature with a view adds it here. */
public final class SceneViews {
    private static final List<SceneView> VIEWS = distinct(List.of(
            new TreeProfileSceneView(),
            new FormationProfileSceneView(),
            new FluidProfileSceneView()));

    private SceneViews() {
    }

    public static List<SceneView> all() {
        return VIEWS;
    }

    public static SceneView byId(String id) {
        return VIEWS.stream().filter(view -> view.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No scene view " + id));
    }

    /** Every feature's view followed by the studio's own, such as its views of the pack's object files. */
    public static List<SceneView> withStudioViews(List<SceneView> studioViews) {
        List<SceneView> views = new ArrayList<>(VIEWS);
        views.addAll(studioViews);
        return List.copyOf(distinct(views));
    }

    static List<SceneView> distinct(List<SceneView> views) {
        Set<String> ids = new HashSet<>();
        for (SceneView view : views) {
            if (!ids.add(view.id())) {
                throw new IllegalStateException("Two scene views are called " + view.id());
            }
        }
        return views;
    }
}
