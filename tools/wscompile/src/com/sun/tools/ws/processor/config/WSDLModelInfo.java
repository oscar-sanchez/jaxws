/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */

package com.sun.tools.ws.processor.config;


import java.util.*;

import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.sun.tools.ws.processor.modeler.Modeler;
import com.sun.tools.ws.util.JAXWSClassFactory;

/**
 *
 * @author WS Development Team
 */
public class WSDLModelInfo extends ModelInfo {

    public WSDLModelInfo() {}

    protected Modeler getModeler(Properties options) {
        return JAXWSClassFactory.newInstance().createWSDLModeler(this, options);
    }

    public String getLocation() {
        return _location;
    }

    public void setLocation(String s) {
        _location = s;
    }

    public Map<String, Document> getJAXWSBindings(){
        return _jaxwsBindings;
    }

    public void putJAXWSBindings(String systemId, Document binding){
        _jaxwsBindings.put(systemId, binding);
    }

    public Set<InputSource> getJAXBBindings(){
        return _jaxbBindings;
    }

    public void addJAXBBIndings(InputSource jaxbBinding){        
        _jaxbBindings.add(jaxbBinding);
    }

    public void setHandlerConfig(Element handlerConfig){
        this.handlerConfig = handlerConfig;
    }
    
    public Element getHandlerConfig(){
        return handlerConfig;
    }
    
    private Element handlerConfig;
    
    private String _location;

    //external jaxws:bindings elements
    private Map<String, Document> _jaxwsBindings = new HashMap<String, Document>();

    //we need an array of jaxb:binding elements, they are children of jaxws:bindings
    //and could come from an external customization file or wsdl.
    private Set<InputSource> _jaxbBindings = new HashSet<InputSource>();
}
