/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems Inc. All Rights Reserved
 */

package com.sun.tools.ws.wsdl.parser;

import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import com.sun.tools.ws.wsdl.document.soap.SOAPAddress;
import com.sun.tools.ws.wsdl.document.soap.SOAPBinding;
import com.sun.tools.ws.wsdl.document.soap.SOAPBody;
import com.sun.tools.ws.wsdl.document.soap.SOAPConstants;
import com.sun.tools.ws.wsdl.document.soap.SOAPFault;
import com.sun.tools.ws.wsdl.document.soap.SOAPHeader;
import com.sun.tools.ws.wsdl.document.soap.SOAPHeaderFault;
import com.sun.tools.ws.wsdl.document.soap.SOAPOperation;
import com.sun.tools.ws.wsdl.document.soap.SOAPStyle;
import com.sun.tools.ws.wsdl.document.soap.SOAPUse;
import com.sun.tools.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.ws.api.wsdl.TWSDLParserContext;
import com.sun.tools.ws.wsdl.framework.TWSDLParserContextImpl;
import com.sun.tools.ws.util.xml.XmlUtil;

/**
 * The SOAP extension handler for WSDL.
 *
 * @author WS Development Team
 */
public class SOAPExtensionHandler extends AbstractExtensionHandler {

    public SOAPExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap) {
        super(extensionHandlerMap);
    }

    public String getNamespaceURI() {
        return Constants.NS_WSDL_SOAP;
    }

    public boolean handleDefinitionsExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    public boolean handleTypesExtension(
        com.sun.tools.ws.api.wsdl.TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    protected SOAPBinding getSOAPBinding(){
        return new SOAPBinding();
    }

    public boolean handleBindingExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, getBindingQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPBinding binding = getSOAPBinding();

            // NOTE - the "transport" attribute is required according to section 3.3 of the WSDL 1.1 spec,
            // but optional according to the schema in appendix A 4.2 of the same document!
            String transport =
                Util.getRequiredAttribute(e, Constants.ATTR_TRANSPORT);
            binding.setTransport(transport);

            String style = XmlUtil.getAttributeOrNull(e, Constants.ATTR_STYLE);
            if (style != null) {
                if (style.equals(Constants.ATTRVALUE_RPC)) {
                    binding.setStyle(SOAPStyle.RPC);
                } else if (style.equals(Constants.ATTRVALUE_DOCUMENT)) {
                    binding.setStyle(SOAPStyle.DOCUMENT);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_STYLE,
                        style);
                }
            }
            parent.addExtension(binding);
            context.pop();
//            context.fireDoneParsingEntity(getBindingQName(), binding);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    public boolean handleOperationExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, getOperationQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPOperation operation = new SOAPOperation();

            String soapAction =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_SOAP_ACTION);
            if (soapAction != null) {
                operation.setSOAPAction(soapAction);
            }

            String style = XmlUtil.getAttributeOrNull(e, Constants.ATTR_STYLE);
            if (style != null) {
                if (style.equals(Constants.ATTRVALUE_RPC)) {
                    operation.setStyle(SOAPStyle.RPC);
                } else if (style.equals(Constants.ATTRVALUE_DOCUMENT)) {
                    operation.setStyle(SOAPStyle.DOCUMENT);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_STYLE,
                        style);
                }
            }
            parent.addExtension(operation);
            context.pop();
//            context.fireDoneParsingEntity(
//                getOperationQName(),
//                operation);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    public boolean handleInputExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        return handleInputOutputExtension(context, parent, e);
    }
    public boolean handleOutputExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        return handleInputOutputExtension(context, parent, e);
    }

    @Override
    protected boolean handleMIMEPartExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        return handleInputOutputExtension(context, parent, e);
    }

    protected boolean handleInputOutputExtension(
        TWSDLParserContext contextif,
        TWSDLExtensible parent,
        Element e) {
        TWSDLParserContextImpl context = (TWSDLParserContextImpl)contextif;
        if (XmlUtil.matchesTagNS(e, getBodyQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPBody body = new SOAPBody();

            String use = XmlUtil.getAttributeOrNull(e, Constants.ATTR_USE);
            if (use != null) {
                if (use.equals(Constants.ATTRVALUE_LITERAL)) {
                    body.setUse(SOAPUse.LITERAL);
                } else if (use.equals(Constants.ATTRVALUE_ENCODED)) {
                    body.setUse(SOAPUse.ENCODED);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_USE,
                        use);
                }
            }

            String namespace =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAMESPACE);
            if (namespace != null) {
                body.setNamespace(namespace);
            }

            String encodingStyle =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_ENCODING_STYLE);
            if (encodingStyle != null) {
                body.setEncodingStyle(encodingStyle);
            }

            String parts = XmlUtil.getAttributeOrNull(e, Constants.ATTR_PARTS);
            if (parts != null) {
                body.setParts(parts);
            }

            parent.addExtension(body);
            context.pop();
