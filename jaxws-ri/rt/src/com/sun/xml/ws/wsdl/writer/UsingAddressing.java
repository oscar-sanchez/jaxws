/*
 The contents of this file are subject to the terms
 of the Common Development and Distribution License
 (the "License").  You may not use this file except
 in compliance with the License.
 
 You can obtain a copy of the license at
 https://jwsdp.dev.java.net/CDDLv1.0.html
 See the License for the specific language governing
 permissions and limitations under the License.
 
 When distributing Covered Code, include this CDDL
 HEADER in each file and include the License file at
 https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 add the following below this CDDL HEADER, with the
 fields enclosed by brackets "[]" replaced with your
 own identifying information: Portions Copyright [yyyy]
 [name of copyright owner]
*/
/*
 $Id: UsingAddressing.java,v 1.1.2.3 2006-09-26 22:17:15 ramapulavarthi Exp $

 Copyright (c) 2006 Sun Microsystems, Inc.
 All rights reserved.
*/

package com.sun.xml.ws.wsdl.writer;

import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.annotation.XmlAttribute;
import com.sun.xml.txw2.annotation.XmlElement;
import com.sun.xml.ws.addressing.W3CAddressingConstants;
import com.sun.xml.ws.wsdl.writer.document.StartWithExtensionsType;

/**
 * @author Arun Gupta
 */
@XmlElement(value = W3CAddressingConstants.WSA_NAMESPACE_WSDL_NAME,
            ns = W3CAddressingConstants.WSAW_USING_ADDRESSING_NAME)
public interface UsingAddressing extends TypedXmlWriter, StartWithExtensionsType {
    @XmlAttribute(value = "required", ns = "http://schemas.xmlsoap.org/wsdl/")
    public void required(boolean b);
}
