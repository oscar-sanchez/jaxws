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
package com.sun.xml.ws.model;

import com.sun.xml.bind.api.Bridge;
import com.sun.xml.bind.api.BridgeContext;
import com.sun.xml.bind.api.JAXBRIContext;
import com.sun.xml.bind.api.RawAccessor;
import com.sun.xml.bind.api.TypeReference;
import com.sun.xml.ws.api.model.CheckedException;
import com.sun.xml.ws.api.model.JavaMethod;
import com.sun.xml.ws.api.model.Mode;
import com.sun.xml.ws.api.model.Parameter;
import com.sun.xml.ws.api.model.ParameterBinding;
import com.sun.xml.ws.api.model.RuntimeModel;
import com.sun.xml.ws.api.model.wsdl.BoundOperation;
import com.sun.xml.ws.api.model.wsdl.BoundPortType;
import com.sun.xml.ws.api.model.wsdl.Part;
import com.sun.xml.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.ws.encoding.JAXWSAttachmentUnmarshaller;
import com.sun.xml.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.ws.encoding.jaxb.RpcLitPayload;
import com.sun.xml.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.ws.model.wsdl.BoundPortTypeImpl;
import com.sun.xml.ws.pept.presentation.MEP;

import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * model of the web service.  Used by the runtime marshall/unmarshall
 * web service invocations
 *
 * $author: JAXWS Development Team
 */
public abstract class AbstractRuntimeModelImpl implements RuntimeModel {

    /**
     *
     */
    public AbstractRuntimeModelImpl() {
        super();
    }

    void postProcess() {
        // should be called only once.
        if (jaxbContext != null)
            return;
        populateMaps();
        populateAsyncExceptions();
        createJAXBContext();
        createDecoderInfo();
    }

    /**
     * Link {@link RuntimeModel} to {@link WSDLModel}.
     * Merge it with {@link #postProcess()}.
     */
    void freeze(BoundPortType portType) {
        for (JavaMethodImpl m : javaMethods) {
            m.freeze(portType);
        }
    }

    /**
     * Populate methodToJM and nameToJM maps.
     */
    protected void populateMaps() {
        for (JavaMethodImpl jm : getJavaMethods()) {
            put(jm.getMethod(), jm);
            for (Parameter p : jm.getRequestParameters()) {
                put(p.getName(), jm);
            }
        }
    }

    protected void populateAsyncExceptions() {
        for (JavaMethodImpl jm : getJavaMethods()) {
            MEP mep = jm.getMEP();
            if (mep.isAsync) {
                String opName = jm.getOperationName();
                Method m = jm.getMethod();
                Class[] params = m.getParameterTypes();
                if (mep == MEP.ASYNC_CALLBACK) {
                    params = new Class[params.length-1];
                    System.arraycopy(m.getParameterTypes(), 0, params, 0, m.getParameterTypes().length-1);
                }
                try {
                    Method om = m.getDeclaringClass().getMethod(opName, params);
                    JavaMethod jm2 = getJavaMethod(om);
                    for (CheckedException ce : jm2.getCheckedExceptions()) {
                        jm.addException(ce);
                    }
                } catch (NoSuchMethodException ex) {
                }
            }
        }
    }

    /**
     * @return the <code>BridgeContext</code> for this <code>RuntimeModel</code>
     */
    public BridgeContext getBridgeContext() {
        if (jaxbContext == null)
            return null;
        BridgeContext bc = bridgeContext.get();
        if (bc == null) {
            bc = jaxbContext.createBridgeContext();
            bc.setAttachmentMarshaller(new JAXWSAttachmentMarshaller(enableMtom));
            bc.setAttachmentUnmarshaller(new JAXWSAttachmentUnmarshaller());
            bridgeContext.set(bc);
        }
        return bc;
    }

    /**
     * @return the <code>JAXBRIContext</code>
     */
    public JAXBRIContext getJAXBContext() {
        return jaxbContext;
    }

    /**
     * @return the known namespaces from JAXBRIContext
     */
    public List<String> getKnownNamespaceURIs() {
        return knownNamespaceURIs;
    }

    /**
     * @param type
     * @return the <code>Bridge</code> for the <code>type</code>
     */
    public Bridge getBridge(TypeReference type) {
        return bridgeMap.get(type);
    }

    /**
     * @param name
     * @return either a <code>RpcLitpayload</code> or a <code>JAXBBridgeInfo</code> for
     * either a message payload or header
     * @deprecated Will no longer be needed with the {@link com.sun.xml.ws.api.message.Message}
     */
    public Object getDecoderInfo(QName name) {
        Object obj = payloadMap.get(name);
        if (obj instanceof RpcLitPayload) {
            return RpcLitPayload.copy((RpcLitPayload) obj);
        } else if (obj instanceof JAXBBridgeInfo) {
            return JAXBBridgeInfo.copy((JAXBBridgeInfo) obj);
        }
        return null;
    }

