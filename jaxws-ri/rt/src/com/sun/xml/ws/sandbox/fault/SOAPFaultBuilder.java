package com.sun.xml.ws.sandbox.fault;

import com.sun.xml.bind.api.Bridge;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.model.CheckedException;
import com.sun.xml.ws.api.model.ExceptionType;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.model.CheckedExceptionImpl;
import com.sun.xml.ws.util.StringUtils;
import com.sun.xml.ws.encoding.soap.SerializationException;
import com.sun.xml.ws.encoding.soap.SOAPConstants;
import com.sun.xml.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.ws.sandbox.message.impl.jaxb.JAXBMessage;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;
import javax.xml.transform.dom.DOMResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Base class that represents SOAP 1.1 or SOAP 1.2 fault. This class can be used by the invocation handlers to create
 * an Exception from a received messge.
 * <p/>
 * <p/>
 *
 * @author Vivek Pandey
 */
public abstract class SOAPFaultBuilder {

    /**
     * Gives the {@link DetailType} for a Soap 1.1 or Soap 1.2 message that can be used to create either a checked exception or
     * a protocol specific exception
     */
    abstract DetailType getDetail();

    /**
     * gives the fault string that can be used to create an {@link Exception}
     */
    abstract String getFaultString();

    /**
     * This should be called from the client side to throw an {@link Exception} for a given soap mesage
     */
    public Throwable createException(Map<QName, CheckedExceptionImpl> exceptions, Message msg) throws JAXBException {
        DetailType dt = getDetail();
        if ((dt == null) || (dt.getDetails().size() != 1)) {
            // No soap detail, doesnt look like its a checked exception
            // throw a protocol exception
            return getProtocolException(msg);
        }
        Node jaxbDetail = (Node)dt.getDetails().get(0);
        QName detailName = new QName(jaxbDetail.getNamespaceURI(), jaxbDetail.getLocalName());
        CheckedExceptionImpl ce = exceptions.get(detailName);
        if (ce == null) {
            //No Checked exception for the received detail QName, throw a SOAPFault exception
            return getProtocolException(msg);

        }
        if (ce.getExceptionType().equals(ExceptionType.UserDefined)) {
            return createUserDefinedException(ce);

        }
        Class exceptionClass = ce.getExcpetionClass();
        try {
            Constructor constructor = exceptionClass.getConstructor(String.class, (Class) ce.getDetailType().type);
            Object exception = constructor.newInstance(getFaultString(), getJAXBObject(jaxbDetail, ce));
            return (Exception) exception;
        } catch (Exception e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * To be called by the server runtime in the situations when there is an Exception that needs to be transformed in
     * to a soapenv:Fault payload.
     *
     * @param ceModel     {@link CheckedExceptionImpl} model that provides useful informations such as the detail tagname
     *                    and the Exception associated with it. Caller of this constructor should get the CheckedException
     *                    model by calling {@link com.sun.xml.ws.model.JavaMethodImpl#getCheckedException(Class)}, where
     *                    Class is t.getClass().
     *                    <p/>
     *                    If its null then this is not a checked exception  and in that case the soap fault will be
     *                    serialized only from the exception as described below.
     * @param ex          Exception that needs to be translated into soapenv:Fault, always non-null.
     *                    <p/>
     *                    <li>If t is instance of {@link javax.xml.ws.soap.SOAPFaultException} then its serilaized as protocol exception.
     *                    <li>If t.getCause() is instance of {@link javax.xml.ws.soap.SOAPFaultException} and t is a checked exception then
     *                    the soap fault detail is serilaized from t and the fault actor/string/role is taken from t.getCause().
     * @param soapVersion non-null
     */
    public static Message createSOAPFaultMessage(SOAPVersion soapVersion, CheckedExceptionImpl ceModel, Throwable ex) {
        Object detail = getFaultDetail(ceModel, ex);
        return createSOAPFault(soapVersion, ex, detail, ceModel);
    }

    /**
     * Server runtime will call this when there is some internal error not resulting from an exception.
     *
     * @param soapVersion {@link SOAPVersion#SOAP_11} or {@link SOAPVersion#SOAP_12}
     * @param faultString must be non-null
     * @param faultCode   For SOAP 1.1, it must be one of
     *                    <li>{@link SOAPConstants#FAULT_CODE_CLIENT}
     *                    <li>{@link SOAPConstants#FAULT_CODE_SERVER}
     *                    <li>{@link SOAPConstants#FAULT_CODE_MUST_UNDERSTAND}
     *                    <li>{@link SOAPConstants#FAULT_CODE_VERSION_MISMATCH}
     *                    <p/>
     *                    For SOAP 1.2
     *                    <p/>
     *                    <li>{@link SOAP12Constants#FAULT_CODE_CLIENT}
     *                    <li>{@link SOAP12Constants#FAULT_CODE_SERVER}
     *                    <li>{@link SOAP12Constants#FAULT_CODE_MUST_UNDERSTAND}
     *                    <li>{@link SOAP12Constants#FAULT_CODE_VERSION_MISMATCH}
     *                    <li>{@link SOAP12Constants#FAULT_CODE_DATA_ENCODING_UNKNOWN}
     * @return non-null {@link Message}
     */
    public static Message createSOAPFaultMessage(SOAPVersion soapVersion, String faultString, QName faultCode) {
        if (faultCode == null)
            faultCode = getDefaultFaultCode(soapVersion);
        return createSOAPFaultMessage(soapVersion, faultString, faultCode, null);

    }

    private static Message createSOAPFaultMessage(SOAPVersion soapVersion, String faultString, QName faultCode, Node detail) {
        switch (soapVersion) {
            case SOAP_11:
                return new JAXBMessage(JAXB_MARSHALLER, new SOAP11Fault(faultCode, faultString, null, detail), soapVersion);
            case SOAP_12:
                return new JAXBMessage(JAXB_MARSHALLER, new SOAP12Fault(faultCode, faultString, null, detail), soapVersion);
            default:
                throw new AssertionError();
        }
    }

    private Throwable getProtocolException(Message msg) {
        try {
            return new SOAPFaultException(msg.readAsSOAPMessage().getSOAPBody().getFault());
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    private Object getJAXBObject(Node jaxbBean, CheckedException ce) throws JAXBException {
        Bridge bridge = ce.getBridge();
        return bridge.unmarshal(ce.getOwner().getBridgeContext(), jaxbBean);
    }

    private Exception createUserDefinedException(CheckedExceptionImpl ce) {
        Class exceptionClass = ce.getExcpetionClass();
        try {
            Constructor constructor = exceptionClass.getConstructor(String.class);
            Object exception = constructor.newInstance(getFaultString());
            Object jaxbDetail = getDetail().getDetails().get(0);
            Field[] fields = jaxbDetail.getClass().getFields();
            for (Field f : fields) {
                Method m = exceptionClass.getMethod(getWriteMethod(f));
                m.invoke(exception, f.get(jaxbDetail));
            }
            throw (Exception) exception;
        } catch (Exception e) {
            throw new WebServiceException(e);
        }
    }

    private static String getWriteMethod(Field f) {
        return "set" + StringUtils.capitalize(f.getName());
    }

    private static Object getFaultDetail(CheckedExceptionImpl ce, Throwable exception) {
        if (ce == null)
            return null;
        if (ce.getExceptionType().equals(ExceptionType.UserDefined)) {
            return createDetailFromUserDefinedException(ce, exception);
        }
        try {
            Method m = exception.getClass().getMethod("getFaultInfo");
            return m.invoke(exception);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private static Object createDetailFromUserDefinedException(CheckedExceptionImpl ce, Object exception) {
        Class detailBean = ce.getDetailBean();
        Field[] fields = detailBean.getDeclaredFields();
        try {
            Object detail = detailBean.newInstance();
            for (Field f : fields) {
                Method em = exception.getClass().getMethod(getReadMethod(f));
                Method sm = detailBean.getMethod(getWriteMethod(f), em.getReturnType());
                sm.invoke(detail, em.invoke(exception));
            }
            return detail;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private static String getReadMethod(Field f) {
        if (f.getType().isAssignableFrom(boolean.class))
            return "is" + StringUtils.capitalize(f.getName());
        return "get" + StringUtils.capitalize(f.getName());
    }

    private static Message createSOAPFault(SOAPVersion soapVersion, Throwable e, Object detail, CheckedExceptionImpl ce) {
        SOAPFaultException soapFaultException = null;
        QName faultCode = null;
        String faultString = null;
        String faultActor = null;
        Throwable cause = e.getCause();
        if (e instanceof SOAPFaultException) {
            soapFaultException = (SOAPFaultException) e;
        } else if (cause != null && cause instanceof SOAPFaultException) {
            soapFaultException = (SOAPFaultException) e.getCause();
        }
        if (soapFaultException != null) {
            faultCode = soapFaultException.getFault().getFaultCodeAsQName();
            faultString = soapFaultException.getFault().getFaultString();
            faultActor = soapFaultException.getFault().getFaultActor();
        }

        if (faultCode == null) {
            faultCode = getDefaultFaultCode(soapVersion);
        }

        if (faultString == null) {
            faultString = e.getMessage();
            if (faultString == null) {
                faultString = e.toString();
            }
        }
        Node detailNode = null;
        if (detail == null && soapFaultException != null) {
            detail = soapFaultException.getFault().getDetail();
        } else if(detail != null){
            try {
                DOMResult dr = new DOMResult();
                ce.getBridge().marshal(ce.getOwner().getBridgeContext(), detail, dr);
                detailNode = dr.getNode().getFirstChild();
            } catch (JAXBException e1) {
                //Should we throw Internal Server Error???
                faultString = e.getMessage();
                faultCode = getDefaultFaultCode(soapVersion);
            }
        }
        return createSOAPFaultMessage(soapVersion, faultString, faultCode, detailNode);
    }

    private static QName getDefaultFaultCode(SOAPVersion soapVersion) {
        switch (soapVersion) {
            case SOAP_12:
                return SOAP12Constants.FAULT_CODE_SERVER;
            default:
                return SOAPConstants.FAULT_CODE_SERVER;
        }

    }

    /**
     * Parses a fault {@link Message} and returns it as a {@link SOAPFaultBuilder}.
     *
     * @return always non-null valid object.
     * @throws JAXBException if the parsing fails.
     */
    public static SOAPFaultBuilder create(Message msg) throws JAXBException {
        return msg.readPayloadAsJAXB(JAXB_CONTEXT.createUnmarshaller());
    }

    /**
     * This {@link JAXBContext} can handle SOAP 1.1/1.2 faults.
     */
    public static final JAXBContext JAXB_CONTEXT;

    private static final Marshaller JAXB_MARSHALLER;

    static {
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(SOAP11Fault.class, SOAP12Fault.class);
            JAXB_MARSHALLER = JAXB_CONTEXT.createMarshaller();
            JAXB_MARSHALLER.setProperty(Marshaller.JAXB_FRAGMENT, true);
        } catch (JAXBException e) {
            throw new Error(e); // this must be a bug in our code
        }
    }
}
