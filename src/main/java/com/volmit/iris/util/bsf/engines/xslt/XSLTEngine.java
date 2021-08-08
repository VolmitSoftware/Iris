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

package com.volmit.iris.util.bsf.engines.xslt;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.Vector;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import com.volmit.iris.util.bsf.BSFDeclaredBean;
import com.volmit.iris.util.bsf.BSFException;
import com.volmit.iris.util.bsf.BSFManager;
import com.volmit.iris.util.bsf.util.BSFEngineImpl;
import com.volmit.iris.util.bsf.util.BSFFunctions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xpath.objects.XObject;
import org.w3c.dom.Node;

/**
 * Xerces XSLT interface to BSF. Requires Xalan and Xerces from Apache.
 * 
 * This integration uses the BSF registry to pass in any src document
 * and stylesheet base URI that the user may wish to set. 
 *
 * @author   Sanjiva Weerawarana
 * @author   Sam Ruby
 *
 * Re-implemented for the Xalan 2 codebase
 * 
 * @author   Victor J. Orlikowski
 */
public class XSLTEngine extends BSFEngineImpl {
    TransformerFactory tFactory;
    Transformer transformer;
    
    Log logger = LogFactory.getLog(this.getClass().getName());

    /**
     * call the named method of the given object.
     */
    public Object call (Object object, String method, Object[] args) 
        throws BSFException {
	throw new BSFException (BSFException.REASON_UNSUPPORTED_FEATURE,
                                "BSF:XSLTEngine can't call methods");
    }

    /**
     * Declare a bean by setting it as a parameter
     */
    public void declareBean (BSFDeclaredBean bean) throws BSFException {
        transformer.setParameter (bean.name, new XObject (bean.bean));
    }

    /**
     * Evaluate an expression. In this case, an expression is assumed
     * to be a stylesheet of the template style (see the XSLT spec).
     */
    public Object eval (String source, int lineNo, int columnNo, 
                        Object oscript) throws BSFException {
	// get the style base URI (the place from where Xerces XSLT will
	// look for imported/included files and referenced docs): if a
	// bean named "xslt:styleBaseURI" is registered, then cvt it
	// to a string and use that. Otherwise use ".", which means the
	// base is the directory where the process is running from
	Object sbObj = mgr.lookupBean ("xslt:styleBaseURI");
	String styleBaseURI = (sbObj == null) ? "." : sbObj.toString ();

	// Locate the stylesheet.
	StreamSource styleSource;

        styleSource = 
            new StreamSource(new StringReader(oscript.toString ()));
        styleSource.setSystemId(styleBaseURI);

        try {
            transformer = tFactory.newTransformer(styleSource);
        } catch (Exception e) {
        	logger.error("Exception from Xerces XSLT:", e);
            throw new BSFException (BSFException.REASON_EXECUTION_ERROR,
                                    "Exception from Xerces XSLT: " + e, e);
        }

	// get the src to work on: if a bean named "xslt:src" is registered
	// and its a Node, then use it as the source. If its not a Node, then
	// if its a URL parse it, if not treat it as a file and make a URL and
	// parse it and go. If no xslt:src is found, use an empty document
	// (stylesheet is treated as a literal result element stylesheet)
	Object srcObj = mgr.lookupBean ("xslt:src");
	Object xis = null;
	if (srcObj != null) {
            if (srcObj instanceof Node) {
		xis = new DOMSource((Node)srcObj);
            } else {
		try {
                    String mesg = "as anything";
                    if (srcObj instanceof Reader) {
			xis = new StreamSource ((Reader) srcObj);
			mesg = "as a Reader";
                    } else if (srcObj instanceof File) {
                        xis = new StreamSource ((File) srcObj);
                        mesg = "as a file";
                    } else {
                        String srcObjstr=srcObj.toString();
                        xis = new StreamSource (new StringReader(srcObjstr));
                        if (srcObj instanceof URL) {
                            mesg = "as a URL";
                        } else {
                            ((StreamSource) xis).setPublicId (srcObjstr);
                            mesg = "as an XML string";
                        }
                    }

                    if (xis == null) {
			throw new Exception ("Unable to get input from '" +
                                             srcObj + "' " + mesg);
                    }
		} catch (Exception e) {
                    throw new BSFException (BSFException.REASON_EXECUTION_ERROR,
                                            "BSF:XSLTEngine: unable to get " +
                                            "input from '" + srcObj + "' as XML", e);
		}
            }
	} else {
            // create an empty document - real src must come into the 
            // stylesheet using "doc(...)" [see XSLT spec] or the stylesheet
            // must be of literal result element type
            xis = new StreamSource();
	}
	
	// set all declared beans as parameters.
	for (int i = 0; i < declaredBeans.size (); i++) {
            BSFDeclaredBean b = (BSFDeclaredBean) declaredBeans.elementAt (i);
            transformer.setParameter (b.name, new XObject (b.bean));
	}

	// declare a "bsf" parameter which is the BSF handle so that 
	// the script can do BSF stuff if it wants to
	transformer.setParameter ("bsf", 
                                  new XObject (new BSFFunctions (mgr, this)));

	// do it
	try {
            DOMResult result = new DOMResult();
            transformer.transform ((StreamSource) xis, result);
            return new XSLTResultNode (result.getNode());
	} catch (Exception e) {
            throw new BSFException (BSFException.REASON_EXECUTION_ERROR,
                                    "exception while eval'ing XSLT script" + e, e);
	}
    }

    /**
     * Initialize the engine.
     */
    public void initialize (BSFManager mgr, String lang,
                            Vector declaredBeans) throws BSFException {
	super.initialize (mgr, lang, declaredBeans);

        tFactory = TransformerFactory.newInstance();
    }

    /**
     * Undeclare a bean by setting he parameter represeting it to null
     */
    public void undeclareBean (BSFDeclaredBean bean) throws BSFException {
        // Cannot clear only one parameter in Xalan 2, so we set it to null
        if ((transformer.getParameter (bean.name)) != null) {
            transformer.setParameter (bean.name, null);
        }
    }
}