    /**
     * @param name Qualified name of the message payload or header
     * @param payload  One of {@link RpcLitPayload} or {@link JAXBBridgeInfo}
     * @deprecated It will be no longer needed with the {@link com.sun.xml.ws.api.message.Message}
     */
    void addDecoderInfo(QName name, Object payload) {
        payloadMap.put(name, payload);
    }

    private JAXBRIContext createJAXBContext() {
        final List<TypeReference> types = getAllTypeReferences();
        final Class[] cls = new Class[types.size()];
        final String ns = targetNamespace;
        int i = 0;
        for (TypeReference type : types) {
            cls[i++] = (Class) type.type;
        }
        try {
            //jaxbContext = JAXBRIContext.newInstance(cls, types, targetNamespace, false);
            // Need to avoid doPriv block once JAXB is fixed. Afterwards, use the above
            jaxbContext = (JAXBRIContext)
                 AccessController.doPrivileged(new PrivilegedExceptionAction() {
                     public java.lang.Object run() throws Exception {
                         return JAXBRIContext.newInstance(cls, types, ns, false);
                     }
                 });
            createBridgeMap(types);
        } catch (PrivilegedActionException e) {
            throw new WebServiceException(e.getMessage(), e.getException());
        }
        knownNamespaceURIs = new ArrayList<String>();
        for (String namespace : jaxbContext.getKnownNamespaceURIs()) {
            if (namespace.length() > 0) {
                if (!namespace.equals(SOAPNamespaceConstants.XSD))
                    knownNamespaceURIs.add(namespace);
            }
        }

        return jaxbContext;
    }

    /**
     * @return returns non-null list of TypeReference
     */
    private List<TypeReference> getAllTypeReferences() {
        List<TypeReference> types = new ArrayList<TypeReference>();
        Collection<JavaMethodImpl> methods = methodToJM.values();
        for (JavaMethodImpl m : methods) {
            fillTypes(m,types);
            fillFaultDetailTypes(m, types);
        }
        return types;
    }

    private void fillFaultDetailTypes(JavaMethod m, List<TypeReference> types) {
        for (CheckedException ce : m.getCheckedExceptions()) {
            types.add(ce.getDetailType());
//            addGlobalType(ce.getDetailType());
        }
    }

    protected void fillTypes(JavaMethodImpl m, List<TypeReference> types) {
        addTypes(m.requestParams, types);
        addTypes(m.responseParams, types);
    }

    private void addTypes(List<ParameterImpl> params, List<TypeReference> types) {
        for (ParameterImpl p : params) {
            types.add(p.getTypeReference());
        }
    }

    private void createBridgeMap(List<TypeReference> types) {
        for (TypeReference type : types) {
            Bridge bridge = jaxbContext.createBridge(type);
            bridgeMap.put(type, bridge);
        }
    }

    /**
     * @param qname
     * @return the <code>Method</code> for a given Operation <code>qname</code>
     */
    public Method getDispatchMethod(QName qname) {
        //handle the empty body
        if (qname == null)
            qname = emptyBodyName;
        JavaMethod jm = getJavaMethod(qname);
        if (jm != null) {
            return jm.getMethod();
        }
        return null;
    }

    /**
     * @param name
     * @param method
     * @return true if <code>name</code> is the name
     * of a known fault name for the <code>Method method</code>
     */
    public boolean isKnownFault(QName name, Method method) {
        JavaMethod m = getJavaMethod(method);
        for (CheckedException ce : m.getCheckedExceptions()) {
            if (ce.getDetailType().tagName.equals(name))
                return true;
        }
        return false;
    }

    /**
     * @param m
     * @param ex
     * @return true if <code>ex</code> is a Checked Exception
     * for <code>Method m</code>
     */
    public boolean isCheckedException(Method m, Class ex) {
        JavaMethod jm = getJavaMethod(m);
        for (CheckedException ce : jm.getCheckedExceptions()) {
            if (ce.getExcpetionClass().equals(ex))
                return true;
        }
        return false;
    }

    /**
     * @param method
     * @return the <code>JavaMethod</code> representing the <code>method</code>
     */
    public JavaMethod getJavaMethod(Method method) {
        return methodToJM.get(method);
    }

    /**
     * @param name
     * @return the <code>JavaMethod</code> associated with the
     * operation named name
     */
    public JavaMethod getJavaMethod(QName name) {
        return nameToJM.get(name);
    }

