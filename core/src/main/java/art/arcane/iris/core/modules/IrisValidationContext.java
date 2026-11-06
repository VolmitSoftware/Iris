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
import com.volmit.resource.PlatformRegistries;
import com.volmit.validation.Problem;
import com.volmit.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What one module validator sees of a pack Iris validates: the pack's resources and its settings as Iris loads them
 * ({@link PackDeclaredSettings}), and the problems it reported. Built-in validators that still read Iris's own pack
 * data reach it through {@link #of(ValidationContext)}, as built-in stages reach the engine through their carriers.
 */
public final class IrisValidationContext implements ValidationContext {
    private final IrisData data;
    private final PackSettings settings;
    private final Optional<PlatformRegistries> platform;
    private final List<Problem> problems = new ArrayList<>();

    private IrisValidationContext(IrisData data, Optional<PlatformRegistries> platform) {
        this.data = data;
        this.settings = new PackDeclaredSettings(data);
        this.platform = platform;
    }

    /** A context for one validator run over the pack {@code data} holds, without the platform's registries. */
    public static IrisValidationContext forPack(IrisData data) {
        return forPack(data, Optional.empty());
    }

    /** A context for one validator run over the pack {@code data} holds, seeing {@code platform}'s registries. */
    public static IrisValidationContext forPack(IrisData data, Optional<PlatformRegistries> platform) {
        return new IrisValidationContext(Objects.requireNonNull(data, "data"), Objects.requireNonNull(platform, "platform"));
    }

    /** The registries of the platform Iris is bound to, read on first use; empty when none is bound, such as in a build. */
    public static Optional<PlatformRegistries> boundPlatformRegistries() {
        return IrisPlatformRegistryIds.ofBoundPlatform();
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
    public Optional<PlatformRegistries> platform() {
        return platform;
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
