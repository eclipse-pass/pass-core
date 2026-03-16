/*
 *
 * Copyright 2019 Johns Hopkins University
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

import java.io.IOException;
import java.io.Writer;

import com.yahoo.elide.RefreshableElide;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class defines DOI service endpoints and orchestrates responses
 *
 * @author jrm
 */
@RestController
public class PassDoiServiceController {
    private static final Logger LOG = LoggerFactory.getLogger(PassDoiServiceController.class);

    private final ElideConnector elideConnector;
    private final ExternalDoiServiceConnector externalDoiServiceConnector;
    private final ExternalDoiService xrefDoiService;
    private final ExternalDoiService unpaywallDoiService;

    /**
     * @param refreshableElide the RefreshableElide
     */
    @Autowired
    public PassDoiServiceController(RefreshableElide refreshableElide) {
        this.elideConnector = new ElideConnector(refreshableElide);
        this.externalDoiServiceConnector = new ExternalDoiServiceConnector();
        this.xrefDoiService = new XrefDoiService();
        this.unpaywallDoiService = new UnpaywallDoiService();
    }

    /**
     * Constructor for testing, allows injection of mock connectors and services.
     *
     * @param elideConnector
     * @param externalDoiServiceConnector
     * @param xrefDoiService
     * @param unpaywallDoiService
     */
    PassDoiServiceController(ElideConnector elideConnector,
                             ExternalDoiServiceConnector externalDoiServiceConnector,
                             ExternalDoiService xrefDoiService,
                             ExternalDoiService unpaywallDoiService) {
        this.elideConnector = elideConnector;
        this.externalDoiServiceConnector = externalDoiServiceConnector;
        this.xrefDoiService = xrefDoiService;
        this.unpaywallDoiService = unpaywallDoiService;
    }

    /**
     * This method handles GET requests to resolve a given DOI to a journal metadata
     *
     * @param request the HTTP Request with the DOI to resolve for a journal
     * @param response the HTTP Response with the journal metadata
     * @throws IOException if there is an error writing the response
     */
    @GetMapping("/doi/journal")
    protected void getXrefMetadata(HttpServletRequest request, HttpServletResponse response)
        throws IOException {

        ExternalDoiService externalService = xrefDoiService;

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        //we will call out to crossref and collect the work JSON object
        //the value of this parameter is expected to be already URIencoded
        String doi = externalService.verify(request.getParameter("doi"));

        //stage 1: verify doi is valid
        if (doi == null) {
            // do not have have a valid xref doi
            try (Writer out = response.getWriter()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", "Supplied DOI is not in valid DOI format.")
                                            .build();
                out.write(jsonObject.toString());
                response.setStatus(400);
                return;
            }
        }

        //stage 2: try to get crossref record, catch errors first, and halt processing
        JsonObject xrefJsonObject = externalDoiServiceConnector.retrieveMetadata(doi, externalService);

        if (xrefJsonObject == null) {
            try (Writer out = response.getWriter()) {
                String message = "There was an error getting the metadata from " +
                                 externalService.name() + " for " + doi;
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString());
                response.setStatus(500);
            }
        } else if (xrefJsonObject.containsKey("error")) {
            int responseCode = xrefJsonObject.getInt(ExternalDoiServiceConnector.HTTP_STATUS_CODE);
            String message;

            if (responseCode == 404) {
                message = "The resource for DOI " + doi + " could not be found on " + externalService.name() + ".";
            } else {
                message = "A record for this resource could not be returned from " + externalService.name() + ": " +
                        xrefJsonObject.getJsonString("error");
            }

            try (Writer out = response.getWriter()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString());
                response.setStatus(responseCode);
                LOG.warn(message);
            }
        } else {
            // have a non-empty string to process
            String journalId = elideConnector.resolveJournal(xrefJsonObject);
            if (journalId != null) {

                try (Writer out = response.getWriter()) {
                    JsonObject jsonObject = Json.createObjectBuilder()
                                                .add("journal-id", journalId)
                                                .add("crossref", externalService.processObject(xrefJsonObject))
                                                .build();

                    out.write(jsonObject.toString());
                    response.setStatus(200);
                }

            } else {
                // journal id is null - this should never happen unless Crosssref journal is insufficient
                // for example, if a book doi ws supplied which has no issns
                try (Writer out = response.getWriter()) {
                    String message = "Insufficient information to locate or specify a journal entry.";
                    JsonObject jsonObject = Json.createObjectBuilder()
                                                .add("error", message)
                                                .build();
                    out.write(jsonObject.toString());
                    response.setStatus(422);
                    LOG.warn(message);
                }
            }
        }
    }

    /**
     * This method handles GET requests to retrieve a manuscript from Unpaywall REST API for a given DOI. The response
     * is a JSON object containing the manuscript metadata.
     *
     * @param request The HTTP request containing the DOI to be resolved
     * @param response The HTTP response containing the JSON object of the resolved DOI to manuscript metadata
     * @throws IOException if there is an error writing the response
     */
    @GetMapping("/doi/manuscript")
    protected void getUnpaywallMetadata(HttpServletRequest request, HttpServletResponse response)
        throws IOException {

        ExternalDoiService externalService = unpaywallDoiService;

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        //we will call out to unpaywall and collect the JSON object
        //the value of this parameter is expected to be already URIencoded
        String doi = externalService.verify(request.getParameter("doi"));

        //stage 1: verify doi is valid
        if (doi == null) {
            // do not have have a valid doi
            try (Writer out = response.getWriter()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", "Supplied DOI is not in valid DOI format.")
                                            .build();
                out.write(jsonObject.toString());
                response.setStatus(400);
                return;
            }
        }

        //stage 2: try to get unpaywall record, catch errors first, and halt processing
        JsonObject unpaywallJsonObject = externalDoiServiceConnector.retrieveMetadata(doi, externalService);
        if (unpaywallJsonObject == null) {
            try (Writer out = response.getWriter()) {
                String message = "There was an error getting the metadata from " +
                                 externalService.name() + " for " + doi;
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString());
                response.setStatus(500);
            }
        } else if (unpaywallJsonObject.containsKey("error")) {
            int responseCode = unpaywallJsonObject.getInt(ExternalDoiServiceConnector.HTTP_STATUS_CODE);
            String message = "A record for this resource could not be returned from Unpaywall: " +
                    unpaywallJsonObject.getJsonString("error");

            try (Writer out = response.getWriter()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString());
                response.setStatus(responseCode);
                LOG.warn(message);
            }
        } else {
            // have a non-empty JSON string to process
            try (Writer out = response.getWriter()) {
                JsonObject jsonObject = externalService.processObject(unpaywallJsonObject);
                out.write(jsonObject.toString());
                response.setStatus(200);
            }
        }
    }
}