    /**
     * @param jm
     * @return the <code>QName</code> associated with the
     * JavaMethod jm
     */
    public QName getQNameForJM(JavaMethod jm) {
        for (QName key : nameToJM.keySet()) {
            JavaMethodImpl jmethod = nameToJM.get(key);
            if (jmethod.getOperationName().equals(((JavaMethodImpl)jm).getOperationName())){
               return key;
            }
        }
        return null;
    }

    /**
     * @return a <code>Collection</code> of <code>JavaMethods</code>
     * associated with this <code>RuntimeModel</code>
     */
    public final Collection<JavaMethodImpl> getJavaMethods() {
        return Collections.unmodifiableList(javaMethods);
    }

    void addJavaMethod(JavaMethodImpl jm) {
        if (jm != null)
            javaMethods.add(jm);
    }

    /**
     * Used from {@link com.sun.xml.ws.client.WSServiceDelegate#buildEndpointIFProxy(javax.xml.namespace.QName, Class)}}
     * to apply the binding information from WSDL after the model is created frm SEI class on the client side. On the server
     * side all the binding information is available before modeling and this method is not used.
     *
     * @param wsdlBinding
     * @deprecated To be removed once client side new architecture is implemented
     */
    public void applyParameterBinding(BoundPortTypeImpl wsdlBinding){
        if(wsdlBinding == null)
            return;
        if(wsdlBinding.isRpcLit())
            wsdlBinding.finalizeRpcLitBinding();
        for(JavaMethodImpl method : javaMethods){
            if(method.isAsync())
                continue;
            QName opName = new QName(wsdlBinding.getPortTypeName().getNamespaceURI());
            boolean isRpclit = method.getBinding().isRpcLit();
            List<ParameterImpl> reqParams = method.requestParams;
            List<ParameterImpl> reqAttachParams = null;
            for(Parameter param:reqParams){
                if(param.isWrapperStyle()){
                    if(isRpclit)
                        reqAttachParams = applyRpcLitParamBinding(method, (WrapperParameter)param, wsdlBinding, Mode.IN);
                    continue;
                }
                String partName = param.getPartName();
                if(partName == null)
                    continue;
                ParameterBinding paramBinding = wsdlBinding.getBinding(opName,
                        partName, Mode.IN);
                if(paramBinding != null)
                    ((ParameterImpl)param).setInBinding(paramBinding);
            }

            List<ParameterImpl> resAttachParams = null;
            List<ParameterImpl> resParams = method.responseParams;
            for(Parameter param:resParams){
                if(param.isWrapperStyle()){
                    if(isRpclit)
                        resAttachParams = applyRpcLitParamBinding(method, (WrapperParameter)param, wsdlBinding, Mode.OUT);
                    continue;
                }
                //if the parameter is not inout and its header=true then dont get binding from WSDL
//                if(!param.isINOUT() && param.getBinding().isHeader())
//                    continue;
                String partName = param.getPartName();
                if(partName == null)
                    continue;
                ParameterBinding paramBinding = wsdlBinding.getBinding(opName,
                        partName, Mode.OUT);
                if(paramBinding != null)
                    ((ParameterImpl)param).setOutBinding(paramBinding);
            }
            if(reqAttachParams != null){
                for(ParameterImpl p : reqAttachParams){
                    method.addRequestParameter(p);
                }
            }
            if(resAttachParams != null){
                for(ParameterImpl p : resAttachParams){
                    method.addResponseParameter(p);
                }
            }

        }
    }



