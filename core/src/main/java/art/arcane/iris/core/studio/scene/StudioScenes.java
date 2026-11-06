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

package art.arcane.iris.core.studio.scene;

import art.arcane.iris.engine.framework.Engine;
import com.volmit.object.GeneratedObject;
import com.volmit.tooling.SceneEntry;
import com.volmit.tooling.SceneView;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** The studio scene views the modules of an engine's pack registered, asked for that engine's world. */
public final class StudioScenes {
    private final ModuleViews<SceneView> views;

    private StudioScenes(ModuleViews<SceneView> views) {
        this.views = views;
    }

    public static StudioScenes of(Engine engine) {
        return new StudioScenes(ModuleViews.of(engine, SceneView.class, "scene view"));
    }

    public List<ModuleViewInfo> views() {
        return views.infos();
    }

    /** @throws NoSuchElementException when no module registered a scene view {@code viewId} */
    public List<SceneEntry> entries(String viewId) {
        return views.ask(viewId, "list its entries", view -> view.entries(views.world()));
    }

    /**
     * The blocks of one variant of an entry; empty when the view has no such entry or variant.
     *
     * @throws NoSuchElementException when no module registered a scene view {@code viewId}
     */
    public Optional<GeneratedObject> scene(String viewId, String key, int variant) {
        return views.ask(viewId, "build " + key, view -> view.scene(views.world(), key, variant));
    }
}
