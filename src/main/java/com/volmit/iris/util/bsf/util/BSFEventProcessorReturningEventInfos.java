/*
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the Apache Software Foundation and was originally created by
 * Sanjiva Weerawarana and others at International Business Machines
 * Corporation. For more information on the Apache Software Foundation,
 * please see <http://www.apache.org/>.
 */

package com.volmit.iris.util.bsf.util;

import java.util.Vector;

import com.volmit.iris.util.bsf.BSFEngine;
import com.volmit.iris.util.bsf.BSFException;
import com.volmit.iris.util.bsf.BSFManager;
import com.volmit.iris.util.bsf.util.event.EventProcessor;

/*
 * Copyright (C) 2001-2006 Rony G. Flatscher
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * <a
 * href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 */

/**
 * 
 * This is used to support binding scripts to be run when an event occurs,
 * forwarding the arguments supplied to the event listener. It is an adapted
 * version of com.volmit.iris.util.bsf.util.BSFEventProcessor.
 * 
 * @author Rony G. Flatscher, but most of the code copied from
 *         com.volmit.iris.util.bsf.util.BSFEventProcessor by Sanjiva Weerawarana
 */
public class BSFEventProcessorReturningEventInfos implements EventProcessor {
    BSFEngine engine;

    BSFManager manager;

    String filter;

    String source;

    int lineNo;

    int columnNo;

    Object script;

    Object dataFromScriptingEngine; // ---rgf, 2006-02-24: data coming from the
                                    // script engine, could be

    // e.g. an object reference to forward event with received arguments to

    /**
     * Package-protected constructor makes this class unavailable for public
     * use.
     * 
     * @param dataFromScriptingEngine
     *            this contains any object supplied by the scripting engine and
     *            gets sent back with the supplied script. This could be used
     *            e.g. for indicating which scripting engine object should be
     *            ultimately informed of the event occurrence.
     */
    BSFEventProcessorReturningEventInfos(BSFEngine engine, BSFManager manager,
            String filter, String source, int lineNo, int columnNo,
            Object script, Object dataFromScriptingEngine) throws BSFException {
        this.engine = engine;
        this.manager = manager;
        this.filter = filter;
        this.source = source;
        this.lineNo = lineNo;
        this.columnNo = columnNo;
        this.script = script;
        this.dataFromScriptingEngine = dataFromScriptingEngine;
    }

    // ////////////////////////////////////////////////////////////////////////
    //
    // event is delegated to me by the adapters using this. inFilter is
    // in general the name of the method via which the event was received
    // at the adapter. For prop/veto change events, inFilter is the name
    // of the property. In any case, in the event processor, I only forward
    // those events if for which the filters match (if one is specified).

    public void processEvent(String inFilter, Object[] evtInfo) {
        try {
            processExceptionableEvent(inFilter, evtInfo);
        } catch (RuntimeException re) {
            // rethrow this .. I don't want to intercept run-time stuff
            // that can in fact occur legit
            throw re;
        } catch (Exception e) {
            // should not occur
            System.err.println("BSFError: non-exceptionable event delivery "
                    + "threw exception (that's not nice): " + e);
            e.printStackTrace();
        }
    }

    // ////////////////////////////////////////////////////////////////////////
    //
    // same as above, but used when the method event method may generate
    // an exception which must go all the way back to the source (as in
    // the vetoableChange case)

    public void processExceptionableEvent(String inFilter, Object[] evtInfo)
            throws Exception {

        // System.err.println(this+": inFilter=["+inFilter+"],
        // filter=["+filter+"]");
        if ((filter != null) && !filter.equals(inFilter)) {
            // ignore this event
            return;
        }

        // run the script
        // engine.exec (source, lineNo, columnNo, script);

        // create the parameter vectors for engine.apply()
        Vector paramNames = new Vector(), paramValues = new Vector();

        // parameter # 1
        // supply the parameters as an array object as sent to the event object
        // listener
        // (usually the first entry is the sent event object)
        paramNames.add("eventParameters");
        paramValues.add(evtInfo);

        // parameter # 2
        // supply the data object received from the scripting engine to be sent
        // with the event
        paramNames.add("dataFromScriptingEngine");
        paramValues.add(this.dataFromScriptingEngine); // can be null as well

        // parameter # 3
        // event filter in place
        paramNames.add("inFilter");
        paramValues.add(inFilter); // event name that has occurred

        // parameter # 4
        // event filter in place
        paramNames.add("eventFilter");
        paramValues.add(this.filter); // can be null as well

        // parameter # 5
        // BSF manager instance (e.g. allows access to its registry)
        paramNames.add("BSFManager");
        paramValues.add(this.manager);

        engine.apply(source, lineNo, columnNo, this.script, paramNames,
                paramValues);
    }
}
