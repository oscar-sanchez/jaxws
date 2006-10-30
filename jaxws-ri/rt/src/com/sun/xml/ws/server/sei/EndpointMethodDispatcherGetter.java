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

import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.model.AbstractSEIModelImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets the list of {@link EndpointMethodDispatcher}s for {@link SEIInvokerTube}.
 * a request {@link Packet}. If WS-Addressing is enabled on the endpoint, then
 * only {@link ActionBasedDispatcher} is added to the list. Otherwise,
 * {@link PayloadQNameBasedDispatcher} is added to the list.
 *
 * <p>
 * {@link Message} payload's QName to obtain the handler. If no handler is
 * registered corresponding to that QName, then uses Action Message
 * Addressing Property value to get the handler. 
 *
 * @author Arun Gupta
 */
final class EndpointMethodDispatcherGetter {
    private final List<EndpointMethodDispatcher> dispatcherList;

    EndpointMethodDispatcherGetter(AbstractSEIModelImpl model, WSBinding binding, SEIInvokerTube invokerTube) {
        dispatcherList = new ArrayList<EndpointMethodDispatcher>();

        if (AddressingVersion.fromBinding(binding) != null) {
            dispatcherList.add(new ActionBasedDispatcher(model, binding, invokerTube));
        } else {
            dispatcherList.add(new PayloadQNameBasedDispatcher(model, binding, invokerTube));
        }
    }

    List<EndpointMethodDispatcher> getDispatcherList() {
        return dispatcherList;
    }
}