//            context.fireDoneParsingEntity(getBodyQName(), body);
            return true;
        } else if (XmlUtil.matchesTagNS(e, getHeaderQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPHeader header = new SOAPHeader();

            String use = XmlUtil.getAttributeOrNull(e, Constants.ATTR_USE);
            if (use != null) {
                if (use.equals(Constants.ATTRVALUE_LITERAL)) {
                    header.setUse(SOAPUse.LITERAL);
                } else if (use.equals(Constants.ATTRVALUE_ENCODED)) {
                    header.setUse(SOAPUse.ENCODED);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_USE,
                        use);
                }
            }

            String namespace =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAMESPACE);
            if (namespace != null) {
                header.setNamespace(namespace);
            }

            String encodingStyle =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_ENCODING_STYLE);
            if (encodingStyle != null) {
                header.setEncodingStyle(encodingStyle);
            }

            String part = XmlUtil.getAttributeOrNull(e, Constants.ATTR_PART);
            if (part != null) {
                header.setPart(part);
            }

            String messageAttr =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_MESSAGE);
            if (messageAttr != null) {
                header.setMessage(context.translateQualifiedName(messageAttr));
            }

            for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;

                if (XmlUtil
                    .matchesTagNS(e2, getHeaderfaultQName())) {
                    context.push();
                    context.registerNamespaces(e);

                    SOAPHeaderFault headerfault = new SOAPHeaderFault();

                    String use2 =
                        XmlUtil.getAttributeOrNull(e2, Constants.ATTR_USE);
                    if (use2 != null) {
                        if (use2.equals(Constants.ATTRVALUE_LITERAL)) {
                            headerfault.setUse(SOAPUse.LITERAL);
                        } else if (use.equals(Constants.ATTRVALUE_ENCODED)) {
                            headerfault.setUse(SOAPUse.ENCODED);
                        } else {
                            Util.fail(
                                "parsing.invalidAttributeValue",
                                Constants.ATTR_USE,
                                use2);
                        }
                    }

                    String namespace2 =
                        XmlUtil.getAttributeOrNull(
                            e2,
                            Constants.ATTR_NAMESPACE);
                    if (namespace2 != null) {
                        headerfault.setNamespace(namespace2);
                    }

                    String encodingStyle2 =
                        XmlUtil.getAttributeOrNull(
                            e2,
                            Constants.ATTR_ENCODING_STYLE);
                    if (encodingStyle2 != null) {
                        headerfault.setEncodingStyle(encodingStyle2);
                    }

                    String part2 =
                        XmlUtil.getAttributeOrNull(e2, Constants.ATTR_PART);
                    if (part2 != null) {
                        headerfault.setPart(part2);
                    }

                    String messageAttr2 =
                        XmlUtil.getAttributeOrNull(e2, Constants.ATTR_MESSAGE);
                    if (messageAttr2 != null) {
                        headerfault.setMessage(
                            context.translateQualifiedName(messageAttr2));
                    }

                    header.add(headerfault);
                    context.pop();
                } else {
                    Util.fail(
                        "parsing.invalidElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                }
            }

            parent.addExtension(header);
            context.pop();
            context.fireDoneParsingEntity(getHeaderQName(), header);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    public boolean handleFaultExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, getFaultQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPFault fault = new SOAPFault();

            String name = XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAME);
            if (name != null) {
                fault.setName(name);
            }

            String use = XmlUtil.getAttributeOrNull(e, Constants.ATTR_USE);
            if (use != null) {
                if (use.equals(Constants.ATTRVALUE_LITERAL)) {
                    fault.setUse(SOAPUse.LITERAL);
                } else if (use.equals(Constants.ATTRVALUE_ENCODED)) {
                    fault.setUse(SOAPUse.ENCODED);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_USE,
                        use);
                }
            }

            String namespace =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAMESPACE);
            if (namespace != null) {
                fault.setNamespace(namespace);
            }

            String encodingStyle =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_ENCODING_STYLE);
            if (encodingStyle != null) {
                fault.setEncodingStyle(encodingStyle);
            }

            parent.addExtension(fault);
            context.pop();
//            context.fireDoneParsingEntity(getFaultQName(), fault);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    public boolean handleServiceExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    @Override
    public boolean handlePortExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, getAddressQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPAddress address = new SOAPAddress();

            String location =
                Util.getRequiredAttribute(e, Constants.ATTR_LOCATION);
            address.setLocation(location);

            parent.addExtension(address);
            context.pop();
//            context.fireDoneParsingEntity(getAddressQName(), address);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    public boolean handlePortTypeExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
       Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    protected QName getBodyQName(){
        return SOAPConstants.QNAME_BODY;
    }

    protected QName getHeaderQName(){
        return SOAPConstants.QNAME_HEADER;
    }

    protected QName getHeaderfaultQName(){
        return SOAPConstants.QNAME_HEADERFAULT;
    }

    protected QName getOperationQName(){
        return SOAPConstants.QNAME_OPERATION;
    }

    protected QName getFaultQName(){
        return SOAPConstants.QNAME_FAULT;
    }

    protected QName getAddressQName(){
        return SOAPConstants.QNAME_ADDRESS;
    }

    protected QName getBindingQName(){
        return SOAPConstants.QNAME_BINDING;
    }
}
