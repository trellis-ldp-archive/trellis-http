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
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static javax.ws.rs.core.Response.status;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.domain.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.domain.HttpConstants.TIMEMAP;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.domain.HttpConstants.UPLOADS;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;

import com.codahale.metrics.annotation.Timed;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
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
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.IRI;

import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.PATCH;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.impl.LdpDeleteHandler;
import org.trellisldp.http.impl.LdpGetHandler;
import org.trellisldp.http.impl.LdpOptionsHandler;
import org.trellisldp.http.impl.LdpPatchHandler;
import org.trellisldp.http.impl.LdpPostHandler;
import org.trellisldp.http.impl.LdpPutHandler;
import org.trellisldp.http.impl.MementoResource;
import org.trellisldp.spi.AccessControlService;
import org.trellisldp.spi.AgentService;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@Path("{path: .+}")
@Produces({TEXT_TURTLE, APPLICATION_LD_JSON, APPLICATION_N_TRIPLES, APPLICATION_LINK_FORMAT, TEXT_HTML})
public class LdpResource extends BaseLdpResource {

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
     * @param agentService the agent service
     * @param accessService the access control service
     * @param partitions a map of partitions for use with custom hostnames
     * @param authChallenges a list of auth protocols supported
     * @param unsupportedMediaTypes any unsupported media types
     */
    public LdpResource(final ResourceService resourceService, final IOService ioService,
            final ConstraintService constraintService, final BinaryService binaryService,
            final AgentService agentService, final AccessControlService accessService,
            final Map<String, String> partitions, final List<String> authChallenges,
            final Collection<String> unsupportedMediaTypes) {
        super(partitions, authChallenges, agentService, accessService);
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
    public Response getResource(@BeanParam final LdpGetRequest req) {

        final List<MediaType> acceptableTypes = req.headers.getAcceptableMediaTypes();
        final String baseUrl = getBaseUrl(req);

        final Session session = getSession(req);
        if (ACL.equals(req.ext)) {
            verifyCanControl(session, req.path);
        } else {
            verifyCanRead(session, req.path);
        }

        final LdpGetHandler getHandler = new LdpGetHandler(resourceService, ioService, binaryService,
                req.request);
        getHandler.setPath(req.path);
        getHandler.setBaseUrl(baseUrl);
        getHandler.setAcceptableTypes(acceptableTypes);
        if (ACL.equals(req.ext)) {
            // TODO make this more compact?
            getHandler.setPrefer(new Prefer("return=representation; include=\"" +
                        Trellis.PreferAccessControl.getIRIString() + "\"; omit=\"" +
                        Trellis.PreferUserManaged.getIRIString() + " " +
                        LDP.PreferContainment.getIRIString() + " " +
                        LDP.PreferMembership.getIRIString() + "\""));
            getHandler.setGraphName(Trellis.PreferAccessControl);
        } else {
            getHandler.setPrefer(req.prefer);
        }
        getHandler.setWantDigest(req.digest);
        getHandler.setRange(req.range);

        // Fetch a versioned resource
        if (nonNull(req.version)) {
            LOGGER.info("Getting versioned resource: {}", req.version.toString());
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path), req.version.getInstant())
                .map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();

        // Fetch a timemap
        } else if (TIMEMAP.equals(req.ext)) {
            LOGGER.info("Getting timemap resource");
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path)).map(MementoResource::new)
                .map(res -> res.getTimeMapBuilder(baseUrl + req.path, acceptableTypes, ioService))
                .orElse(status(NOT_FOUND)).build();

        // Fetch a timegate
        } else if (nonNull(req.datetime)) {
            LOGGER.info("Getting timegate resource: {}", req.datetime.getInstant());
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path),
                    req.datetime.getInstant())
                .map(MementoResource::new).map(res -> res.getTimeGateBuilder(baseUrl + req.path,
                            req.datetime.getInstant()))
                .orElse(status(NOT_FOUND)).build();
        }

        // Fetch the current state of the resource
        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path))
                .map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform an OPTIONS operation on an LDP Resource
     * @param req the request
     * @return the response
     */
    @OPTIONS
    @Timed
    public Response options(@BeanParam final LdpBaseRequest req) {

        final Session session = getSession(req);
        if (ACL.equals(req.ext)) {
            verifyCanControl(session, req.path);
        } else {
            verifyCanRead(session, req.path);
        }

        final LdpOptionsHandler optionsHandler = new LdpOptionsHandler(resourceService);
        optionsHandler.setPath(req.path);
        optionsHandler.setBaseUrl(getBaseUrl(req));

        if (ACL.equals(req.ext)) {
            optionsHandler.setGraphName(Trellis.PreferAccessControl);
        }

        if (nonNull(req.version)) {
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path), req.version.getInstant())
                .map(optionsHandler::ldpOptions).orElse(status(NOT_FOUND)).build();

        } else if (TIMEMAP.equals(req.ext)) {
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path), MAX)
                .map(optionsHandler::ldpOptions).orElse(status(NOT_FOUND)).build();
        }

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path))
            .map(optionsHandler::ldpOptions).orElse(status(NOT_FOUND)).build();
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
    public Response updateResource(@BeanParam final LdpPatchRequest req, final String body) {

        final Session session = getSession(req);
        if (ACL.equals(req.ext)) {
            verifyCanControl(session, req.path);
        } else {
            verifyCanWrite(session, req.path);
        }

        if (nonNull(req.version) || UPLOADS.equals(req.ext)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final LdpPatchHandler patchHandler = new LdpPatchHandler(resourceService, ioService, constraintService,
                req.request);
        patchHandler.setPath(req.path);
        patchHandler.setBaseUrl(getBaseUrl(req));
        patchHandler.setAcceptableTypes(req.headers.getAcceptableMediaTypes());
        patchHandler.setPrefer(req.prefer);
        patchHandler.setSession(session);
        patchHandler.setSparqlUpdate(body);
        if (ACL.equals(req.ext)) {
            patchHandler.setGraphName(Trellis.PreferAccessControl);
        }

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path), MAX)
                .map(patchHandler::updateResource).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a DELETE operation on an LDP Resource
     * @param req the request
     * @return the response
     */
    @DELETE
    @Timed
    public Response deleteResource(@BeanParam final LdpBaseRequest req) {

        final Session session = getSession(req);
        verifyCanWrite(session, req.path);

        if (nonNull(req.ext) || nonNull(req.version)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final LdpDeleteHandler deleteHandler = new LdpDeleteHandler(resourceService, req.request);
        deleteHandler.setPath(req.path);
        deleteHandler.setBaseUrl(getBaseUrl(req));
        deleteHandler.setSession(session);

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path), MAX)
            .map(deleteHandler::deleteResource).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a POST operation on a LDP Resource
     * @param req the request
     * @param body the body
     * @return the response
     */
    @POST
    @Timed
    public Response createResource(@BeanParam final LdpPostRequest req, final InputStream body) {

        final Session session = getSession(req);
        verifyCanAppend(session, req.path);

        if (unsupportedTypes.contains(req.contentType)) {
            return status(UNSUPPORTED_MEDIA_TYPE).build();
        }

        final String baseUrl = getBaseUrl(req);

        final String fullPath = req.path + "/" + ofNullable(req.slug)
            .orElseGet(resourceService.getIdentifierSupplier());

        if (nonNull(req.ext) || nonNull(req.version)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final LdpPostHandler postHandler = new LdpPostHandler(resourceService, ioService, constraintService,
                binaryService);
        postHandler.setPath(fullPath);
        postHandler.setBaseUrl(baseUrl);
        postHandler.setSession(session);
        postHandler.setContentType(req.contentType);
        postHandler.setLink(req.link);
        postHandler.setEntity(body);

        // First check if this is a container
        final Optional<Resource> parent = resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path), MAX);
        if (parent.isPresent()) {
            final Optional<IRI> ixModel = parent.map(Resource::getInteractionModel);
            if (ixModel.filter(type -> ldpResourceTypes(type).anyMatch(LDP.Container::equals)).isPresent()) {
                return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + fullPath), MAX).map(x -> status(CONFLICT))
                    .orElseGet(postHandler::createResource).build();
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
    public Response setResource(@BeanParam final LdpPutRequest req, final InputStream body) {

        final Session session = getSession(req);
        verifyCanWrite(session, req.path);

        if (unsupportedTypes.contains(req.contentType)) {
            return status(UNSUPPORTED_MEDIA_TYPE).build();
        }

        if (nonNull(req.ext) || nonNull(req.version)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final String baseUrl = getBaseUrl(req);

        final LdpPutHandler putHandler = new LdpPutHandler(resourceService, ioService, constraintService,
                binaryService, req.request);
        putHandler.setPath(req.path);
        putHandler.setBaseUrl(baseUrl);
        putHandler.setSession(session);
        putHandler.setContentType(req.contentType);
        putHandler.setLink(req.link);
        putHandler.setEntity(body);

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + req.path), MAX)
                .map(putHandler::setResource).orElseGet(putHandler::setResource).build();
    }
}
