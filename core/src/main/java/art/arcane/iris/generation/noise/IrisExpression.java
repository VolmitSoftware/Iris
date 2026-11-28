/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.noise;

import art.arcane.iris.spi.IrisLogging;
import com.dfsek.paralithic.Expression;
import com.dfsek.paralithic.functions.dynamic.DynamicFunction;
import com.dfsek.paralithic.eval.parser.Parser;
import com.dfsek.paralithic.eval.parser.Scope;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.function.Function;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents an Iris Expression")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisExpression extends IrisRegistrant {
    @ArrayType(type = IrisExpressionLoad.class, min = 1)
    @Description("Variables to use in this expression")
    private KList<IrisExpressionLoad> variables = new KList<>();

    @ArrayType(type = IrisExpressionFunction.class, min = 1)
    @Description("Functions to use in this expression")
    private KList<IrisExpressionFunction> functions = new KList<>();

    @Required
    @Description("The expression. Inherited variables are x, y and z. Avoid using those variable names.")
    private String expression;

    /**
     * Parses this expression with each valid function bound to the runtime object the caller built for it.
     * Returns null when the expression does not parse; the failure is reported.
     */
    public Expression parse(Function<IrisExpressionFunction, DynamicFunction> functionRuntime) {
        Scope scope = new Scope(); // Create variable scope. This scope can hold both constants and invocation variables.
        Parser parser = new Parser();

        try {
            for (IrisExpressionLoad i : variables) {
                scope.addInvocationVariable(i.getName());
            }

            scope.addInvocationVariable("x");
            scope.addInvocationVariable("y");
            scope.addInvocationVariable("z");
        } catch (Throwable e) {
            IrisLogging.reportError("Script variable load failed in " + getLoadFile().getPath() + ".", e);
        }

        for (IrisExpressionFunction f : functions) {
            if (!f.isValid()) continue;
            parser.registerFunction(f.getName(), functionRuntime.apply(f));
        }

        try {
            return parser.parse(getExpression(), scope);
        } catch (Throwable e) {
            IrisLogging.reportError("Script load failed in " + getLoadFile().getPath() + ".", e);
        }

        return null;
    }

    @Override
    public String getFolderName() {
        return "expressions";
    }

    @Override
    public String getTypeName() {
        return "Expression";
    }
}
