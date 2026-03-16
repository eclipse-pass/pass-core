/*
 *
 * Copyright 2022 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.eclipse.pass.doi.service;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.stream.JsonParsingException;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class which manages the retrieval of JSON from external DOI services (Unpaywall, Crossref)
 *
 * @author jrm
 */
public class ExternalDoiServiceConnector {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalDoiServiceConnector.class);
    static final String HTTP_STATUS_CODE = "HTTP_STATUS_CODE";

    private final OkHttpClient client;

    ExternalDoiServiceConnector(OkHttpClient client) {
        this.client = client;
    }

    ExternalDoiServiceConnector() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(30, SECONDS);
        builder.readTimeout(30, SECONDS);
        builder.writeTimeout(30, SECONDS);
        this.client = builder.build();
    }

    /**
     * Consult external service to get a json object for a supplied doi.
     *
     * @param doi - the supplied doi string, prefix trimmed if necessary
     * @return a JSON object if successful,
     *      null if an error occurs interacting with the external service,
     *      {error: "error message", HTTP_STATUS_CODE: http status code} if
     *      the external service returns an error status code
     */
    JsonObject retrieveMetadata(String doi, ExternalDoiService service) {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(service.baseUrl() + doi)).newBuilder();

        if (service.parameterMap() != null) {
            for (String key : service.parameterMap().keySet()) {
                urlBuilder.addQueryParameter(key, service.parameterMap().get(key));
            }
        }

        Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());

        if (service.headerMap() != null) {
            requestBuilder.headers(Headers.of(service.headerMap()));
        }

        Request okHttpRequest =  requestBuilder.build();
        Call call = client.newCall(okHttpRequest);

        try (Response okHttpResponse = call.execute()) {
            if (okHttpResponse.isSuccessful()) {
                try (Reader reader = okHttpResponse.body().charStream();
                        JsonReader jsonReader = Json.createReader(reader)) {
                    return jsonReader.readObject();
                } catch (JsonParsingException e) {
                    LOG.error("Error parsing JSON of external service: " + okHttpRequest.url(), e);
                    return null;
                }
            }

            // Set response as the error field and save the status code.
            return Json.createObjectBuilder().add("error", okHttpResponse.body().string()).
                    add(HTTP_STATUS_CODE, Json.createValue(okHttpResponse.code())).build();
        } catch (IOException e) {
            LOG.error("Error accessing external service: " + okHttpRequest.url(), e);
            return null;
        }
    }
}

