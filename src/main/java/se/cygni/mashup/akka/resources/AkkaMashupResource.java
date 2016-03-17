package se.cygni.mashup.akka.resources;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.AskTimeoutException;
import akka.util.Timeout;
import com.fasterxml.jackson.databind.JsonNode;
import javassist.runtime.Desc;
import org.glassfish.jersey.server.ManagedAsync;
import org.springframework.beans.factory.annotation.Autowired;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;
import se.cygni.mashup.akka.JsonTraverser;
import se.cygni.mashup.akka.actors.CoverArtActor;
import se.cygni.mashup.akka.actors.MusicBrainzActor;
import se.cygni.mashup.akka.actors.WikipediaActor;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static akka.pattern.Patterns.ask;
import static se.cygni.mashup.akka.extension.SpringExtension.SpringExtProvider;

/**
 * Created by lasse on 2016-03-07.
 */
@Path("/mashup")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class AkkaMashupResource {


    private final static Logger logger = Logger.getLogger(AkkaMashupResource.class.getName());

    private final AtomicLong musicBrainzCount = new AtomicLong(0);
    private final AtomicLong wikipediaCount = new AtomicLong(0);
    private final AtomicLong coverArtCount = new AtomicLong(0);

    @Autowired
    public ActorSystem actorSystem;

    @Autowired
    private JsonTraverser json;

    @ManagedAsync
    @GET
    @Path("/{mbid}")
    public void mashup(@Suspended final AsyncResponse asyncResponse, @PathParam("mbid") final String mbid) {
        Future<Object> musicBrainzFuture = fetchDataFromMusicBrainz(mbid);

        musicBrainzFuture.onFailure(handleFailure(asyncResponse), actorSystem.dispatcher());

        musicBrainzFuture
            .map(toJsonNode(),actorSystem.dispatcher())
            .onSuccess(musicBrainzSuccess(asyncResponse),actorSystem.dispatcher());
    }

    private OnFailure handleFailure(final AsyncResponse asyncResponse) {
        return new OnFailure() {
            @Override
            public void onFailure(Throwable t) throws Throwable {
                if (t instanceof AskTimeoutException) {
                    asyncResponse.resume(json.createError("Time-out when sending request. Do you have an internet connection?"));
                } else {
                    asyncResponse.resume(json.createError(t));
                }
            }
        };
    }

    private Mapper toJsonNode() {
        return new Mapper<Object,JsonNode>() {
            public JsonNode apply(Object object) {
                return (JsonNode)object;
            }
        };
    }

    private OnSuccess<JsonNode> musicBrainzSuccess(final AsyncResponse asyncResponse) {
        return new OnSuccess<JsonNode>() {
            @Override
            public void onSuccess(final JsonNode mbData) {
                Future<Object> wikipediaFuture = fetchWikipediaDescription(mbData);
                List<Future<Object>> coverArtsFuture = fetchAllCoverArt(mbData);
                List<Future<Object>> futures = new ArrayList<>();
                futures.add(wikipediaFuture);
                futures.addAll(coverArtsFuture);
                Futures.sequence(futures, actorSystem.dispatcher())
                        .onComplete(new OnComplete<Iterable<Object>>() {
                            @Override
                            public void onComplete(Throwable throwable, Iterable<Object> objects) throws Throwable {
                                if (throwable != null) {
                                    asyncResponse.resume(json.createError(throwable));
                                } else {

                                    Map<? extends Class<?>, List<Object>> resultByClass = StreamSupport.stream(objects.spliterator(), false)
                                            .collect(Collectors.groupingBy(o -> o.getClass()));
                                    List<Object> descriptions = resultByClass.get(Description.class);
                                    String description = descriptions.isEmpty() ? "n/a" : ((Description)descriptions.get(0)).getDescription();
                                    List<JsonNode> albums = resultByClass.get(AlbumNode.class).stream()
                                                                .map(a -> ((AlbumNode)a).getNode()).collect(Collectors.toList());
                                    asyncResponse.resume(json.createResult(mbData, description, albums));
                                }

                            }
                        }, actorSystem.dispatcher());
            }
        };
    }


    private Future<Object> fetchDataFromMusicBrainz(String mbId) {
        return sendMusicBrainzRequest(mbId);
    }

    private Future<Object> fetchWikipediaDescription(JsonNode mbData) {
        Optional<String> name = json.scrapeWikipediaName(mbData);
        return name.isPresent()
                ? sendWikipediaRequest(name.get())
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
        Future<Object> coverArtFuture = sendCoverArtRequest(albumId);
        return coverArtFuture
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

    private Future<Object> sendWikipediaRequest(String name) {
        return ask(wikipediaActor(), new WikipediaActor.ArtistName(name),
                Timeout.durationToTimeout(FiniteDuration.create(5, TimeUnit.SECONDS)));

    }

    private Future<Object> sendMusicBrainzRequest(String mbid) {
        return ask(musicBrainzActor(), new MusicBrainzActor.MBId(mbid),
                Timeout.durationToTimeout(FiniteDuration.create(5, TimeUnit.SECONDS)));

    }

    private Future<Object> sendCoverArtRequest(String albumId) {
        return ask(coverArtActor(), new CoverArtActor.AlbumId(albumId),
                Timeout.durationToTimeout(FiniteDuration.create(5, TimeUnit.SECONDS)));

    }

    private ActorRef musicBrainzActor() {
         return actorSystem.actorOf(
                SpringExtProvider.get(actorSystem).props("MusicBrainzActor"), "MusicBrainz:" + musicBrainzCount.getAndIncrement());

    }

    private ActorRef wikipediaActor() {
        return actorSystem.actorOf(
                SpringExtProvider.get(actorSystem).props("WikipediaActor"), "Wikipedia:" + wikipediaCount.getAndIncrement());

    }

    private ActorRef coverArtActor() {
        return actorSystem.actorOf(
                SpringExtProvider.get(actorSystem).props("CoverArtActor"), "CoverArt:" + coverArtCount.getAndIncrement());

    }

    private static class Description {
        private final String description;

        public Description(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private static class AlbumNode {
        private final JsonNode node;

        public AlbumNode(JsonNode node) {
            this.node = node;
        }

        public JsonNode getNode() {
            return node;
        }
    }

}
