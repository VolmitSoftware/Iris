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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.modules.post.PostProcessingSettings;
import art.arcane.iris.modules.post.PostProcessingSlabsSettings;
import art.arcane.iris.modules.post.PostProcessingWallsSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;

/** Module {@code iris:post}: smooths the carved surface with {@link SurfaceSmoothing}. */
public final class PostModule implements IrisModule {
    private static final String VERSION = "1.0.0";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:post", "Post-processing", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var registry = setup.registry();
        registry.registerSettings(PostProcessingSettings.class);
        registry.registerSettings(PostProcessingSlabsSettings.class);
        registry.registerSettings(PostProcessingWallsSettings.class);
        // Ordering only: iris:carve ships as a module jar, which a pack may not have.
        setup.stages().define(IrisStages.Post.SMOOTH).softAfter(IrisStages.Carve.APPLY).run(SurfaceSmoothing::smooth);
    }
}
