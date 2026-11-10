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

package art.arcane.iris.core.studio.noise;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

/**
 * The randoms the world evaluates the pack's expressions with, found by following them down from the dimension's noise
 * styles. An expression previewed with its random lines up with the terrain it shapes.
 */
final class ExpressionSeeds {
    private final Engine engine;
    private final IrisData data;
    private final Map<String, RNG> randomsByExpression = new HashMap<>();

    private ExpressionSeeds(Engine engine) {
        this.engine = engine;
        this.data = engine.getData();
    }

    static Map<String, RNG> of(Engine engine) {
        ExpressionSeeds seeds = new ExpressionSeeds(engine);
        for (IrisComplex.SeededStyle root : engine.getComplex().seededNoiseStyles(engine.getDimension())) {
            seeds.follow(root.style(), root.rng());
        }
        return Map.copyOf(seeds.randomsByExpression);
    }

    // Variables and functions sample their styles with their expression's random, so it passes down unchanged.
    private void follow(IrisGeneratorStyle style, RNG callerRandom) {
        if (style == null || style.getExpression() == null) {
            return;
        }
        String key = style.getExpression();
        RNG expressionRandom = style.rollFor(callerRandom, engine);
        if (randomsByExpression.putIfAbsent(key, expressionRandom) != null) {
            return;
        }
        IrisExpression expression = data.getExpressionLoader().load(key);
        if (expression == null) {
            return;
        }
        expression.getVariables().forEach(variable -> follow(variable.getStyleValue(), expressionRandom));
        expression.getFunctions().forEach(function -> follow(function.getStyleValue(), expressionRandom));
    }
}
