/*
 * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.policy.rules;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.eclipse.pass.policy.interfaces.PolicyResolver;

/**
 * Validator validates a serialized PolicyRules document against the implementation's expected JSON schema,
 * and then populates the DSL with the PolicyRules
 *
 * @author jrm
 */
public class Validator {

    public PolicyResolver validate(String filePath) throws IOException {

        DSL dsl;

        try (
            InputStream jsonStream = new FileInputStream(filePath);
            InputStream schemaStream = new FileInputStream("../pass-core-policy-service/src/main/resources/schemas/policy_config_1.0.json");
        ) {

            ObjectMapper objectMapper = new ObjectMapper();
            JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

            JsonNode jsonNode = objectMapper.readTree(jsonStream);
            JsonSchema schema = schemaFactory.getSchema(schemaStream);
            Set<ValidationMessage> validationResult = schema.validate(jsonNode);

            if (!validationResult.isEmpty()) { //validation errors
                throw new RuntimeException("There were validation errors");
            }

            dsl = objectMapper.convertValue(jsonNode, DSL.class);
            //for testing and verification
            //System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));

        } catch (IOException e) {
            throw e;
        }

        return dsl;
    }

}

