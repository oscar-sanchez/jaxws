package com.sun.xml.ws.sandbox.server;

import com.sun.xml.ws.api.model.wsdl.WSDLModel;

/**
 * Root of the unparsed WSDL and other resources referenced from it.
 * This object represents the description of the service
 * that a {@link WSEndpoint} offers.
 *
 * <p>
 * A description consists of a set of {@link SDDocument}, which
 * each represents a single XML document that forms a part of the
 * descriptor (for example, WSDL might refer to separate schema documents,
 * or a WSDL might refer to another WSDL.)
 *
 * <p>
 * {@link ServiceDefinition} and its descendants are immutable
 * read-only objects. Once they are created, they always return
 * the same value.
 *
 * <h2>Expected Usage</h2>
 * <p>
 * This object is intended to be used for serving the descriptors
 * to remote clients (such as by MEX, or other protocol-specific
 * metadata query, such as HTTP GET with "?wsdl" query string.)
 *
 * <p>
 * This object is <b>NOT</b> intended to be used by other
 * internal components to parse them. For that purpose, use
 * {@link WSDLModel} instead.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ServiceDefinition extends Iterable<SDDocument> {
    /**
     * Gets the "primary" {@link SDDocument} that represents a WSDL.
     *
     * <p>
     * This WSDL eventually refers to all the other {@link SDDocument}s.
     *
     * @return
     *      always non-null.
     */
    SDDocument getPrimary();
}
