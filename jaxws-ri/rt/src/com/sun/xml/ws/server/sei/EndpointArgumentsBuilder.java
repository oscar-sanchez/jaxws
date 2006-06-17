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
package com.sun.xml.ws.server.sei;

import com.sun.xml.bind.api.AccessorException;
import com.sun.xml.bind.api.Bridge;
import com.sun.xml.bind.api.CompositeStructure;
import com.sun.xml.bind.api.RawAccessor;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.model.ParameterBinding;
import com.sun.xml.ws.model.ParameterImpl;
import com.sun.xml.ws.model.WrapperParameter;
import com.sun.xml.ws.streaming.XMLStreamReaderUtil;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import javax.jws.WebParam.Mode;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.activation.DataHandler;
import javax.imageio.ImageIO;
import javax.xml.transform.Source;

/**
 * Reads a request {@link Message}, disassembles it, and moves obtained Java values
 * to the expected places.
 *
 * @author Jitendra Kotamraju
 */
abstract class EndpointArgumentsBuilder {
    /**
     * Reads a request {@link Message}, disassembles it, and moves obtained
     * Java values to the expected places.
     *
     * @param request
     *      The request {@link Message} to be de-composed.
     * @param args
     *      The Java arguments given to the SEI method invocation.
     *      Some parts of the reply message may be set to {@link Holder}s in the arguments.
     * @throws JAXBException
     *      if there's an error during unmarshalling the request message.
     * @throws XMLStreamException
     *      if there's an error during unmarshalling the request message.
     */
    abstract void readRequest(Message request, Object[] args)
        throws JAXBException, XMLStreamException;

    static final class None extends EndpointArgumentsBuilder {
        private None(){
        }
        public void readRequest(Message msg, Object[] args) {
        }
    }

    /**
     * The singleton instance that produces null return value.
     * Used for operations that doesn't have any output.
     */
    public static EndpointArgumentsBuilder NONE = new None();

    /**
     * Returns the 'uninitialized' value for the given type.
     *
     * <p>
     * For primitive types, it's '0', and for reference types, it's null.
     */
    public static Object getVMUninitializedValue(Type type) {
        // if this map returns null, that means the 'type' is a reference type,
        // in which case 'null' is the correct null value, so this code is correct.
        return primitiveUninitializedValues.get(type);
    }

    private static final Map<Class,Object> primitiveUninitializedValues = new HashMap<Class, Object>();

    static {
        Map<Class, Object> m = primitiveUninitializedValues;
        m.put(int.class,(int)0);
        m.put(char.class,(char)0);
        m.put(byte.class,(byte)0);
        m.put(short.class,(short)0);
        m.put(long.class,(long)0);
        m.put(float.class,(float)0);
        m.put(double.class,(double)0);
    }

    /**
     * {@link EndpointArgumentsBuilder} that sets the VM uninitialized value to the type.
     */
    static final class NullSetter extends EndpointArgumentsBuilder {
        private final EndpointValueSetter setter;
        private final Object nullValue;

        public NullSetter(EndpointValueSetter setter, Object nullValue){
            assert setter!=null;
            this.nullValue = nullValue;
            this.setter = setter;
        }
        public void readRequest(Message msg, Object[] args) {
            setter.put(nullValue, args);
        }
    }

    /**
     * {@link EndpointArgumentsBuilder} that is a composition of multiple
     * {@link EndpointArgumentsBuilder}s.
     *
     * <p>
     * Sometimes we need to look at multiple parts of the reply message
     * (say, two header params, one body param, and three attachments, etc.)
     * and that's when this object is used to combine multiple {@link EndpointArgumentsBuilder}s
     * (that each responsible for handling one part).
     *
     * <p>
     * The model guarantees that only at most one {@link EndpointArgumentsBuilder} will
     * return a value as a return value (and everything else has to go to
     * {@link Holder}s.)
     */
    static final class Composite extends EndpointArgumentsBuilder {
        private final EndpointArgumentsBuilder[] builders;

