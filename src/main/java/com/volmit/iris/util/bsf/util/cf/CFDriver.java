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

package com.volmit.iris.util.bsf.util.cf;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * This is an example of how a <code>CodeFormatter</code> bean can be used.
 * <p>
 * The CFDriver is a stand-alone tool that will instantiate a
 * <code>CodeFormatter</code> bean, configure it according to your
 * command-line arguments, and invoke the formatting. Since the
 * default source of input is <code>stdin</code>, and the default
 * target for output is <code>stdout</code>, a <code>CFDriver</code>
 * can also be used as a filter.
 *
 * @see CodeFormatter
 *
 * @version 1.0
 * @author Matthew J. Duftler
 */
public class CFDriver
{
	/**
	 * Not used.
	 */
	public CFDriver()
  {
  }
  /**
	* A driver for <code>CodeFormatter</code>.
	*<p>
	* Usage:
	*<code><pre>
	*  java org.apache.cf.CFDriver [args]
	*<p>
	*    args:
	*<p>
	*      [-in      fileName]   default: &lt;STDIN&gt;
	*      [-out     fileName]   default: &lt;STDOUT&gt;
	*      [-maxLine   length]   default: 74
	*      [-step        size]   default: 2
	*      [-delim      group]   default: (+
	*      [-sdelim     group]   default: ,
	*</pre></code>
	*/
	public static void main(String[] argv)
  {
	if (argv.length % 2 == 0)
	{
	  String        inFile  = null,
					outFile = null,
					maxLine = null,
					indStep = null,
					delim   = null,
					sDelim  = null;
	  Reader        in      = null;
		Writer        out     = null;
	  CodeFormatter cf      = new CodeFormatter();

	  for (int i = 0; i < argv.length; i += 2)
	  {
		if (argv[i].startsWith("-i"))
		  inFile = argv[i + 1];
		else if (argv[i].startsWith("-o"))
		  outFile = argv[i + 1];
	  	else if (argv[i].startsWith("-m"))
	  		maxLine = argv[i + 1];
		else if (argv[i].startsWith("-st"))
		  indStep = argv[i + 1];
	  	else if (argv[i].startsWith("-d"))
	  		delim = argv[i + 1];
		else if (argv[i].startsWith("-sd"))
		  sDelim = argv[i + 1];
	  }

	  if (inFile != null)
	  {
		try
		{
		  in = new FileReader(inFile);
		}
		catch (FileNotFoundException e)
		{
		  printError("Cannot open input file: " + inFile);
			
		  return;
		}
	  }
	  else
	  {
		in = new InputStreamReader(System.in);
	  }

	  if (outFile != null)
	  {
		try
		{
		  out = new FileWriter(outFile);
		}
		catch (IOException e)
		{
		  printError("Cannot open output file: " + outFile);
		  
		  return;
		}
	  }
	  else
	  {
		out = new OutputStreamWriter(System.out);
	  }

		if (maxLine != null)
	  {
	  	try
		{
			cf.setMaxLineLength(Integer.parseInt(maxLine));
		}
	  	catch (NumberFormatException nfe)
		{
			printError("Not a valid integer: " + maxLine);
			
			return;
		}
	  }

	  if (indStep != null)
	  {
		try
		{
		  cf.setIndentationStep(Integer.parseInt(indStep));
		}
		catch (NumberFormatException nfe)
		{
		  printError("Not a valid integer: " + indStep);
		  
		  return;
		}
	  }
		
		if (delim != null)
		  cf.setDelimiters(delim);
		
		if (sDelim != null)
		cf.setStickyDelimiters(sDelim);
			
		cf.formatCode(in, out);
	}
	else
	  printHelp();
  }
	private static void printError(String errMsg)
  {
  	System.err.println("ERROR: " + errMsg);
  }
	private static void printHelp()
  {
	System.out.println("Usage:");
	System.out.println();
	System.out.println("  java " + CFDriver.class.getName() + " [args]");
	System.out.println();
	System.out.println("    args:");
	System.out.println();
	System.out.println("      [-in      fileName]   default: <STDIN>");
	System.out.println("      [-out     fileName]   default: <STDOUT>");
	System.out.println("      [-maxLine   length]   default: " +
					   CodeFormatter.DEFAULT_MAX);
	System.out.println("      [-step        size]   default: " +
					   CodeFormatter.DEFAULT_STEP);
	System.out.println("      [-delim      group]   default: " +
					   CodeFormatter.DEFAULT_DELIM);
	System.out.println("      [-sdelim     group]   default: " +
					   CodeFormatter.DEFAULT_S_DELIM);
  }
}
