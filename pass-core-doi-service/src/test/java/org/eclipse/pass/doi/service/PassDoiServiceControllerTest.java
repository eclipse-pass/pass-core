package org.eclipse.pass.doi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PassDoiServiceControllerTest {
    private PassDoiServiceController controller;
    private ElideConnector elideConnector;
    private ExternalDoiServiceConnector externalDoiServiceConnector;
    private ExternalDoiService xrefDoiService;
    private ExternalDoiService unpaywallDoiService;

    @BeforeEach
    public void setUp() {
        elideConnector = mock(ElideConnector.class);
        externalDoiServiceConnector = mock(ExternalDoiServiceConnector.class);
        xrefDoiService = mock(ExternalDoiService.class);
        unpaywallDoiService = mock(ExternalDoiService.class);

        controller = new PassDoiServiceController(
            elideConnector,
            externalDoiServiceConnector,
            xrefDoiService,
            unpaywallDoiService
        );
    }

    @Test
    public void testGetXrefMetadata_Success() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "10.1234/5678";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(xrefDoiService.verify(doi)).thenReturn(doi);

        JsonObject xrefJson = Json.createObjectBuilder().add("a", "b").build();
        JsonObject processedJson = Json.createObjectBuilder().add("processed", "true").build();

        when(externalDoiServiceConnector.retrieveMetadata(doi, xrefDoiService)).thenReturn(xrefJson);
        when(elideConnector.resolveJournal(xrefJson)).thenReturn("journal-1");
        when(xrefDoiService.processObject(xrefJson)).thenReturn(processedJson);

        controller.getXrefMetadata(request, response);

        verify(response).setStatus(200);
        verify(response).setContentType("application/json");

        JsonObject result = parseJson(stringWriter.toString());
        assertEquals("journal-1", result.getString("journal-id"));
        assertEquals(processedJson, result.getJsonObject("crossref"));
    }

    @Test
    public void testGetXrefMetadata_InvalidDoi() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "invalid-doi";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(xrefDoiService.verify(doi)).thenReturn(null);

        controller.getXrefMetadata(request, response);

        verify(response).setStatus(400);
        JsonObject result = parseJson(stringWriter.toString());
        assertEquals("Supplied DOI is not in valid DOI format.", result.getString("error"));
    }

    @Test
    public void testGetXrefMetadata_ServiceError_NullCheck() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "10.1234/5678";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(xrefDoiService.verify(doi)).thenReturn(doi);
        when(xrefDoiService.name()).thenReturn("Crossref");

        when(externalDoiServiceConnector.retrieveMetadata(doi, xrefDoiService)).thenReturn(null);

        controller.getXrefMetadata(request, response);

        verify(response).setStatus(500);
        JsonObject result = parseJson(stringWriter.toString());
        assertEquals("There was an error getting the metadata from Crossref for " + doi, result.getString("error"));
    }

    @Test
    public void testGetXrefMetadata_ServiceError_404() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "10.1234/5678";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(xrefDoiService.verify(doi)).thenReturn(doi);
        when(xrefDoiService.name()).thenReturn("Crossref");

        JsonObject errorJson = Json.createObjectBuilder()
                .add("error", "Not Found")
                .add(ExternalDoiServiceConnector.HTTP_STATUS_CODE, 404)
                .build();

        when(externalDoiServiceConnector.retrieveMetadata(doi, xrefDoiService)).thenReturn(errorJson);

        controller.getXrefMetadata(request, response);

        verify(response).setStatus(404);
        JsonObject result = parseJson(stringWriter.toString());
        assertEquals("The resource for DOI " + doi + " could not be found on Crossref.", result.getString("error"));
    }

    @Test
    public void testGetXrefMetadata_NoJournalId() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "10.1234/5678";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(xrefDoiService.verify(doi)).thenReturn(doi);

        JsonObject xrefJson = Json.createObjectBuilder().add("a", "b").build();

        when(externalDoiServiceConnector.retrieveMetadata(doi, xrefDoiService)).thenReturn(xrefJson);
        when(elideConnector.resolveJournal(xrefJson)).thenReturn(null);

        controller.getXrefMetadata(request, response);

        verify(response).setStatus(422);
        JsonObject result = parseJson(stringWriter.toString());
        assertEquals("Insufficient information to locate or specify a journal entry.", result.getString("error"));
    }

    @Test
    public void testGetUnpaywallMetadata_Success() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "10.1234/5678";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(unpaywallDoiService.verify(doi)).thenReturn(doi);

        JsonObject unpaywallJson = Json.createObjectBuilder().add("a", "b").build();
        JsonObject processedJson = Json.createObjectBuilder().add("processed", "true").build();

        when(externalDoiServiceConnector.retrieveMetadata(doi, unpaywallDoiService)).thenReturn(unpaywallJson);
        when(unpaywallDoiService.processObject(unpaywallJson)).thenReturn(processedJson);

        controller.getUnpaywallMetadata(request, response);

        verify(response).setStatus(200);
        JsonObject result = parseJson(stringWriter.toString());
        assertEquals(processedJson, result);
    }

    @Test
    public void testGetUnpaywallMetadata_InvalidDoi() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "invalid-doi";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(unpaywallDoiService.verify(doi)).thenReturn(null);

        controller.getUnpaywallMetadata(request, response);

        verify(response).setStatus(400);
        JsonObject result = parseJson(stringWriter.toString());
        assertEquals("Supplied DOI is not in valid DOI format.", result.getString("error"));
    }

    @Test
    public void testGetUnpaywallMetadata_ServiceError_Null() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "10.1234/5678";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(unpaywallDoiService.verify(doi)).thenReturn(doi);
        when(unpaywallDoiService.name()).thenReturn("Unpaywall");

        when(externalDoiServiceConnector.retrieveMetadata(doi, unpaywallDoiService)).thenReturn(null);

        controller.getUnpaywallMetadata(request, response);

        verify(response).setStatus(500);
        JsonObject result = parseJson(stringWriter.toString());
        assertEquals("There was an error getting the metadata from Unpaywall for " + doi, result.getString("error"));
    }

    @Test
    public void testGetUnpaywallMetadata_ServiceError_WithCode() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        String doi = "10.1234/5678";
        when(request.getParameter("doi")).thenReturn(doi);
        when(response.getWriter()).thenReturn(writer);
        when(unpaywallDoiService.verify(doi)).thenReturn(doi);

        JsonObject errorJson = Json.createObjectBuilder()
                .add("error", "Some Error")
                .add(ExternalDoiServiceConnector.HTTP_STATUS_CODE, 503)
                .build();

        when(externalDoiServiceConnector.retrieveMetadata(doi, unpaywallDoiService)).thenReturn(errorJson);

        controller.getUnpaywallMetadata(request, response);

        verify(response).setStatus(503);
        JsonObject result = parseJson(stringWriter.toString());
        assertTrue(result.getString("error").contains("Some Error"));
    }

    private JsonObject parseJson(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }
}