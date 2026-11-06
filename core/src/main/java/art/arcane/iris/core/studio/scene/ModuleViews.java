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
import com.volmit.host.StudioViewRegistration;
import com.volmit.tooling.StudioView;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

/** The studio views of one kind the modules of an engine's pack registered, asked for that engine's world. */
final class ModuleViews<V extends StudioView> {
    private final List<StudioViewRegistration> registrations;
    private final Class<V> kind;
    private final String kindName;
    private final PackWorld world;

    private ModuleViews(List<StudioViewRegistration> registrations, Class<V> kind, String kindName, PackWorld world) {
        this.registrations = registrations;
        this.kind = kind;
        this.kindName = kindName;
        this.world = world;
    }

    static <V extends StudioView> ModuleViews<V> of(Engine engine, Class<V> kind, String kindName) {
        List<StudioViewRegistration> ofKind = engine.getData().getModules().pack().catalog().studioViews().stream()
                .filter(registration -> kind.isInstance(registration.view()))
                .toList();
        return new ModuleViews<>(ofKind, kind, kindName, engine.getGeneration().world());
    }

    List<ModuleViewInfo> infos() {
        return registrations.stream()
                .map(registration -> new ModuleViewInfo(registration.view().id().toString(), registration.view().label(), registration.view().description()))
                .toList();
    }

    /**
     * What {@code ask} gives for the view {@code viewId}; a failure inside the module names the module and the task.
     *
     * @throws NoSuchElementException when no module registered a view {@code viewId} of this kind
     */
    <T> T ask(String viewId, String task, Function<V, T> ask) {
        StudioViewRegistration registration = registrations.stream()
                .filter(candidate -> candidate.view().id().toString().equals(viewId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No module shows a " + kindName + " " + viewId));
        try {
            return ask.apply(kind.cast(registration.view()));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("The " + registration + " could not " + task, failure);
        }
    }

    PackWorld world() {
        return world;
    }
}
