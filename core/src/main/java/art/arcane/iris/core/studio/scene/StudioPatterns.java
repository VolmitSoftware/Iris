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
import com.volmit.tooling.BlockPattern;
import com.volmit.tooling.PatternArea;
import com.volmit.tooling.PatternEntry;
import com.volmit.tooling.PatternView;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** The studio pattern views the modules of an engine's pack registered, asked for that engine's world. */
public final class StudioPatterns {
    private final ModuleViews<PatternView> views;

    private StudioPatterns(ModuleViews<PatternView> views) {
        this.views = views;
    }

    public static StudioPatterns of(Engine engine) {
        return new StudioPatterns(ModuleViews.of(engine, PatternView.class, "pattern view"));
    }

    public List<ModuleViewInfo> views() {
        return views.infos();
    }

    /** @throws NoSuchElementException when no module registered a pattern view {@code viewId} */
    public List<PatternEntry> entries(String viewId) {
        return views.ask(viewId, "list its entries", view -> view.entries(views.world()));
    }

    /**
     * What an entry places at a depth across an area; empty when the view has no such entry.
     *
     * @throws NoSuchElementException when no module registered a pattern view {@code viewId}
     */
    public Optional<BlockPattern> pattern(String viewId, String key, int depth, PatternArea area) {
        return views.ask(viewId, "lay out " + key, view -> view.pattern(views.world(), key, depth, area));
    }
}