    /**
     * Applies binding related information to the RpcLitPayload. The payload map is populated correctly.
     * @param method
     * @param wrapperParameter
     * @param boundPortType
     * @param mode
     * @return
     *
     * Returns attachment parameters if/any.
     */
    private List<ParameterImpl> applyRpcLitParamBinding(JavaMethodImpl method, WrapperParameter wrapperParameter, BoundPortType boundPortType, Mode mode) {
        QName opName = new QName(boundPortType.getPortTypeName().getNamespaceURI(), method.getOperationName());
        RpcLitPayload payload = new RpcLitPayload(wrapperParameter.getName());
        BoundOperation bo = boundPortType.get(opName);
        Map<Integer, ParameterImpl> bodyParams = new HashMap<Integer, ParameterImpl>();
        List<ParameterImpl> unboundParams = new ArrayList<ParameterImpl>();
        List<ParameterImpl> attachParams = new ArrayList<ParameterImpl>();
        for(ParameterImpl param : wrapperParameter.wrapperChildren){
            String partName = param.getPartName();
            if(partName == null)
                continue;

            ParameterBinding paramBinding = boundPortType.getBinding(opName,
                    partName, mode);
            if(paramBinding != null){
                if(mode == Mode.IN)
                    param.setInBinding(paramBinding);
                else if(mode == Mode.OUT)
                    param.setOutBinding(paramBinding);

                if(paramBinding.isUnbound()){
                        unboundParams.add(param);
                } else if(paramBinding.isAttachment()){
                    attachParams.add(param);
                }else if(paramBinding.isBody()){
                    if(bo != null){
                        Part p = bo.getPart(param.getPartName(), mode);
                        if(p != null)
                            bodyParams.put(p.getIndex(), param);
                        else
                            bodyParams.put(bodyParams.size(), param);
                    }else{
                        bodyParams.put(bodyParams.size(), param);
                    }
                }
            }

        }
        wrapperParameter.clear();
        for(int i = 0; i <  bodyParams.size();i++){
            ParameterImpl p = bodyParams.get(i);
            wrapperParameter.addWrapperChild(p);
            if(((mode == Mode.IN) && p.getInBinding().isBody())||
                    ((mode == Mode.OUT) && p.getOutBinding().isBody())){
                JAXBBridgeInfo bi = new JAXBBridgeInfo(p.getBridge(), null);
                payload.addParameter(bi);
            }
        }

        for(Parameter p : attachParams){
            JAXBBridgeInfo bi = new JAXBBridgeInfo(p.getBridge(), null);
            payloadMap.put(p.getName(), bi);
        }

        //add unbounded parts
        for(ParameterImpl p:unboundParams){
            wrapperParameter.addWrapperChild(p);
        }
        payloadMap.put(wrapperParameter.getName(), payload);
        return attachParams;
    }


    /**
     * @param name
     * @param jm
     */
    void put(QName name, JavaMethodImpl jm) {
        nameToJM.put(name, jm);
    }

    /**
     * @param method
     * @param jm
     */
    void put(Method method, JavaMethodImpl jm) {
        methodToJM.put(method, jm);
    }

    public String getWSDLLocation() {
        return wsdlLocation;
    }

    void setWSDLLocation(String location) {
        wsdlLocation = location;
    }

    public QName getServiceQName() {
        return serviceName;
    }

    public QName getPortName() {
        return portName;
    }

    public QName getPortTypeName() {
        return portTypeName;
    }

    void setServiceQName(QName name) {
        serviceName = name;
    }

    void setPortName(QName name) {
        portName = name;
    }

    void setPortTypeName(QName name) {
        portTypeName = name;
    }

    /**
     * This is the targetNamespace for the WSDL containing the PortType
     * definition
     */
    void setTargetNamespace(String namespace) {
        targetNamespace = namespace;
    }

    /**
     * This is the targetNamespace for the WSDL containing the PortType
     * definition
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * Mtom processing is disabled by default. To enable it the RuntimeModel creator must call it to enable it.
     * Its used by {@link com.sun.xml.ws.server.RuntimeEndpointInfo#init()} after the runtime model is built. This method should not be exposed to outside.
     *
     * This needs to be changed - since this information is available to {@link com.sun.xml.ws.server.RuntimeEndpointInfo}
     * before building the model it should be passed on to {@link com.sun.xml.ws.model.RuntimeModeler#buildRuntimeModel()}.
     *
     * @param enableMtom
     * @deprecated
     */
    public void enableMtom(boolean enableMtom){
        this.enableMtom = enableMtom;
    }

    /**
     * This will no longer be needed with the new architecture
     * @return
     * @deprecated
     */
    public Map<Integer, RawAccessor> getRawAccessorMap() {
        return rawAccessorMap;
    }

    /**
     * This method creates the decoder info that
     * @deprecated
     */
    protected abstract void createDecoderInfo();

    private boolean enableMtom = false;
    private ThreadLocal<BridgeContext> bridgeContext = new ThreadLocal<BridgeContext>();
    protected JAXBRIContext jaxbContext;
    private String wsdlLocation;
    private QName serviceName;
    private QName portName;
    private QName portTypeName;
    private Map<Method,JavaMethodImpl> methodToJM = new HashMap<Method, JavaMethodImpl>();
    private Map<QName,JavaMethodImpl> nameToJM = new HashMap<QName, JavaMethodImpl>();
    private List<JavaMethodImpl> javaMethods = new ArrayList<JavaMethodImpl>();
    private final Map<TypeReference, Bridge> bridgeMap = new HashMap<TypeReference, Bridge>();
    private final Map<QName, Object> payloadMap = new HashMap<QName, Object>();
    protected final QName emptyBodyName = new QName("");
    private String targetNamespace = "";
    private final Map<Integer, RawAccessor> rawAccessorMap = new HashMap<Integer, RawAccessor>();
    private List<String> knownNamespaceURIs = null;
}