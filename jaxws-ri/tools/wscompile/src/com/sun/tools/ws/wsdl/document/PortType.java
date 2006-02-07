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

package com.sun.tools.ws.wsdl.document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import com.sun.tools.ws.wsdl.framework.Defining;
import com.sun.tools.ws.wsdl.framework.Entity;
import com.sun.tools.ws.wsdl.framework.EntityAction;
import com.sun.tools.ws.wsdl.framework.ExtensibilityHelper;
import com.sun.tools.ws.api.wsdl.TExtensible;
import com.sun.tools.ws.api.wsdl.TExtension;
import com.sun.tools.ws.wsdl.framework.ExtensionImpl;
import com.sun.tools.ws.wsdl.framework.GlobalEntity;
import com.sun.tools.ws.wsdl.framework.Kind;
import com.sun.tools.ws.wsdl.framework.ValidationException;

/**
 * Entity corresponding to the "portType" WSDL element.
 *
 * @author WS Development Team
 */
public class PortType extends GlobalEntity implements TExtensible {

    public PortType(Defining defining) {
        super(defining);
        _operations = new ArrayList();
        _operationKeys = new HashSet();
        _helper = new ExtensibilityHelper();
    }

    public void add(Operation operation) {
        String key = operation.getUniqueKey();
        if (_operationKeys.contains(key))
            throw new ValidationException(
                "validation.ambiguousName",
                operation.getName());
        _operationKeys.add(key);
        _operations.add(operation);
    }

    public Iterator operations() {
        return _operations.iterator();
    }

    public Set getOperationsNamed(String s) {
        Set result = new HashSet();
        for (Iterator iter = _operations.iterator(); iter.hasNext();) {
            Operation operation = (Operation) iter.next();
            if (operation.getName().equals(s)) {
                result.add(operation);
            }
        }
        return result;
    }

    public Kind getKind() {
        return Kinds.PORT_TYPE;
    }

    public QName getElementName() {
        return WSDLConstants.QNAME_PORT_TYPE;
    }

    public Documentation getDocumentation() {
        return _documentation;
    }

    public void setDocumentation(Documentation d) {
        _documentation = d;
    }

    public void withAllSubEntitiesDo(EntityAction action) {
        super.withAllSubEntitiesDo(action);

        for (Iterator iter = _operations.iterator(); iter.hasNext();) {
            action.perform((Entity) iter.next());
        }
        _helper.withAllSubEntitiesDo(action);
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        visitor.preVisit(this);
        _helper.accept(visitor);
        for (Iterator iter = _operations.iterator(); iter.hasNext();) {
            ((Operation) iter.next()).accept(visitor);
        }
        visitor.postVisit(this);
    }

    public void validateThis() {
        if (getName() == null) {
            failValidation("validation.missingRequiredAttribute", "name");
        }
    }

    public String getNameValue() {
        return getName();
    }

    public String getNamespaceURI() {
        return getDefining().getTargetNamespaceURI();
    }

    public QName getWSDLElementName() {
        return getElementName();
    }

    /* (non-Javadoc)
    * @see TExtensible#addExtension(ExtensionImpl)
    */
    public void addExtension(TExtension e) {
        _helper.addExtension(e);

    }

    /* (non-Javadoc)
     * @see TExtensible#extensions()
     */
    public Iterable<TExtension> extensions() {
        return _helper.extensions();
    }

    public TExtensible getParent() {
        return parent;
    }

    public void setParent(TExtensible parent) {
        this.parent = parent;
    }

    private TExtensible parent;
    private Documentation _documentation;
    private List _operations;
    private Set _operationKeys;
    private ExtensibilityHelper _helper;
}
