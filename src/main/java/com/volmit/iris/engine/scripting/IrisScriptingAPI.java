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

package com.volmit.iris.engine.scripting;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisExpression;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

@Data
public class IrisScriptingAPI {
    private final Engine engine;
    private IrisRegistrant preprocessorObject;
    private double x = 0;
    private double y = 0;
    private double z = 0;
    private Location location;
    private Entity entity;

    public IrisScriptingAPI(Engine engine) {
        this.engine = engine;
    }

    public IrisData getData() {
        return getEngine().getData();
    }

    public IrisComplex getComplex() {
        return getEngine().getComplex();
    }

    public long getSeed() {
        return getEngine().getSeedManager().getScript();
    }

    public double expression(String expressionName, double x, double y, double z) {
        IrisExpression expression = getData().getExpressionLoader().load(expressionName);
        return expression.evaluate(getComplex().getRng(), x, y, z);
    }

    public double expression(String expressionName, double x, double z) {
        IrisExpression expression = getData().getExpressionLoader().load(expressionName);
        return expression.evaluate(getComplex().getRng(), x, z);
    }

    public IrisBiome getBiomeAt(int x, int z) {
        return getEngine().getSurfaceBiome(x, z);
    }

    public IrisDimension getDimension() {
        return getEngine().getDimension();
    }

    public void info(String log) {
        Iris.info(log);
    }

    public void debug(String log) {
        Iris.debug(log);
    }

    public void warn(String log) {
        Iris.warn(log);
    }

    public void error(String log) {
        Iris.error(log);
    }
}
