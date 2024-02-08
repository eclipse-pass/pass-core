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
import org.eclipse.pass.object.model.SubmissionStatus;

/**
 * Converter class for SubmissionStatus. Converts SubmissionStatus to String and a String to SubmissionStatus.
 */
public class SubmissionStatusToStringConverter implements AttributeConverter<SubmissionStatus, String> {
    @Override
    public String convertToDatabaseColumn(SubmissionStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public SubmissionStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SubmissionStatus.of(dbData);
    }
}