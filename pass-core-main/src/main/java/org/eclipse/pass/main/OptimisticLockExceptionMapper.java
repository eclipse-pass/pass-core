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
package org.eclipse.pass.main;

import javax.annotation.Nullable;

import jakarta.persistence.OptimisticLockException;

import com.yahoo.elide.ElideErrorResponse;
import com.yahoo.elide.ElideErrors;
import com.yahoo.elide.core.exceptions.ErrorContext;
import com.yahoo.elide.core.exceptions.ExceptionMapper;
import org.springframework.stereotype.Component;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Component
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException, ElideErrors> {

    @Nullable
    @Override
    public ElideErrorResponse<ElideErrors> toErrorResponse(OptimisticLockException exception, ErrorContext errorContext) {
        return ElideErrorResponse.status(400)
            .errors(errors -> errors.error(error -> error.message(exception.getMessage())));
    }
}
