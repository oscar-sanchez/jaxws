/*
 * $Id: JAXRPCContextListener.java,v 1.9 2005-07-16 01:38:41 kohlert Exp $
 */

/*
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.xml.ws.transport.http.servlet;
import com.sun.xml.ws.server.DocInfo;
import com.sun.xml.ws.server.RuntimeEndpointInfo;
import com.sun.xml.ws.server.WSDLPatcher;
import com.sun.xml.ws.server.WSDLPatcher.DOC_TYPE;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.sun.xml.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.ws.util.localization.Localizer;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * Parses sun-jaxws.xml and creates a <code>java.util.List</code> of 
 * RuntimeEndpointInfos. It also calls deploy() on each RuntimeEndpointInfo.
 *
 * @author WS Development Team
 */
public class JAXRPCContextListener
    implements ServletContextAttributeListener, ServletContextListener {

    /**
     * default contructor
     */
    public JAXRPCContextListener() {
        localizer = new Localizer();
        messageFactory =
            new LocalizableMessageFactory("com.sun.xml.ws.resources.jaxrpcservlet");
    }

    public void attributeAdded(ServletContextAttributeEvent event) {
    }

    public void attributeRemoved(ServletContextAttributeEvent event) {
    }

    public void attributeReplaced(ServletContextAttributeEvent event) {
    }

    public void contextDestroyed(ServletContextEvent event) {
        context = null;
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                localizer.localize(
                    messageFactory.getMessage("listener.info.destroy")));
        }
    }

    public void contextInitialized(ServletContextEvent event) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                localizer.localize(
                    messageFactory.getMessage("listener.info.initialize")));
        }
        context = event.getServletContext();
        classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }
        try {
            // Get all the WSDL & schema documents under WEB-INF/wsdl directory
            Map<String, DocInfo> docs = new HashMap<String, DocInfo>();
            collectDocs(context, JAXWS_WSDL_DIR, docs);
            logger.fine("war metadata="+docs);

            // Parse the descriptor file and build endpoint infos
            JAXRPCRuntimeInfoParser parser =
                new JAXRPCRuntimeInfoParser(classLoader);
            InputStream is = context.getResourceAsStream(JAXRPC_RI_RUNTIME);
            List<RuntimeEndpointInfo> endpoints = parser.parse(is);
            context.setAttribute(JAXRPCServlet.JAXRPC_RI_RUNTIME_INFO, endpoints);
            
            // Creates WSDL & schema metadata and runtime model
            createModelAndMetadata(endpoints, docs);

        } catch (Exception e) {
            logger.log(
                Level.SEVERE,
                localizer.localize(
                    messageFactory.getMessage(
                        "listener.parsingFailed",
                        e.toString())),
                e);
            context.removeAttribute(JAXRPCServlet.JAXRPC_RI_RUNTIME_INFO);
            throw new JAXRPCServletException("listener.parsingFailed", new Object[] {e});
        }
    }
    
    /*
     * Get all the WSDL & schema documents under WEB-INF/wsdl directory
     */
    private static void collectDocs(ServletContext context, String dirPath,
            Map<String, DocInfo> docs) {
        Set paths = context.getResourcePaths(dirPath);
        if (paths != null) {
            Iterator i = paths.iterator();
            while(i.hasNext()) {
                String docPath = (String)i.next();
                if (docPath.endsWith("/")) {
                    collectDocs(context, docPath, docs);
                } else {
                    docs.put(docPath, new ServletDocInfo(context, docPath));
                }
            }
        }
    }
    
    /*
     * updates metadata with query string and builds runtime model for each
     * endpoint
     */
    private void createModelAndMetadata(List<RuntimeEndpointInfo> endpoints,
            Map<String, DocInfo> docs)  throws UnsupportedEncodingException {
        
        for(RuntimeEndpointInfo endpoint : endpoints) {
            // Set queryString for the document
            Set<Entry<String, DocInfo>> entries = docs.entrySet();
            for(Entry<String, DocInfo> entry : entries) {
                ServletDocInfo docInfo = (ServletDocInfo)entry.getValue();
                String path = docInfo.getPath();
                String query = null;
                String queryValue = docInfo.getPath().substring(JAXWS_WSDL_DIR.length()+1);    // Without /WEB-INF/wsdl
                queryValue = URLEncoder.encode(queryValue, "UTF-8");
                InputStream in = docInfo.getDoc();
                DOC_TYPE docType = null;    // is WSDL or schema ??
                try {
                    docType = WSDLPatcher.getDocType(docInfo.getDoc());
                } catch (Exception e) {
                    continue;           // Not XML ?? Ignore this document
                } finally {
                    try { in.close(); } catch(IOException ie) {};
                }
                switch(docType) {
                    case WSDL :                   
                        query = path.equals(endpoint.getWSDLFileName())
                            ? "wsdl" : "wsdl="+queryValue;
                        break;
                    case SCHEMA : 
                        query = "xsd="+queryValue;
                        break;
                    case OTHER :
                        logger.warning(docInfo.getPath()+" is not a WSDL or Schema file.");
                }
                docInfo.setQueryString(query);
            }

            endpoint.setMetadata(docs);
            endpoint.deploy();
        }
    }

    private Localizer localizer;
    private LocalizableMessageFactory messageFactory;
    private ServletContext context;
    private ClassLoader classLoader;

    private static final String JAXRPC_RI_RUNTIME = "/WEB-INF/sun-jaxws.xml";
    public static final String JAXWS_WSDL_DIR = "/WEB-INF/wsdl";

    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.ws.util.Constants.LoggingDomain + ".server.http");
}
