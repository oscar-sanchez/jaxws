package com.sun.xml.ws.client.port;

import com.sun.xml.bind.api.AccessorException;
import com.sun.xml.bind.api.Bridge;
import com.sun.xml.bind.api.CompositeStructure;
import com.sun.xml.bind.api.RawAccessor;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Messages;
import com.sun.xml.ws.model.ParameterImpl;
import com.sun.xml.ws.model.WrapperParameter;
import com.sun.xml.ws.sandbox.message.impl.jaxb.JAXBMessage;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.util.List;

/**
 * Builds a JAXB object that represents the payload.
 *
 * @see MessageFiller
 * @author Kohsuke Kawaguchi
 */
abstract class BodyBuilder {
    abstract Message createMessage(Object[] methodArgs);

    static final BodyBuilder EMPTY_SOAP11 = new Empty(SOAPVersion.SOAP_11);
    static final BodyBuilder EMPTY_SOAP12 = new Empty(SOAPVersion.SOAP_12);

    private static final class Empty extends BodyBuilder {
        private final SOAPVersion soapVersion;

        public Empty(SOAPVersion soapVersion) {
            this.soapVersion = soapVersion;
        }

        Message createMessage(Object[] methodArgs) {
            return Messages.createEmpty(soapVersion);
        }
    }

    /**
     * Base class for those {@link BodyBuilder}s that build a {@link Message}
     * from JAXB objects.
     */
    private static abstract class JAXB extends BodyBuilder {
        /**
         * This object determines the binding of the object returned
         * from {@link #build(Object[])}.
         */
        private final Bridge bridge;
        private final PortInterfaceStub owner;

        protected JAXB(Bridge bridge,PortInterfaceStub owner) {
            assert bridge!=null;
            this.bridge = bridge;
            assert owner!=null;
            this.owner = owner;
        }

        final Message createMessage(Object[] methodArgs) {
            return new JAXBMessage(
                bridge, build(methodArgs),
                owner.seiModel.getBridgeContext(), owner.soapVersion );
        }

        /**
         * Builds a JAXB object that becomes the payload.
         */
        abstract Object build(Object[] methodArgs);
    }

    /**
     * Used to create a payload JAXB object just by taking
     * one of the parameters.
     */
    final static class Bare extends JAXB {
        /**
         * The index of the method invocation parameters that goes into the payload.
         */
        private final int methodPos;

        private final ValueGetter getter;

        /**
         * Creates a {@link BodyBuilder} from a bare parameter.
         */
        Bare(ParameterImpl p, PortInterfaceStub owner) {
            super(p.getBridge(),owner);
            this.methodPos = p.getIndex();
            this.getter = ValueGetter.get(p);
        }

        /**
         * Picks up an object from the method arguments and uses it.
         */
        Object build(Object[] methodArgs) {
            return getter.get(methodArgs[methodPos]);
        }
    }

    /**
     * Used to create a payload JAXB object by wrapping
     * multiple parameters into one bean.
     */
    final static class Wrapped extends JAXB {
        /**
         * How does each wrapped parameter binds to XML?
         */
        private final RawAccessor[] accessors;

        /**
         * Where in the method argument list do they come from?
         */
        private final int[] indices;

        /**
         * Abstracts away the {@link Holder} handling when touching method arguments.
         */
        private final ValueGetter[] getters;

        /**
         * Wrapper bean.
         */
        private final Class wrapper;

        /**
         * Creates a {@link BodyBuilder} from a {@link WrapperParameter}.
         */
        Wrapped(WrapperParameter wp, PortInterfaceStub owner) {
            super(wp.getBridge(),owner);

            wrapper = (Class)wp.getBridge().getTypeReference().type;

            List<ParameterImpl> children = wp.getWrapperChildren();

            accessors = new RawAccessor[children.size()];
            for( int i=0; i<accessors.length; i++ ) {
                ParameterImpl p = children.get(i);
                QName name = p.getName();
                try {
                    accessors[i] = p.getOwner().getJAXBContext().getElementPropertyAccessor(
                        wrapper, name.getNamespaceURI(), name.getLocalPart() );
                } catch (JAXBException e) {
                    throw new WebServiceException(  // TODO: i18n
                        wrapper+" do not have a property of the name "+name,e);
                }
            }

            indices = new int[children.size()];
            getters = new ValueGetter[children.size()];
            for( int i=0; i<indices.length; i++ ) {
                ParameterImpl p = children.get(i);
                indices[i] = p.getIndex();
                getters[i] = ValueGetter.get(p);
            }
        }

        /**
         * Packs a bunch of arguments into a {@link CompositeStructure}.
         */
        Object build(Object[] methodArgs) {
            try {
                Object bean = wrapper.newInstance();

                // fill in wrapped parameters from methodArgs
                for( int i=indices.length-1; i>=0; i-- ) {
                    accessors[i].set(bean,getters[i].get(methodArgs[indices[i]]));
                }

                return bean;
            } catch (InstantiationException e) {
                // this is irrecoverable
                Error x = new InstantiationError(e.getMessage());
                x.initCause(e);
                throw x;
            } catch (IllegalAccessException e) {
                // this is irrecoverable
                Error x = new IllegalAccessError(e.getMessage());
                x.initCause(e);
                throw x;
            } catch (AccessorException e) {
                // this can happen when the set method throw a checked exception or something like that
                throw new WebServiceException(e);    // TODO:i18n
            }
        }
    }
}
