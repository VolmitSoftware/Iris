/*
 * Copyright 2004,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.volmit.iris.util.bsf.util;

import com.volmit.iris.util.bsf.BSFEngine;
import com.volmit.iris.util.bsf.BSFException;
import com.volmit.iris.util.bsf.BSFManager;

/**
 * This is a utility that engine implementors may use as the Java
 * object they expose in the scripting language as "bsf". This has
 * essentially a subset of the methods in BSFManager plus some
 * stuff from the utils. Currently used by Javascript (Rhino) & BML.
 *
 * @author   Sanjiva Weerawarana
 */
public class BSFFunctions {
  BSFManager mgr;
  BSFEngine engine;

  public BSFFunctions (BSFManager mgr, BSFEngine engine) {
	this.mgr = mgr;
	this.engine = engine;
  }
  public void addEventListener (Object src, String eventSetName,
				String filter, Object script)
	   throws BSFException {
	EngineUtils.addEventListener (src, eventSetName, filter, engine, 
				  mgr, "<event-binding>", 0, 0, script);
  }
  public  Object lookupBean (String name) {
	return mgr.lookupBean (name);
  }
  public void registerBean (String name, Object bean) {
	mgr.registerBean (name, bean);
  }
  public void unregisterBean (String name) {
	mgr.unregisterBean (name);
  }
}
