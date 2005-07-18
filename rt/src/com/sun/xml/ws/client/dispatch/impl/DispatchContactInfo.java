/*
 * $Id: DispatchContactInfo.java,v 1.4 2005-07-18 16:52:07 kohlert Exp $
 */

/*
* Copyright (c) 2004 Sun Microsystems, Inc.
* All rights reserved.
*/
package com.sun.xml.ws.client.dispatch.impl;

import com.sun.pept.encoding.Decoder;
import com.sun.pept.encoding.Encoder;
import com.sun.pept.protocol.MessageDispatcher;
import com.sun.pept.transport.Connection;
import com.sun.xml.ws.client.ContactInfoBase;

/**
 * @author WS Development Team
 */
public class DispatchContactInfo extends ContactInfoBase {

    public DispatchContactInfo(Connection connection,
                               MessageDispatcher messageDispatcher,
                               Encoder encoder,
                               Decoder decoder,
                               String bindingId) {
        super(connection, messageDispatcher, encoder, decoder, bindingId);
    }

    public DispatchContactInfo() {
        super();
    }
}
