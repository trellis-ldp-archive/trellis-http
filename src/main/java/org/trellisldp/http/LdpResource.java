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

import static java.time.Instant.MAX;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static javax.ws.rs.core.Response.status;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.TIMEMAP;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.domain.HttpConstants.UPLOADS;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;

import com.codahale.metrics.annotation.Timed;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;

import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.http.domain.PATCH;
import org.trellisldp.http.impl.LdpDeleteHandler;
import org.trellisldp.http.impl.LdpGetHandler;
import org.trellisldp.http.impl.LdpOptionsHandler;
import org.trellisldp.http.impl.LdpPatchHandler;
import org.trellisldp.http.impl.LdpPostHandler;
import org.trellisldp.http.impl.LdpPutHandler;
import org.trellisldp.http.impl.MementoResource;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@Path("{partition}{path: .*}")
public class LdpResource extends BaseLdpResource {

    private static final Logger LOGGER = getLogger(LdpResource.class);

    protected final ResourceService resourceService;

    protected final IOService ioService;

    protected final BinaryService binaryService;

    protected final ConstraintService constraintService;

    protected final Collection<String> unsupportedTypes;

    /**
     * Create a LdpResource
     * @param resourceService the resource service
     * @param ioService the i/o service
     * @param constraintService the RDF constraint enforcing service
     * @param binaryService the datastream service
     * @param partitions a map of partitions for use with custom hostnames
     * @param unsupportedMediaTypes any unsupported media types
     */
    public LdpResource(final ResourceService resourceService, final IOService ioService,
            final ConstraintService constraintService, final BinaryService binaryService,
            final Map<String, String> partitions, final Collection<String> unsupportedMediaTypes) {
        super(partitions);
        this.resourceService = resourceService;
        this.ioService = ioService;
        this.binaryService = binaryService;
        this.constraintService = constraintService;
        this.unsupportedTypes = unsupportedMediaTypes;
    }

