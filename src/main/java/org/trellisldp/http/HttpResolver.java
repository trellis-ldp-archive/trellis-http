/*
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
package org.trellisldp.http;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.client.ClientBuilder.newClient;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.BinaryService.MultipartUpload;
import org.trellisldp.spi.RuntimeRepositoryException;

/**
 * @author acoburn
 */
public class HttpResolver implements BinaryService.Resolver {

    private static final String HTTP_RESOLVER_NO_MULTIPART = "HTTP Resolver does not support multipart uploads";
    private static final String NON_NULL_IDENTIFIER = "Identifier may not be null!";

    private static final Logger LOGGER = getLogger(HttpResolver.class);

    // TODO - JDK9 use the new HttpClient library
    private final Client httpClient;

    /**
     * Create an HttpResolver using the default HTTP client
     */
    public HttpResolver() {
        this(newClient());
    }

    /**
     * Create an HttpResolver with a provided client
     * @param client the client
     */
    public HttpResolver(final Client client) {
        requireNonNull(client, "HTTP client may not be null!");
        httpClient = client;
    }

    @Override
    public List<String> getUriSchemes() {
        return asList("http", "https");
    }

    @Override
    public Boolean supportsMultipartUpload() {
        return false;
    }

    @Override
    public String initiateUpload(final String partition, final IRI identifier, final String mimeType) {
        throw new UnsupportedOperationException(HTTP_RESOLVER_NO_MULTIPART);
    }

    @Override
    public String uploadPart(final String identifier, final Integer partNumber, final InputStream content) {
        throw new UnsupportedOperationException(HTTP_RESOLVER_NO_MULTIPART);
    }

    @Override
    public MultipartUpload completeUpload(final String identifier, final Map<Integer, String> partDigests) {
        throw new UnsupportedOperationException(HTTP_RESOLVER_NO_MULTIPART);
    }

    @Override
    public void abortUpload(final String identifier) {
        throw new UnsupportedOperationException(HTTP_RESOLVER_NO_MULTIPART);
    }

    @Override
    public Stream<Map.Entry<Integer, String>> listParts(final String identifier) {
        throw new UnsupportedOperationException(HTTP_RESOLVER_NO_MULTIPART);
    }

    @Override
    public Boolean uploadSessionExists(final String identifier) {
        throw new UnsupportedOperationException(HTTP_RESOLVER_NO_MULTIPART);
    }

    @Override
    public Boolean exists(final String partition, final IRI identifier) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
        final Response res = httpClient.target(identifier.getIRIString()).request().head();
        final Boolean status = res.getStatusInfo().getFamily().equals(SUCCESSFUL);
        LOGGER.info("HTTP HEAD request to {} returned status {}", identifier, res.getStatus());
        res.close();
        return status;
    }

    @Override
    public Optional<InputStream> getContent(final String partition, final IRI identifier) {
        requireNonNull(identifier,  NON_NULL_IDENTIFIER);
        final Response res = httpClient.target(identifier.getIRIString()).request().get();
        LOGGER.info("HTTP GET request to {} returned status {}", identifier, res.getStatus());
        if (res.hasEntity()) {
            return of(res.getEntity()).map(x -> (InputStream) x);
        }
        return empty();
    }

    @Override
    public void setContent(final String partition, final IRI identifier, final InputStream stream,
            final Map<String, String> metadata) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
        final Response res = httpClient.target(identifier.getIRIString()).request().put(entity(stream,
                    ofNullable(metadata.get(CONTENT_TYPE)).map(MediaType::valueOf)
                        .orElse(APPLICATION_OCTET_STREAM_TYPE)));
        LOGGER.info("HTTP PUT request to {} returned {}", identifier, res.getStatusInfo());
        final Boolean ok = res.getStatusInfo().getFamily().equals(SUCCESSFUL);
        res.close();
        if (!ok) {
            throw new RuntimeRepositoryException("HTTP PUT request to " + identifier + " failed with a " +
                    res.getStatusInfo());
        }
    }

    @Override
    public void purgeContent(final String partition, final IRI identifier) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
        final Response res = httpClient.target(identifier.getIRIString()).request().delete();
        LOGGER.info("HTTP DELETE request to {} returned {}", identifier, res.getStatusInfo());
        final Boolean ok = res.getStatusInfo().getFamily().equals(SUCCESSFUL);
        res.close();
        if (!ok) {
            throw new RuntimeRepositoryException("HTTP DELETE request to " + identifier + " failed with a " +
                    res.getStatusInfo());

        }
    }
}
