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

package com.volmit.iris.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisScript;
import com.volmit.iris.engine.scripting.EngineExecutionEnvironment;
import com.volmit.iris.engine.scripting.IrisScriptingAPI;
import com.volmit.iris.util.format.C;
import lombok.Data;
import org.apache.bsf.BSFException;
import org.apache.bsf.BSFManager;
import org.apache.bsf.engines.javascript.JavaScriptEngine;

@Data
public class IrisExecutionEnvironment implements EngineExecutionEnvironment {
    private final BSFManager manager;
    private final Engine engine;
    private final IrisScriptingAPI api;
    private JavaScriptEngine javaScriptEngine;

    public IrisExecutionEnvironment(Engine engine) {
        this.engine = engine;
        this.api = new IrisScriptingAPI(engine);
        this.manager = new BSFManager();
        this.manager.setClassLoader(Iris.class.getClassLoader());
        try {
            this.manager.declareBean("Iris", api, api.getClass());
            this.javaScriptEngine = (JavaScriptEngine) this.manager.loadScriptingEngine("javascript");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public IrisScriptingAPI getAPI() {
        return api;
    }

    public void execute(String script) {
        execute(getEngine().getData().getScriptLoader().load(script));
    }

    public void execute(IrisScript script) {
        Iris.debug("Execute Script (void) " + C.DARK_GREEN + script.getLoadKey());
        try {
            javaScriptEngine.exec("", 0, 0, script);
        } catch (BSFException e) {
            e.printStackTrace();
        }
    }

    public Object evaluate(String script) {
        Iris.debug("Execute Script (for result) " + C.DARK_GREEN + script);
        try {
            return javaScriptEngine.eval("", 0, 0, getEngine().getData().getScriptLoader().load(script));
        } catch (BSFException e) {
            e.printStackTrace();
        }

        return null;
    }
}
