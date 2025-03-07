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

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.eclipse.pass.main.SimpleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ActiveProfiles("test")
public class StorageConfigurationTest extends SimpleIntegrationTest {

    @Autowired private StorageProperties storageProperties;

    @Test
    public void testDefaultValuesFromConfiguration() {
        assertFalse(storageProperties.getStorageRootDir().isEmpty());
        assertFalse(storageProperties.getStorageRootDir().contains("#{null}"));
    }
}
