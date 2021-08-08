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

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import com.volmit.iris.util.bsf.util.event.EventAdapterImpl;

public class java_awt_event_ItemAdapter extends EventAdapterImpl
										   implements ItemListener {

  public void itemStateChanged (ItemEvent e) {
	eventProcessor.processEvent ("itemStateChanged", new Object[]{e});
  }
}
