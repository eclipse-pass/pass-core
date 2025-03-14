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
import java.io.OutputStream;

import com.yahoo.elide.RefreshableElide;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public PassDoiServiceController(RefreshableElide refreshableElide) {
        this.elideConnector = new ElideConnector(refreshableElide);
        this.externalDoiServiceConnector = new ExternalDoiServiceConnector();
        this.xrefDoiService = new XrefDoiService();
        this.unpaywallDoiService = new UnpaywallDoiService();
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
        String doi = request.getParameter("doi");

        //stage 1: verify doi is valid
        if (externalService.verify(doi) == null) {
            // do not have have a valid xref doi
            try (OutputStream out = response.getOutputStream()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", "Supplied DOI is not in valid DOI format.")
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(400);
                return;
            }
        }

        //Stage 2: make sure we don't already have a request being processed for this doi
        if (externalService.isAlreadyActive(doi)) {
            // return already processing error (429>)
            try (OutputStream out = response.getOutputStream()) {
                String message = "There is already an active request for " + doi;
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message + "; try again later.")
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(429);
                LOG.warn(message);
            }
        }

        //stage 3: try to get crossref record, catch errors first, and halt processing
        JsonObject xrefJsonObject = externalDoiServiceConnector.retrieveMetadata(doi, externalService);
        if (xrefJsonObject == null) {
            try (OutputStream out = response.getOutputStream()) {
                String message = "There was an error getting the metadata from " +
                                 externalService.name() + " for " + doi;
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(500);
                LOG.warn(message);
            }
        } else if (xrefJsonObject.getJsonString("error") != null) {
            int responseCode;
            String message;
            if (xrefJsonObject.getString("error").equals("Resource not found.")) {
                responseCode = 404;
                message = "The resource for DOI " + doi + " could not be found on " + externalService.name() + ".";
            } else {
                responseCode = 500;
                message = "A record for this resource could not be returned from " + externalService.name() + ": " +
                          xrefJsonObject.getJsonString("error");
            }
            try (OutputStream out = response.getOutputStream()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(responseCode);
                LOG.warn(message);
            }
        } else {
            // have a non-empty string to process
            String journalId = elideConnector.resolveJournal(xrefJsonObject);
            if (journalId != null) {

                try (OutputStream out = response.getOutputStream()) {
                    JsonObject jsonObject = Json.createObjectBuilder()
                                                .add("journal-id", journalId)
                                                .add("crossref", externalService.processObject(xrefJsonObject))
                                                .build();

                    out.write(jsonObject.toString().getBytes());
                    response.setStatus(200);
                }

            } else {
                // journal id is null - this should never happen unless Crosssref journal is insufficient
                // for example, if a book doi ws supplied which has no issns
                try (OutputStream out = response.getOutputStream()) {
                    String message = "Insufficient information to locate or specify a journal entry.";
                    JsonObject jsonObject = Json.createObjectBuilder()
                                                .add("error", message)
                                                .build();
                    out.write(jsonObject.toString().getBytes());
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
        String doi = request.getParameter("doi");

        //stage 1: verify doi is valid
        if (externalService.verify(doi) == null) {
            // do not have have a valid doi
            try (OutputStream out = response.getOutputStream()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", "Supplied DOI is not in valid DOI format.")
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(400);
                return;
            }
        }

        //Stage 2: make sure we don't already have a request being processed for this doi
        if (externalService.isAlreadyActive(doi)) {
            // return already processing error (429)
            try (OutputStream out = response.getOutputStream()) {
                String message = "There is already an active request for " + doi;
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message + "; try again later.")
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(429);
                LOG.warn(message);
            }
        }

        //stage 3: try to get unpaywall record, catch errors first, and halt processing
        JsonObject unpaywallJsonObject = externalDoiServiceConnector.retrieveMetadata(doi, externalService);
        if (unpaywallJsonObject == null) {
            try (OutputStream out = response.getOutputStream()) {
                String message = "There was an error getting the metadata from " +
                                 externalService.name() + " for " + doi;
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(500);
                LOG.warn(message);
            }
        } else if (
            unpaywallJsonObject.containsKey("error") &&
            unpaywallJsonObject.getValue("/error").toString().equals("true")
        ) {
            int responseCode;
            String message;

            if (unpaywallJsonObject.getValue("/HTTP_status_code") != null &&
                unpaywallJsonObject.getValue("/message") != null) {

                responseCode = Integer.parseInt(unpaywallJsonObject.getValue("/HTTP_status_code").toString());
                message = unpaywallJsonObject.getValue("/message").toString();
            } else {
                responseCode = 500;
                message = "A record for this resource could not be returned from Unpaywall: " +
                          unpaywallJsonObject.getJsonString("error");
            }

            try (OutputStream out = response.getOutputStream()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(responseCode);
                LOG.warn(message);
            }
        } else {
            // have a non-empty JSON string to process
            try (OutputStream out = response.getOutputStream()) {
                JsonObject jsonObject = externalService.processObject(unpaywallJsonObject);
                out.write(jsonObject.toString().getBytes());
                response.setStatus(200);
            }
        }
    }
}