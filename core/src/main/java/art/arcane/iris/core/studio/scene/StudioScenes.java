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
import com.volmit.host.SceneViewRegistration;
import com.volmit.object.GeneratedObject;
import com.volmit.tooling.SceneEntry;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** The studio scene views the modules of an engine's pack registered, asked for that engine's world. */
public final class StudioScenes {
    private final List<SceneViewRegistration> registrations;
    private final PackWorld world;

    private StudioScenes(List<SceneViewRegistration> registrations, PackWorld world) {
        this.registrations = registrations;
        this.world = world;
    }

    public static StudioScenes of(Engine engine) {
        return new StudioScenes(engine.getData().getModules().pack().catalog().sceneViews(), engine.getGeneration().world());
    }

    public List<SceneViewInfo> views() {
        return registrations.stream()
                .map(registration -> new SceneViewInfo(registration.view().id().toString(), registration.view().label(),
                        registration.view().description()))
                .toList();
    }

    /** @throws NoSuchElementException when no module registered a view {@code viewId} */
    public List<SceneEntry> entries(String viewId) {
        SceneViewRegistration registration = registration(viewId);
        try {
            return registration.view().entries(world);
        } catch (RuntimeException failure) {
            throw new IllegalStateException(failureOf(registration, "list its entries"), failure);
        }
    }

    /**
     * The blocks of one variant of an entry; empty when the view has no such entry or variant.
     *
     * @throws NoSuchElementException when no module registered a view {@code viewId}
     */
    public Optional<GeneratedObject> scene(String viewId, String key, int variant) {
        SceneViewRegistration registration = registration(viewId);
        try {
            return registration.view().scene(world, key, variant);
        } catch (RuntimeException failure) {
            throw new IllegalStateException(failureOf(registration, "build " + key), failure);
        }
    }

    private SceneViewRegistration registration(String viewId) {
        return registrations.stream()
                .filter(registration -> registration.view().id().toString().equals(viewId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No module shows a scene view " + viewId));
    }

    private static String failureOf(SceneViewRegistration registration, String task) {
        return "The " + registration + " could not " + task;
    }

    /** A scene view as the studio lists it. */
    public record SceneViewInfo(String id, String label, String description) {
    }
}
