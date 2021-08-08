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

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class JavaUtils {
	// Temporarily copied from JavaEngine...

	private static Log logger;

	static {
		logger = LogFactory.getLog((com.volmit.iris.util.bsf.util.JavaUtils.class)
				.getName());
	}

	public static boolean JDKcompile(String fileName, String classPath) {
		String option = (logger.isDebugEnabled()) ? "-g" : "-O";
		String args[] = { "javac", option, "-classpath", classPath, fileName };

		logger.debug("JavaEngine: Compiling " + fileName);
		logger.debug("JavaEngine: Classpath is " + classPath);

		try {
			Process p = java.lang.Runtime.getRuntime().exec(args);
			p.waitFor();
			return (p.exitValue() != 0);
		} catch (IOException e) {
			logger.error("ERROR: IO exception during exec(javac).", e);
		} catch (SecurityException e) {
			logger.error("ERROR: Unable to create subprocess to exec(javac).",
					e);
		} catch (InterruptedException e) {
			logger.error("ERROR: Wait for exec(javac) was interrupted.", e);
		}
		return false;
	}
}
