package com.sun.xml.ws.sandbox.message.impl;

import com.sun.xml.bind.api.Bridge;
import com.sun.xml.bind.api.BridgeContext;
import com.sun.xml.bind.unmarshaller.DOMScanner;
import com.sun.xml.messaging.saaj.packaging.mime.Header;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.streaming.DOMStreamReader;
import com.sun.xml.ws.util.DOMUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * {@link Header} implementation for a DOM.
 *
 * @author Kohsuke Kawaguchi
 */
public class DOMHeader<N extends Element> extends AbstractHeaderImpl {
    protected final N node;

    private final String nsUri;
    private final String localName;

    public DOMHeader(SOAPVersion soapVersion, N node) {
        super(soapVersion);
        assert node!=null;
        this.node = node;

        this.nsUri = fixNull(node.getNamespaceURI());
        this.localName = node.getLocalName();
    }


    public String getNamespaceURI() {
        return nsUri;
    }

    public String getLocalPart() {
        return localName;
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        DOMStreamReader r = new DOMStreamReader(node);
        r.nextTag();    // move ahead to the start tag
        return r;
    }

    public <T> T readAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        return (T) unmarshaller.unmarshal(node);
    }

    public <T> T readAsJAXB(Bridge<T> bridge, BridgeContext context) throws JAXBException {
        return bridge.unmarshal(context,node);
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        DOMUtil.serializeNode(node, w);
    }

    private static String fixNull(String s) {
        if(s!=null)     return s;
        else            return "";
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        DOMScanner ds = new DOMScanner();
        ds.setContentHandler(contentHandler);
        ds.scan(node);
    }

    public String getAttribute(String nsUri, String localName) {
        if(nsUri.length()==0)   nsUri=null; // DOM wants null, not "".
        return node.getAttributeNS(nsUri,localName);
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        SOAPHeader header = saaj.getSOAPHeader();
        Node clone = header.getOwnerDocument().importNode(node,true);
        header.appendChild(clone);
    }
}
