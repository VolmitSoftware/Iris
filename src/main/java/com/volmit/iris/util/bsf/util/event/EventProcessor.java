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

package com.volmit.iris.util.bsf.util.event;


/**
 * <em>EventProcessor</em> is the interface that event adapters use to
 * delegate events they received to be delivered to the appropriate target.
 * They can simply deliver the event using processEvent or, if the event
 * can be excepted to, via processExceptionableEvent (in which case the
 * adapter is expected to forward on an exception to the source bean).
 * 
 * @author   Sanjiva Weerawarana
 * @author   Matthew J. Duftler
 * @see      EventAdapter
 */
public interface EventProcessor {
  public void processEvent (String filter, Object[] eventInfo);
  public void processExceptionableEvent (String filter, Object[] eventInfo) 
	   throws Exception;
}
