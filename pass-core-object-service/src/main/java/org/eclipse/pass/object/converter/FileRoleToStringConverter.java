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
package org.eclipse.pass.object.converter;

import jakarta.persistence.AttributeConverter;
import org.eclipse.pass.object.model.FileRole;

/**
 * Converter class for FileRole. Converts FileRole to database column and to entity attribute.
 */
public class FileRoleToStringConverter implements AttributeConverter<FileRole, String> {
    @Override
    public String convertToDatabaseColumn(FileRole attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public FileRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FileRole.of(dbData);
    }
}