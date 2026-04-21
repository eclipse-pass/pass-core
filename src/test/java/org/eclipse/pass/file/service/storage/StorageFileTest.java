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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorageFileTest {

    @Test
    void testDefaultConstructor_setsDefaultValues() {
        StorageFile file = new StorageFile();
        assertEquals("0", file.getId());
        assertEquals("0", file.getFileName());
        assertEquals("0", file.getMimeType());
        assertEquals("0", file.getStorageType());
        assertEquals(0L, file.getSize());
        assertEquals("0", file.getExtension());
        assertNull(file.getUuid());
    }

    @Test
    void testParameterizedConstructor_setsAllFields() {
        StorageFile file = new StorageFile("1", "uuid-123", "test.pdf", "application/pdf", "FILE_SYSTEM", 1024L, "pdf");
        assertEquals("1", file.getId());
        assertEquals("uuid-123", file.getUuid());
        assertEquals("test.pdf", file.getFileName());
        assertEquals("application/pdf", file.getMimeType());
        assertEquals("FILE_SYSTEM", file.getStorageType());
        assertEquals(1024L, file.getSize());
        assertEquals("pdf", file.getExtension());
    }

    @Test
    void testSetters_updateFieldsCorrectly() {
        StorageFile file = new StorageFile();
        file.setId("42");
        file.setUuid("uuid-456");
        file.setFileName("document.docx");
        file.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        file.setStorageType("S3");
        file.setSize(2048L);
        file.setExtension("docx");

        assertEquals("42", file.getId());
        assertEquals("uuid-456", file.getUuid());
        assertEquals("document.docx", file.getFileName());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", file.getMimeType());
        assertEquals("S3", file.getStorageType());
        assertEquals(2048L, file.getSize());
        assertEquals("docx", file.getExtension());
    }

    @Test
    void testEquals_sameFields_returnsTrue() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        StorageFile b = new StorageFile("1", "uuid-2", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        assertEquals(a, b);
    }

    @Test
    void testEquals_sameInstance_returnsTrue() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        assertEquals(a, a);
    }

    @Test
    void testEquals_differentId_returnsFalse() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        StorageFile b = new StorageFile("2", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        assertNotEquals(a, b);
    }

    @Test
    void testEquals_differentFileName_returnsFalse() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        StorageFile b = new StorageFile("1", "uuid-1", "other.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        assertNotEquals(a, b);
    }

    @Test
    void testEquals_differentMimeType_returnsFalse() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        StorageFile b = new StorageFile("1", "uuid-1", "file.txt", "application/pdf", "FILE_SYSTEM", 100L, "txt");
        assertNotEquals(a, b);
    }

    @Test
    void testEquals_differentSize_returnsFalse() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        StorageFile b = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 200L, "txt");
        assertNotEquals(a, b);
    }

    @Test
    void testEquals_null_returnsFalse() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        assertNotEquals(null, a);
    }

    @Test
    void testEquals_differentClass_returnsFalse() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        assertNotEquals("not a StorageFile", a);
    }

    @Test
    void testHashCode_equalObjects_haveSameHashCode() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        StorageFile b = new StorageFile("1", "uuid-2", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testHashCode_differentObjects_likelyDifferentHashCode() {
        StorageFile a = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        StorageFile b = new StorageFile("2", "uuid-1", "other.pdf", "application/pdf", "S3", 999L, "pdf");
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testToString_containsAllFields() {
        StorageFile file = new StorageFile("1", "uuid-1", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        String result = file.toString();
        assertNotNull(result);
        assertTrue(result.contains("1"));
        assertTrue(result.contains("file.txt"));
        assertTrue(result.contains("text/plain"));
        assertTrue(result.contains("FILE_SYSTEM"));
        assertTrue(result.contains("100"));
        assertTrue(result.contains("txt"));
    }

    @Test
    void testToString_nullField_containsNullString() {
        StorageFile file = new StorageFile();
        file.setFileName(null);
        assertTrue(file.toString().contains("null"));
    }

    @Test
    void testEquals_uuidNotConsideredInEquality() {
        // uuid is intentionally excluded from equals/hashCode
        StorageFile a = new StorageFile("1", "uuid-AAA", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        StorageFile b = new StorageFile("1", "uuid-BBB", "file.txt", "text/plain", "FILE_SYSTEM", 100L, "txt");
        assertEquals(a, b);
    }
}
