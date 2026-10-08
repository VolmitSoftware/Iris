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

import art.arcane.iris.core.loader.IrisData;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.validation.Problem;
import com.volmit.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What one module validator sees of a pack Iris validates: the pack's resources and its settings as Iris loads them
 * ({@link PackDeclaredSettings}), and the problems it reported. Built-in validators that still read Iris's own pack
 * data reach it through {@link #of(ValidationContext)}, as built-in stages reach the engine through their carriers.
 */
public final class IrisValidationContext implements ValidationContext {
    private final IrisData data;
    private final PackSettings settings;
    private final List<Problem> problems = new ArrayList<>();

    private IrisValidationContext(IrisData data) {
        this.data = data;
        this.settings = new PackDeclaredSettings(data);
    }

    /** A context for one validator run over the pack {@code data} holds. */
    public static IrisValidationContext forPack(IrisData data) {
        return new IrisValidationContext(Objects.requireNonNull(data, "data"));
    }

    /** Iris's own view of {@code context}, which Iris hands to every validator it runs. */
    public static IrisValidationContext of(ValidationContext context) {
        if (!(context instanceof IrisValidationContext irisContext)) {
            throw new IllegalArgumentException("Built-in validators check packs Iris validates, not " + context);
        }
        return irisContext;
    }

    /** The pack's data, opened for this validation. */
    public IrisData data() {
        return data;
    }

    @Override
    public PackResources resources() {
        return data.getPackResources();
    }

    @Override
    public PackSettings settings() {
        return settings;
    }

    @Override
    public void report(Problem problem) {
        problems.add(Objects.requireNonNull(problem, "problem"));
    }

    /** What the validator reported, in order. */
    public List<Problem> problems() {
        return List.copyOf(problems);
    }
}
