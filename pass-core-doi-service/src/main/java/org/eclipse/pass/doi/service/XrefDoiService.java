/*
 *
 * Copyright 2022 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.eclipse.pass.doi.service;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The XrefDoiService class is an implementation of the ExternalDoiService abstract class to interface with the Crossref
 * API. The Crossref API is a RESTful API that returns JSON metadata for a given DOI.
 * <p>
 * The Crossref API is documented here: <a href="https://api.crossref.org/">Crossref API</a>
 * <p>
 * The Crossref API requires a User-Agent header to be set on the request. The value of this header must be an email
 * address. The default email address used by is pass@jhu.edu and can be overridden by setting the environment variable
 * PASS_DOI_SERVICE_MAILTO
 */
public class XrefDoiService extends ExternalDoiService {
    private final static String XREF_BASEURI = "https://api.crossref.org/v1/works/";
    private final static Logger LOG = LoggerFactory.getLogger(XrefDoiService.class);

    private final Templates jatsTemplates;

    /**
     * Constructor for XrefDoiService.
     */
    public XrefDoiService() {
        Source xsltSource = new StreamSource(XrefDoiService.class.getResourceAsStream("/jats-to-html.xsl"));

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setURIResolver((href, base) -> {
            InputStream is = XrefDoiService.class.getResourceAsStream("/" + href);

            if (is == null) {
                return null;
            }

            return new StreamSource(is);
        });

        try {
            jatsTemplates = transformerFactory.newTemplates(xsltSource);
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException("Failed to configure JATS XSLT", e);
        }
    }

    @Override
    public String name() {
        return "Crossref";
    }

    @Override
    public String baseUrl() {
        return System.getenv("XREF_BASEURI") != null ? System.getenv(
            "XREF_BASEURI") : XREF_BASEURI;
    }

    @Override
    public HashMap<String, String> parameterMap() {
        return null;
    }

    @Override
    public HashMap<String, String> headerMap() {
        HashMap<String, String> headerMap = new HashMap<>();
        String agent = System.getenv("PASS_DOI_SERVICE_MAILTO") != null ? System.getenv(
            "PASS_DOI_SERVICE_MAILTO") : MAILTO;
        headerMap.put("User-Agent", agent);
        return headerMap;
    }

    private boolean isJats(String s) {
        return s != null && s.contains("<jats:");
    }

    // Crossref requires the jats prefix on abstract elements which must be stripped.
    // Make sure there is a root element and a mml prefix defined for MathML
    private String normalizeJats(String s) {
        s = s.replace("jats:", "");

        return "<sec xmlns:mml=\"http://www.w3.org/1998/Math/MathML\">" + s + "</sec>";
    }

    private String getAbstract(JsonObject object) {
        JsonObject message = object.getJsonObject("message");

        if (message != null) {
            return message.getString("abstract", null);
        }

        return null;
    }

    @Override
    public JsonObject processObject(JsonObject object) {
        String abstract_value = getAbstract(object);

        if (!isJats(abstract_value)) {
            return object;
        }

        String jats = normalizeJats(abstract_value);
        Source xmlSource = new StreamSource(new StringReader(jats));

        try {
            Transformer transformer = jatsTemplates.newTransformer();

            StringWriter output = new StringWriter();
            transformer.transform(xmlSource, new StreamResult(output));

            // Replace the abstract with the new value
            return Json.createObjectBuilder(object).add("message",
                   Json.createObjectBuilder(object.getJsonObject("message")).
                       add("abstract", output.toString())).build();
        } catch (TransformerException e) {
            LOG.error("Failed to transform JATS abstract", e);

            return object;
        }
    }
}
