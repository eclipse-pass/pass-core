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
package org.eclipse.pass.file.service.storage;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import java.io.IOException;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The FileStorageServiceS3Test class is a test class for the FileStorageService that uses the S3 mock server. The S3
 * configuration is managed through application-test-s3.yml profile. The AWS access key and secret need to be set prior
 * to the Application Context initializing, and in addition, the S3 mock server needs to be started before the
 * Application Context. The S3 mock server is stopped and started prior to each test. This test extends the
 * FileStorageServiceTest class, which contains the tests that are common to all FileStorageService configurations.
 * @see FileStorageServiceTest
 * @see FileStorageService
 */
@ActiveProfiles("test-S3")
class FileStorageServiceS3Test extends FileStorageServiceTest {
    private static final DockerImageName LOCALSTACK_IMG =
        DockerImageName.parse("localstack/localstack:3.1.0");

    private static final LocalStackContainer localStack =
        new LocalStackContainer(LOCALSTACK_IMG)
            .withServices(S3);

    static {
        try {
            System.setProperty("aws.accessKeyId", "test");
            System.setProperty("aws.secretAccessKey", "test");
            setupS3();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("pass.file-service.s3-endpoint", () -> localStack.getEndpointOverride(S3).toString());
    }

    static void setupS3() throws IOException, InterruptedException {
        localStack.start();
        localStack.execInContainer("awslocal", "s3", "mb", "s3://pass-core-file-s3-it");
    }

}