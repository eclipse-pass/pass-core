package org.eclipse.pass.doi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class XrefDoiServiceTest {
    private final XrefDoiService xrefDoiService = new XrefDoiService();

    private JsonObject constructMessageWithAbstract(String abstract_value) {
        return Json.createObjectBuilder().add("message",
                Json.createObjectBuilder().add("abstract",  abstract_value).build()).build();
    }

    private String getAbstract(JsonObject message) {
        return message.getJsonObject("message").getString("abstract");
    }

    private String getResource(String path) throws IOException {
        try (InputStream is = XrefDoiServiceTest.class.getResourceAsStream(path)) {
            return IOUtils.toString(is, "utf-8");
        }
    }

    @Test
    public void testTransformJatsAbstract() throws IOException {
        JsonObject result = xrefDoiService.processObject(constructMessageWithAbstract(
                getResource("/jats_abstract.xml")));

        assertEquals(getResource("/jats_abstract.html"), getAbstract(result));
    }

    @Test
    public void testTextAbstract() {
        JsonObject message = constructMessageWithAbstract("This is an extremely interesting abstract. &amp; <blah");
        JsonObject result = xrefDoiService.processObject(message);

        assertEquals(getAbstract(message), getAbstract(result));
    }

    @Test
    public void testJatsMathmlAbstract() throws IOException {
        JsonObject result = xrefDoiService.processObject(constructMessageWithAbstract(
                getResource("/jats_abstract_mathml.xml")));

        assertEquals(getResource("/jats_abstract_mathml.html"), getAbstract(result));
    }

    @Test
    public void testMalformedJatsAbstract() {
        JsonObject message = constructMessageWithAbstract("<jats:pThis is malformed JATS.</jats:p>");
        JsonObject result = xrefDoiService.processObject(message);

        assertEquals(getAbstract(message), getAbstract(result));
    }
}
