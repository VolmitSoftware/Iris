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

/**
 * The public face of Iris's built-in modules: every stage constant, in {@link art.arcane.iris.builtin.IrisStages},
 * and the settings and output types of those stages. {@link art.arcane.iris.core.modules.IrisModuleHost} exposes this package, with its
 * subpackages, to pack modules, so a pack jar can {@code requires(...)} a built-in stage and read its
 * output. Everything here is part of what packs compile against.
 * <p>
 * Keep implementation out of it: stage bodies and anything that uses Iris internals live in the
 * built-in module classes outside this package, which packs cannot see. Settings and output types go in one
 * subpackage per built-in module, named after the module path, such as {@code art.arcane.iris.builtin.spawn}
 * for {@code iris:spawn}. A type only the built-in module jars use, which Iris itself never reads, lives in
 * {@code art.arcane.iris.modules} instead, which Iris does not expose.
 * <p>
 * Besides those, it holds what Iris and the built-in module jars must share, since each sees its own copy
 * of every other class: narrow facades only Iris's own built-ins use, interfaces Iris's worlds implement
 * ({@link art.arcane.iris.builtin.structure.StructureVerifier}), and the seed purposes of the built-in features
 * ({@link art.arcane.iris.builtin.IrisSeedPurposes}). Keep them few: each is a promise.
 * <p>
 * The placement stages ({@code iris:prepare} to {@code iris:snow}) run in the pack's placement graph, which
 * {@code PackWorld.placements()} runs. They read the object and its settings from the run's input,
 * {@link art.arcane.iris.builtin.objects.ObjectRun}, which Iris also builds when it places an object itself.
 */
package art.arcane.iris.builtin;
