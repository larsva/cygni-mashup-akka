package se.cygni.mashup.akka.resources;

import akka.actor.ActorSystem;
import akka.dispatch.*;
import akka.util.Timeout;
import static akka.japi.Util.classTag;
import static akka.pattern.Patterns.ask;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;
import com.fasterxml.jackson.databind.JsonNode;
import org.glassfish.jersey.server.ManagedAsync;
import org.springframework.beans.factory.annotation.Autowired;

import se.cygni.mashup.akka.common.AlbumNode;
import se.cygni.mashup.akka.common.Description;
import se.cygni.mashup.akka.common.JsonTraverser;
import se.cygni.mashup.akka.common.MashupResult;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static se.cygni.mashup.akka.common.MashupFunctions.*;

@Path("/mashup")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class AkkaMashupResource {

    @Autowired
    public ActorSystem actorSystem;

    @Autowired
    private JsonTraverser json;

    @ManagedAsync
    @GET
    @Path("/{mbid}")
    public void mashup(@Suspended final AsyncResponse asyncResponse, @PathParam("mbid") final String mbid) {
        Future<Object> musicBrainzFuture = fetchDataFromMusicBrainz(mbid);

        musicBrainzFuture.onFailure(failureHandler(asyncResponse,json), actorSystem.dispatcher());

        musicBrainzFuture
            .mapTo(classTag(JsonNode.class))
            .onSuccess(musicBrainzSuccess(asyncResponse),actorSystem.dispatcher());
    }

    private OnSuccess<JsonNode> musicBrainzSuccess(final AsyncResponse asyncResponse) {
        return new OnSuccess<JsonNode>() {
            @Override
            public void onSuccess(final JsonNode mbData) {
                combine(fetchWikipediaDescription(mbData),fetchAllCoverArt(mbData),actorSystem.dispatcher())
                        .onComplete(new OnComplete<Iterable<Object>>() {
                            @Override
                            public void onComplete(Throwable throwable, Iterable<Object> objects) throws Throwable {
                                if (throwable != null) {
                                    asyncResponse.resume(json.createError(throwable));
                                } else {
                                    MashupResult result = createResult(mbData, objects);
                                    asyncResponse.resume(json.createResult(result.mbData(), result.description(), result.albums()));
                                }

                            }
                        }, actorSystem.dispatcher());
            }
        };
    }


    private Future<Object> fetchDataFromMusicBrainz(String mbId) {
        return sendMusicBrainzRequest(mbId,actorSystem);
    }

    private Future<Object> fetchWikipediaDescription(JsonNode mbData) {
        Optional<String> name = json.scrapeWikipediaName(mbData);
        return name.isPresent()
                ? sendWikipediaRequest(name.get(),actorSystem)
                        .map(new Mapper<Object, Object>() {
                            @Override
                            public Description apply(Object parameter) {
                                Optional<String> optional = json.scrapeDescription((JsonNode) parameter);
                                return new Description(optional.isPresent() ? optional.get() : "n/a");
                            }
                        }, actorSystem.dispatcher())

                : Futures.successful(new Description("n/a"));
    }

    private List<Future<Object>> fetchAllCoverArt(JsonNode mbData) {
        return json.scrapeAlbums(mbData)
                    .map(this::fetchCoverArtFor)
                    .collect(Collectors.toList());
     }

    private Future<Object> fetchCoverArtFor(final JsonNode album) {
        final String albumId = json.id(album);
        return sendCoverArtRequest(albumId,actorSystem)
                    .recoverWith(new Recover<Future<Object>>() {
                        @Override
                        public Future<Object> recover(Throwable throwable) throws Throwable {
                            return Futures.successful(json.createError(throwable));
                        }
                    },actorSystem.dispatcher())
                    .map(new Mapper<Object,Object>(){
                        @Override
                        public Object apply(Object object) {
                            return new AlbumNode(json.createCoverArtEntry(album, (JsonNode) object));
                        }
                    },actorSystem.dispatcher());
    }


}
