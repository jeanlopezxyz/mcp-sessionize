package io.mcp.sessionize.client;

import java.util.List;

import io.mcp.sessionize.model.SessionizeModel.*;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client for Sessionize API.
 *
 * Sessionize provides a free JSON API for events.
 * API URL format: https://sessionize.com/api/v2/{EVENT_ID}/view/{VIEW}
 *
 * @see <a href="https://sessionize.com/api-documentation">Sessionize API Documentation</a>
 */
@RegisterRestClient(configKey = "sessionize")
@Path("/api/v2")
@ClientHeaderParam(name = "Cache-Control", value = "no-cache, no-store, must-revalidate")
@ClientHeaderParam(name = "Pragma", value = "no-cache")
public interface SessionizeClient {

    @GET
    @Path("/{eventId}/view/Speakers")
    List<Speaker> getSpeakers(@PathParam("eventId") String eventId);

    @GET
    @Path("/{eventId}/view/Sessions")
    List<SessionGroup> getSessions(@PathParam("eventId") String eventId);

    @GET
    @Path("/{eventId}/view/GridSmart")
    List<GridDay> getSchedule(@PathParam("eventId") String eventId);
}
