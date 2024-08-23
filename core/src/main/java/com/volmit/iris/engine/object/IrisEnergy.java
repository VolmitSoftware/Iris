/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object;

import com.dfsek.paralithic.Expression;
import com.dfsek.paralithic.eval.parser.Parser;
import com.dfsek.paralithic.eval.parser.Scope;
import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@SuppressWarnings("ALL")
@Snippet("energy")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor

@Desc("Represents an iris entity.")
@Data
public class IrisEnergy {
    @Desc("If true, the spawner system has infinite energy. This is NOT recommended because it would allow for mobs to keep spawning over and over without a rate limit")
    private boolean infiniteEnergy = false;
    @MinNumber(0)
    @MaxNumber(10000)
//    @Desc("This is the maximum energy you can have in a dimension")
//    private double maximumEnergy = 1000;
    @Desc("The expression. For your energy scaling, Inherited variables are ce ( current energy ). Avoid using those variable names. ")
    private String expressionMax = null;
    @Desc("The expression. For your energy scaling, Inherited variables are ce ( current energy ). Avoid using those variable names. ")
    private String expressionCur = null;
    @ArrayType(type = IrisEnergyExpressionLoad.class, min = 1)
    @Desc("Variables to use in this expression")
    private KList<IrisEnergyExpressionLoad> variables = new KList<>();

    private static final Parser parser = new Parser();
    private transient AtomicCache<Expression> expressionMaxCache = new AtomicCache<>();
    private transient AtomicCache<Expression> expressionCurCache = new AtomicCache<>();

    private Expression getExpression(String type) {
        switch (type) {
            case "max":
                return expressionMaxCache.aquire(() -> parseExpression(expressionMax, "1000"));
            case "cur":
                return expressionCurCache.aquire(() -> parseExpression(expressionCur, "1000"));
            default:
                throw new IllegalArgumentException("Unknown expression type: " + type);
        }
    }

    private Expression parseExpression(String expression, String defaultValue) {
        Scope scope = new Scope();

        try {
            for (IrisEnergyExpressionLoad i : variables) {
                scope.addInvocationVariable(i.getName());
            }

            scope.addInvocationVariable("ce");
        } catch (Throwable e) {
            e.printStackTrace();
            Iris.error("Script Variable load error in Energy Expression");
        }

        try {
            if (expression != null) {
                return parser.parse(expression, scope);
            }
            return parser.parse(defaultValue, scope);
        } catch (Throwable e) {
            e.printStackTrace();
            Iris.error("Script load error in Energy Expression");
        }

        return null;
    }

    public double evaluateMax(String type, RNG rng, IrisData data, Double ce) {
        Expression expression = getExpression(type);

        double[] g = new double[3 + getVariables().size()];
        int m = 0;
        for (IrisEnergyExpressionLoad i : getVariables()) {
            g[m++] = i.getValue(rng, data, ce);
        }

        g[m++] = ce;
        g[m] = -1;

        return expression.evaluate(g);
    }

}