        public Composite(EndpointArgumentsBuilder... builders) {
            this.builders = builders;
        }

        public Composite(Collection<? extends EndpointArgumentsBuilder> builders) {
            this(builders.toArray(new EndpointArgumentsBuilder[builders.size()]));
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            for (EndpointArgumentsBuilder builder : builders) {
                builder.readRequest(msg,args);
            }
        }
    }

    
    /**
     * Reads an Attachment into a Java parameter.
     */
    static final class Attachment {

        /**
         * @param setter
         *      specifies how the obtained value is returned to the client.
         */
        public static EndpointArgumentsBuilder createAttachment(ParameterImpl param, EndpointValueSetter setter) {
            Class type = (Class)param.getTypeReference().type;
            param.getPartName();
            if (DataHandler.class.isAssignableFrom(type)) {
                return new DataHandlerAttachment();
            } else if (byte[].class==type) {
                return new ByteArrayAttachment();
            } else if(Source.class.isAssignableFrom(type)) {
                return new SourceAttachment();
            } else if(Image.class.isAssignableFrom(type)) {
                return new ImageAttachment(param, setter);
            } else if(InputStream.class==type) {
                return new InputStreamAttachment();
                /*
            } else if(isXMLMimeType(paramBinding.getMimeType())) {
                return new XMLAttachment();
                 */
            } else {
                //throw new UnsupportedOperationException("Attachment is not mapped");
            }
            return null;
            
        }
        
        static final class DataHandlerAttachment extends EndpointArgumentsBuilder {
            public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
                throw new UnsupportedOperationException("Attachment is not mapped");
            }
        }
        
