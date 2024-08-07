/*
 * Copyright 2022 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.metadataschema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.elide.RefreshableElide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SchemaInstanceTest {

    private ObjectMapper map;

    @BeforeEach
    void setup() {
        map = new ObjectMapper();
    }

    /*
     * Sort schemas based on the following rules: If one schema is referenced by
     * another in a $ref, then that schema appears before the other For schemas that
     * are independent of one another, the one with the greatest number of form
     * properties appears before those that have fewer. If two schemas have no
     * dependencies and have the same number of properties, the one that appears
     * first in the initial list will be first in the result.
     */
    @Test
    void testSort() throws JsonProcessingException {
        String one = "{\r\n" + "        \"$id\": \"http://example.org/schemas/one.json\",\r\n"
                + "        \"definitions\": {\r\n" + "            \"form\": {\r\n"
                + "                \"properties\": {\r\n" + "                    \"foo\": \"bar\"\r\n"
                + "                }\r\n" + "            }\r\n" + "        }\r\n" + "    }";

        String two = "{\r\n" + "        \"$id\": \"http://example.org/schemas/two.json\",\r\n"
                + "        \"definitions\": {\r\n" + "            \"form\": {\r\n"
                + "                \"properties\": {\r\n"
                + "                    \"foo\": {\"$ref\": \"one.json#/definitions/form/properties/foo\"},\r\n"
                + "                    \"bar\": \"baz\",\r\n"
                + "                    \"baz\": {\"$ref\": \"#/definitions/form/properties/bar\"}\r\n"
                + "                }\r\n" + "            }\r\n" + "        }\r\n" + "    }";

        String three = "{\r\n" + "        \"$id\": \"http://example.org/schemas/three.json\",\r\n"
                + "        \"definitions\": {\r\n" + "            \"form\": {\r\n"
                + "                \"properties\": {\r\n"
                + "                    \"foo\": {\"$ref\": \"one.json#/definitions/form/properties/foo\"},\r\n"
                + "                    \"bar\": {\"$ref\": \"two.json#/definitions/form/properties/foo\"},\r\n"
                + "                    \"baz0\": \"value0\",\r\n" + "                    \"baz\": \"value\"\r\n"
                + "                }\r\n" + "            }\r\n" + "        }\r\n" + "    }";

        String four = "{\r\n" + "        \"$id\": \"http://example.org/schemas/four.json\",\r\n"
                + "        \"definitions\": {\r\n" + "            \"form\": {\r\n"
                + "                \"properties\": {\r\n"
                + "                    \"foo2\": {\"$ref\": \"one.json#/definitions/form/properties/foo\"},\r\n"
                + "                    \"bar2\": {\"$ref\": \"two.json#/definitions/form/properties/foo\"},\r\n"
                + "                    \"baz\": \"value\"\r\n" + "                }\r\n" + "            }\r\n"
                + "        }\r\n" + "    }";

        String five = "{\r\n" + "        \"$id\": \"http://example.org/schemas/five.json\",\r\n"
                + "        \"definitions\": {\r\n" + "            \"form\": {\r\n"
                + "                \"properties\": {\r\n" + "                    \"one\": 1,\r\n"
                + "                    \"two\": 2\r\n" + "                }\r\n" + "            }\r\n" + "        }\r\n"
                + "    }";

        String six = "{\r\n" + "        \"$id\": \"http://example.org/schemas/six.json\",\r\n"
                + "        \"definitions\": {\r\n" + "            \"form\": {\r\n"
                + "                \"properties\": {\r\n" + "                    \"one\": 1\r\n"
                + "                }\r\n" + "            }\r\n" + "        }\r\n" + "    }";

        String seven = "{\r\n" + "        \"$id\": \"http://example.org/schemas/seven.json\"\r\n" + "    }";

        SchemaInstance schema1 = new SchemaInstance(map.readTree(one));
        SchemaInstance schema2 = new SchemaInstance(map.readTree(two));
        SchemaInstance schema3 = new SchemaInstance(map.readTree(three));
        SchemaInstance schema4 = new SchemaInstance(map.readTree(four));
        SchemaInstance schema5 = new SchemaInstance(map.readTree(five));
        SchemaInstance schema6 = new SchemaInstance(map.readTree(six));
        SchemaInstance schema7 = new SchemaInstance(map.readTree(seven));

        ArrayList<SchemaInstance> toSort = new ArrayList<>(Arrays.asList(schema5, schema2,
                schema7, schema1, schema6, schema3, schema4));
        ArrayList<SchemaInstance> expected = new ArrayList<>(Arrays.asList(schema1, schema2,
                schema3, schema4, schema5, schema6, schema7));

        for (SchemaInstance s : toSort) {
            for (SchemaInstance k: toSort) {
                s.updateOrderDeps(k);
            }
        }
        Collections.sort(toSort);
        assertEquals(toSort, expected);
    }

    @Test
    void dereferenceTest() throws JsonProcessingException {
        String example_schema_json = "{\r\n" + "  \"$schema\": \"http://example.org/schema_to_dereference\",\r\n"
                + "  \"$id\": \"https://example.org/schemas/jhu/deref\",\r\n"
                + "  \"copySchemaName\": {\"$ref\": \"#/$schema\"},\r\n"
                + "  \"title\": {\"$ref\": \"schema1.json#/x/title\"},\r\n"
                + "  \"x\": {\"$ref\": \"schema2.json#/x\"},\r\n"
                + "  \"array\": {\"$ref\": \"schema3.json#/array\"},\r\n"
                + "  \"complexarray\": {\"$ref\": \"schema4.json#/complexarray\"},\r\n"
                + "  \"k\": {\"$ref\": \"schema4.json#/h/k\"}\r\n" + "}";

        String expected = "{\r\n" + "  \"$schema\": \"http://example.org/schema_to_dereference\",\r\n"
                + "  \"$id\": \"https://example.org/schemas/jhu/deref\",\r\n"
                + "  \"copySchemaName\": \"http://example.org/schema_to_dereference\",\r\n" + "  \"title\": \"X\",\r\n"
                + "  \"x\": {\r\n" + "    \"title\": \"x\",\r\n" + "    \"description\": \"an awesome letter\",\r\n"
                + "    \"$comment\": \"displays nicely\",\r\n" + "    \"type\": \"letter\"\r\n" + "  },\r\n"
                + "  \"array\": [\"c\", \"d\", \"e\"],\r\n" + "  \"complexarray\": [\"e\", \"f\", {\"g\": \"h\"}],\r\n"
                + "  \"k\": [\"l\", \"m\", \"m'\"]\r\n" + "}";

        SchemaInstance testSchema = new SchemaInstance(map.readTree(example_schema_json));
        SchemaInstance expectedSchema = new SchemaInstance(map.readTree(expected));
        SchemaFetcher schemaFetcher = new SchemaFetcher(Mockito.mock(RefreshableElide.class));
        testSchema.dereference(testSchema.getSchema(), schemaFetcher);
        assertEquals(expectedSchema.getSchema(), testSchema.getSchema());
    }

    @Test
    void dereferenceTest2() throws Exception {
        InputStream schemaDerefTest = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/schema_to_deref.json");

        InputStream expectedDeref = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/schema_to_deref_expected.json");

        SchemaInstance testSchema = new SchemaInstance(map.readTree(schemaDerefTest));
        SchemaInstance expectedSchema = new SchemaInstance(map.readTree(expectedDeref));
        SchemaFetcher schemaFetcher = new SchemaFetcher(Mockito.mock(RefreshableElide.class));
        testSchema.dereference(testSchema.getSchema(), schemaFetcher);
        assertEquals(expectedSchema.getSchema(), testSchema.getSchema());
    }

    @Test
    void dereferenceArrayObjTest() throws Exception {
        InputStream schemaDerefTest = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/deref_obj_array.json");

        InputStream expectedDeref = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/deref_obj_array_expected.json");

        SchemaInstance testSchema = new SchemaInstance(map.readTree(schemaDerefTest));
        SchemaInstance expectedSchema = new SchemaInstance(map.readTree(expectedDeref));
        SchemaFetcher schemaFetcher = new SchemaFetcher(Mockito.mock(RefreshableElide.class));
        testSchema.dereference(testSchema.getSchema(), schemaFetcher);
        assertEquals(expectedSchema.getSchema(), testSchema.getSchema());
    }

    @Test
    void dereferenceJscholarSimpleTest() throws Exception {
        InputStream jscholarSchemaJson = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/jscholarship_simple.json");

        InputStream jscholarExpected = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/jscholarship_simple_deref.json");

        SchemaInstance testSchema = new SchemaInstance(map.readTree(jscholarSchemaJson));
        SchemaInstance expectedSchema = new SchemaInstance(map.readTree(jscholarExpected));
        SchemaFetcher schemaFetcher = new SchemaFetcher(Mockito.mock(RefreshableElide.class));
        testSchema.dereference(testSchema.getSchema(), schemaFetcher);
        assertEquals(expectedSchema.getSchema(), testSchema.getSchema());
    }

    @Test
    void dereferenceJscholarTest() throws Exception {
        InputStream jscholarSchemaJson = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/jscholarship.json");

        InputStream jscholarExpected = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/jscholarship_deref.json");

        SchemaInstance testSchema = new SchemaInstance(map.readTree(jscholarSchemaJson));
        SchemaInstance expectedSchema = new SchemaInstance(map.readTree(jscholarExpected));
        SchemaFetcher schemaFetcher = new SchemaFetcher(Mockito.mock(RefreshableElide.class));
        testSchema.dereference(testSchema.getSchema(), schemaFetcher);
        assertEquals(expectedSchema.getSchema(), testSchema.getSchema());
    }

    @Test
    void dereferenceInvenioRDMTest() throws Exception {
        InputStream irdm_is = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/inveniordm.json");

        InputStream expected_is = SchemaInstanceTest.class
                .getResourceAsStream("/schemas/jhu/inveniordm_deref.json");

        SchemaInstance testSchema = new SchemaInstance(map.readTree(irdm_is));
        SchemaInstance expectedSchema = new SchemaInstance(map.readTree(expected_is));
        SchemaFetcher schemaFetcher = new SchemaFetcher(Mockito.mock(RefreshableElide.class));
        testSchema.dereference(testSchema.getSchema(), schemaFetcher);
        assertEquals(expectedSchema.getSchema(), testSchema.getSchema());
    }
}