    /**
     * Perform a GET operation on an LDP Resource
     * @param req the request parameters
     * @return the response
     */
    @GET
    @Timed
    public Response getResource(@BeanParam final LdpRequest req) {

        final IRI identifier = rdf.createIRI(TRELLIS_PREFIX + req.getPartition() + req.getPath());
        final LdpGetHandler getHandler = new LdpGetHandler(partitions, req, resourceService, ioService, binaryService);

        // Fetch a versioned resource
        if (nonNull(req.getVersion())) {
            LOGGER.info("Getting versioned resource: {}", req.getVersion().toString());
            return resourceService.get(identifier, req.getVersion().getInstant())
                .map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();

        // Fetch a timemap
        } else if (TIMEMAP.equals(req.getExt())) {
            LOGGER.info("Getting timemap resource");
            return resourceService.get(identifier).map(MementoResource::new)
                .map(res -> res.getTimeMapBuilder(partitions, req, ioService))
                .orElse(status(NOT_FOUND)).build();

        // Fetch a timegate
        } else if (nonNull(req.getDatetime())) {
            LOGGER.info("Getting timegate resource: {}", req.getDatetime().getInstant());
            return resourceService.get(identifier, req.getDatetime().getInstant())
                .map(MementoResource::new).map(res -> res.getTimeGateBuilder(partitions, req))
                .orElse(status(NOT_FOUND)).build();
        }

        // Fetch the current state of the resource
        return resourceService.get(identifier).map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform an OPTIONS operation on an LDP Resource
     * @param req the request
     * @return the response
     */
    @OPTIONS
    @Timed
    public Response options(@BeanParam final LdpRequest req) {

        final IRI identifier = rdf.createIRI(TRELLIS_PREFIX + req.getPartition() + req.getPath());
        final LdpOptionsHandler optionsHandler = new LdpOptionsHandler(partitions, req, resourceService);

        if (nonNull(req.getVersion())) {
            return resourceService.get(identifier, req.getVersion().getInstant()).map(optionsHandler::ldpOptions)
                .orElse(status(NOT_FOUND)).build();

        } else if (TIMEMAP.equals(req.getExt())) {
            return resourceService.get(identifier, MAX).map(optionsHandler::ldpOptions)
                .orElse(status(NOT_FOUND)).build();
        }

        return resourceService.get(identifier).map(optionsHandler::ldpOptions).orElse(status(NOT_FOUND)).build();
    }


    /**
     * Perform a PATCH operation on an LDP Resource
     * @param req the request
     * @param body the body
     * @return the response
     */
    @PATCH
    @Timed
    @Consumes("application/sparql-update")
    public Response updateResource(@BeanParam final LdpRequest req, final String body) {

        if (nonNull(req.getVersion()) || UPLOADS.equals(req.getExt())) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final IRI identifier = rdf.createIRI(TRELLIS_PREFIX + req.getPartition() + req.getPath());
        final LdpPatchHandler patchHandler = new LdpPatchHandler(partitions, req, body, resourceService, ioService,
                constraintService);

        return resourceService.get(identifier, MAX).map(patchHandler::updateResource).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a DELETE operation on an LDP Resource
     * @param req the request
     * @return the response
     */
    @DELETE
    @Timed
    public Response deleteResource(@BeanParam final LdpRequest req) {

        if (nonNull(req.getExt()) || nonNull(req.getVersion())) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final IRI identifier = rdf.createIRI(TRELLIS_PREFIX + req.getPartition() + req.getPath());
        final LdpDeleteHandler deleteHandler = new LdpDeleteHandler(partitions, req, resourceService);

        return resourceService.get(identifier, MAX).map(deleteHandler::deleteResource)
            .orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a POST operation on a LDP Resource
     * @param req the request
     * @param body the body
     * @return the response
     */
    @POST
    @Timed
    public Response createResource(@BeanParam final LdpRequest req, final InputStream body) {

        if (unsupportedTypes.contains(req.getContentType())) {
            return status(UNSUPPORTED_MEDIA_TYPE).build();
        }

        final String path = req.getPartition() + req.getPath();
        final String identifier = "/" + ofNullable(req.getSlug())
            .orElseGet(resourceService.getIdentifierSupplier());

        if (nonNull(req.getExt()) || nonNull(req.getVersion())) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, req, identifier, body, resourceService,
                ioService, constraintService, binaryService);

        // First check if this is a container
        final Optional<Resource> parent = resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX);
        if (parent.isPresent()) {
            final Optional<IRI> ixModel = parent.map(Resource::getInteractionModel);
            if (ixModel.filter(type -> ldpResourceTypes(type).anyMatch(LDP.Container::equals)).isPresent()) {
                return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path + identifier), MAX)
                    .map(x -> status(CONFLICT)).orElseGet(postHandler::createResource).build();
           } else if (ixModel.filter(LDP.Resource::equals).isPresent() &&
                    parent.get().getTypes().anyMatch(Trellis.DeletedResource::equals)) {
                return status(GONE).build();
            }
            return status(METHOD_NOT_ALLOWED).build();
        }
        return status(NOT_FOUND).build();
    }

    /**
     * Perform a PUT operation on a LDP Resource
     * @param req the request
     * @param body the body
     * @return the response
     */
    @PUT
    @Timed
    public Response setResource(@BeanParam final LdpRequest req, final InputStream body) {

        if (unsupportedTypes.contains(req.getContentType())) {
            return status(UNSUPPORTED_MEDIA_TYPE).build();
        }

        if (nonNull(req.getExt()) || nonNull(req.getVersion())) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final IRI identifier = rdf.createIRI(TRELLIS_PREFIX + req.getPartition() + req.getPath());
        final LdpPutHandler putHandler = new LdpPutHandler(partitions, req, body, resourceService, ioService,
                constraintService, binaryService);

        return resourceService.get(identifier, MAX).map(putHandler::setResource)
            .orElseGet(putHandler::setResource).build();
    }
}
