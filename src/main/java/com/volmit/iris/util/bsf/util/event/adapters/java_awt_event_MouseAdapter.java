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

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import com.volmit.iris.util.bsf.util.event.EventAdapterImpl;

public class java_awt_event_MouseAdapter extends EventAdapterImpl
											implements MouseListener {

  public void mouseClicked (MouseEvent e) {
	eventProcessor.processEvent ("mouseClicked", new Object[]{e});
  }
  public void mouseEntered (MouseEvent e) {
	eventProcessor.processEvent ("mouseEntered", new Object[]{e});
  }
  public void mouseExited (MouseEvent e) {
	eventProcessor.processEvent ("mouseExited", new Object[]{e});
  }
  public void mousePressed (MouseEvent e) {
	eventProcessor.processEvent ("mousePressed", new Object[]{e});
  }
  public void mouseReleased (MouseEvent e) {
	eventProcessor.processEvent ("mouseReleased", new Object[]{e});
  }
}
