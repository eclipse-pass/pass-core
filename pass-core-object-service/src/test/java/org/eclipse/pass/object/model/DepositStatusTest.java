/*
 * Copyright 2025 Johns Hopkins University
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
package org.eclipse.pass.object.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
class DepositStatusTest {
    private static Stream<Arguments> provideStatuses() {
        return Stream.of(
            Arguments.of("accepted", DepositStatus.ACCEPTED),
            Arguments.of("rejected", DepositStatus.REJECTED),
            Arguments.of("failed", DepositStatus.FAILED),
            Arguments.of("submitted", DepositStatus.SUBMITTED),
            Arguments.of("retry", DepositStatus.RETRY)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStatuses")
    void testStatus(String depositStatus, DepositStatus expectedDepositStatus) {
        DepositStatus actualDepositStatus = DepositStatus.of(depositStatus);
        assertEquals(expectedDepositStatus, actualDepositStatus);
    }

    @Test
    void testNullStatus() {
        assertThrows(IllegalArgumentException.class, () -> {
            DepositStatus.of(null);
        });
    }
}
