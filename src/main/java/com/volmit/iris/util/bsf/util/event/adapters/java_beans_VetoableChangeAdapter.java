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

package com.volmit.iris.util.bsf.util.event.adapters;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

import com.volmit.iris.util.bsf.util.event.EventAdapterImpl;

public class java_beans_VetoableChangeAdapter extends EventAdapterImpl
												 implements VetoableChangeListener {

  public void vetoableChange (PropertyChangeEvent e) throws PropertyVetoException {
	try
	{
	  eventProcessor.processExceptionableEvent (e.getPropertyName(), new Object[]{e});
	}
	catch (PropertyVetoException ex)
	{
	  throw ex;
	}
	catch (Exception ex)
	{
	}
  }
}