        static final class ByteArrayAttachment extends EndpointArgumentsBuilder {
            public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
                throw new UnsupportedOperationException("Attachment is not mapped");
            }
        }
        
        static final class SourceAttachment extends EndpointArgumentsBuilder {
            public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
                throw new UnsupportedOperationException("Attachment is not mapped");
            }
        }
        
        static final class ImageAttachment extends EndpointArgumentsBuilder {
            private final EndpointValueSetter setter;
            private final ParameterImpl param;
            private final String pname;
            private final String pname1;
            
            ImageAttachment(ParameterImpl param, EndpointValueSetter setter) {
                this.setter = setter;
                this.param = param;
                this.pname = param.getPartName();
                this.pname1 = "<"+pname;
            }
            
            public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
                // TODO not to loop
                for (com.sun.xml.ws.api.message.Attachment att : msg.getAttachments()) {
                    String part = getWSDLPartName(att);
                    if (part == null) {
                        continue;
                    }
                    if(part.equals(pname) || part.equals(pname1)){
                        Image image;
                        try {
                            image = image = ImageIO.read(att.asInputStream());
                        } catch(IOException ioe) {
                            throw new WebServiceException(ioe);
                        }
                        if (image != null) {
                            setter.put(image, args);
                        }
                    }
                }
            }
        }
        
        static final class InputStreamAttachment extends EndpointArgumentsBuilder {
            public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
                throw new UnsupportedOperationException("Attachment is not mapped");
            }
        }
        
        static final class XMLAttachment extends EndpointArgumentsBuilder {
            public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
                throw new UnsupportedOperationException("Attachment is not mapped");
            }
        }

    }
    
        /**
     * Gets the WSDL part name of this attachment.
     *
     * <p>
     * According to WSI AP 1.0
     * <PRE>
     * 3.8 Value-space of Content-Id Header
     *   Definition: content-id part encoding
     *   The "content-id part encoding" consists of the concatenation of:
     * The value of the name attribute of the wsdl:part element referenced by the mime:content, in which characters disallowed in content-id headers (non-ASCII characters as represented by code points above 0x7F) are escaped as follows:
     *     o Each disallowed character is converted to UTF-8 as one or more bytes.
     *     o Any bytes corresponding to a disallowed character are escaped with the URI escaping mechanism (that is, converted to %HH, where HH is the hexadecimal notation of the byte value).
     *     o The original character is replaced by the resulting character sequence.
     * The character '=' (0x3D).
     * A globally unique value such as a UUID.
     * The character '@' (0x40).
     * A valid domain name under the authority of the entity constructing the message.
     * </PRE>
     *
     * So a wsdl:part fooPart will be encoded as:
     *      <fooPart=somereallybignumberlikeauuid@example.com>
     *
     * @return null
     *      if the parsing fails.
     */
    public static final String getWSDLPartName(com.sun.xml.ws.api.message.Attachment att){
        String cId = att.getContentId();

        int index = cId.lastIndexOf('@', cId.length());
        if(index == -1){
            return null;
        }
        String localPart = cId.substring(0, index);
        index = localPart.lastIndexOf('=', localPart.length());
        if(index == -1){
            return null;
        }
        try {
            return java.net.URLDecoder.decode(localPart.substring(0, index), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new WebServiceException(e);
        }
    }

    
    

    /**
     * Reads a header into a JAXB object.
     */
    static final class Header extends EndpointArgumentsBuilder {
        private final Bridge<?> bridge;
        private final EndpointValueSetter setter;
        private final QName headerName;

        /**
         * @param name
         *      The name of the header element.
         * @param bridge
         *      specifies how to unmarshal a header into a JAXB object.
         * @param setter
         *      specifies how the obtained value is returned to the client.
         */
        public Header(QName name, Bridge<?> bridge, EndpointValueSetter setter) {
            this.headerName = name;
            this.bridge = bridge;
            this.setter = setter;
        }

        public Header(ParameterImpl param, EndpointValueSetter setter) {
            this(
                param.getTypeReference().tagName,
                param.getBridge(),
                setter);
            assert param.getOutBinding()== ParameterBinding.HEADER;
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException {
            com.sun.xml.ws.api.message.Header header =
                msg.getHeaders().get(headerName,true);

            if(header!=null) {
                setter.put( header.readAsJAXB(bridge), args );
            } else {
                // header not found.
            }
        }
    }

    /**
     * Reads the whole payload into a single JAXB bean.
     */
    static final class Body extends EndpointArgumentsBuilder {
        private final Bridge<?> bridge;
        private final EndpointValueSetter setter;

        /**
         * @param bridge
         *      specifies how to unmarshal the payload into a JAXB object.
         * @param setter
         *      specifies how the obtained value is returned to the client.
         */
        public Body(Bridge<?> bridge, EndpointValueSetter setter) {
            this.bridge = bridge;
            this.setter = setter;
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException {
            setter.put( msg.readPayloadAsJAXB(bridge), args );
        }
    }

    /**
     * Treats a payload as multiple parts wrapped into one element,
     * and processes all such wrapped parts.
     */
    static final class DocLit extends EndpointArgumentsBuilder {
        /**
         * {@link PartBuilder} keyed by the element name (inside the wrapper element.)
         */
        private final PartBuilder[] parts;

        private final Bridge wrapper;

        public DocLit(WrapperParameter wp, Mode skipMode) {
            wrapper = wp.getBridge();
            Class wrapperType = (Class) wrapper.getTypeReference().type;

            List<PartBuilder> parts = new ArrayList<PartBuilder>();

            List<ParameterImpl> children = wp.getWrapperChildren();
            for (ParameterImpl p : children) {
                if (p.getMode() == skipMode) {
                    continue;
                }
                /*
                if(p.isIN())
                    continue;
                 */
                QName name = p.getName();
                try {
                    parts.add( new PartBuilder(
                        wp.getOwner().getJAXBContext().getElementPropertyAccessor(
                            wrapperType,
                            name.getNamespaceURI(),
                            p.getName().getLocalPart()),
                        EndpointValueSetter.get(p)
                    ));
                    // wrapper parameter itself always bind to body, and
                    // so do all its children
                    assert p.getBinding()== ParameterBinding.BODY;
                } catch (JAXBException e) {
                    throw new WebServiceException(  // TODO: i18n
                        wrapperType+" do not have a property of the name "+name,e);
                }
            }

            this.parts = parts.toArray(new PartBuilder[parts.size()]);
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            Object retVal = null;

            XMLStreamReader reader = msg.readPayload();
            Object wrapperBean = wrapper.unmarshal(reader);

            try {
                for (PartBuilder part : parts) {
                    Object o = part.readResponse(args,wrapperBean);
                    // there's only at most one EndpointArgumentsBuilder that returns a value.
                    // TODO: reorder parts so that the return value comes at the end.
                    if(o!=null) {
                        assert retVal==null;
                        retVal = o;
                    }
                }
            } catch (AccessorException e) {
                // this can happen when the set method throw a checked exception or something like that
                throw new WebServiceException(e);    // TODO:i18n
            }

            // we are done with the body
            reader.close();
        }

        /**
         * Unmarshals each wrapped part into a JAXB object and moves it
         * to the expected place.
         */
        static final class PartBuilder {
            private final RawAccessor accessor;
            private final EndpointValueSetter setter;

            /**
             * @param accessor
             *      specifies which portion of the wrapper bean to obtain the value from.
             * @param setter
             *      specifies how the obtained value is returned to the client.
             */
            public PartBuilder(RawAccessor accessor, EndpointValueSetter setter) {
                this.accessor = accessor;
                this.setter = setter;
                assert accessor!=null && setter!=null;
            }

            final Object readResponse( Object[] args, Object wrapperBean ) throws AccessorException {
                Object obj = accessor.get(wrapperBean);
                setter.put(obj,args);
                return null;
            }


        }
    }

    /**
     * Treats a payload as multiple parts wrapped into one element,
     * and processes all such wrapped parts.
     */
    static final class RpcLit extends EndpointArgumentsBuilder {
        /**
         * {@link PartBuilder} keyed by the element name (inside the wrapper element.)
         */
        private final Map<QName,PartBuilder> parts = new HashMap<QName,PartBuilder>();

        private QName wrapperName;

        public RpcLit(WrapperParameter wp) {
            assert wp.getTypeReference().type== CompositeStructure.class;

            wrapperName = wp.getName();
            List<ParameterImpl> children = wp.getWrapperChildren();
            for (ParameterImpl p : children) {
                parts.put( p.getName(), new PartBuilder(
                    p.getBridge(), EndpointValueSetter.get(p)
                ));
                // wrapper parameter itself always bind to body, and
                // so do all its children
                assert p.getBinding()== ParameterBinding.BODY;
            }
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            XMLStreamReader reader = msg.readPayload();
            if (!reader.getName().equals(wrapperName))
                throw new WebServiceException( // TODO: i18n
                    "Unexpected response element "+reader.getName()+" expected: "+wrapperName);
            reader.nextTag();

            while(reader.getEventType()==XMLStreamReader.START_ELEMENT) {
                // TODO: QName has a performance issue
                PartBuilder part = parts.get(reader.getName());
                if(part==null) {
                    // no corresponding part found. ignore
                    XMLStreamReaderUtil.skipElement(reader);
                    reader.nextTag();
                } else {
                    part.readRequest(args,reader);
                }
            }

            // we are done with the body
            reader.close();
        }

        /**
         * Unmarshals each wrapped part into a JAXB object and moves it
         * to the expected place.
         */
        static final class PartBuilder {
            private final Bridge bridge;
            private final EndpointValueSetter setter;

            /**
             * @param bridge
             *      specifies how the part is unmarshalled.
             * @param setter
             *      specifies how the obtained value is returned to the endpoint.
             */
            public PartBuilder(Bridge bridge, EndpointValueSetter setter) {
                this.bridge = bridge;
                this.setter = setter;
            }

            final void readRequest( Object[] args, XMLStreamReader r ) throws JAXBException {
                Object obj = bridge.unmarshal(r);
                setter.put(obj,args);
            }
        }
    }
}
