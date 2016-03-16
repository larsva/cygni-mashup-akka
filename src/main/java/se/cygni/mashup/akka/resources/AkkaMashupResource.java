package se.cygni.mashup.akka.resources;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.*;
import akka.util.Timeout;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
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

    private Logger logger = Logger.getLogger(AkkaMashupResource.class.getName());

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
        musicBrainzFuture.onFailure(new OnFailure() {
            @Override
            public void onFailure(Throwable t) throws Throwable {
                asyncResponse.resume(json.createError(t));
            }
        }, actorSystem.dispatcher());

        musicBrainzFuture
            .map(new Mapper<Object,JsonNode>() {
                public JsonNode apply(Object object) {
                    return (JsonNode)object;
                }
             },actorSystem.dispatcher())
            .onSuccess(new OnSuccess<JsonNode>() {
                @Override
                public void onSuccess(final JsonNode mbData) {
                    fetchWikipediaDescription(mbData)
                          .onSuccess(new OnSuccess<Optional<String>>() {
                            @Override
                            public void onSuccess(final Optional<String> description) throws Throwable {
                                Futures.sequence(fetchAllCoverArt(mbData), actorSystem.dispatcher())
                                        .map(new Mapper<Iterable<Object>, List<JsonNode>>() {
                                            public List<JsonNode> apply(Iterable<Object> objects) {
                                                return StreamSupport.stream(objects.spliterator(), false)
                                                        .map(o -> (JsonNode) o)
                                                        .collect(Collectors.toList());
                                            }
                                        }, actorSystem.dispatcher())
                                        .onComplete(new OnComplete<List<JsonNode>>() {
                                            @Override
                                            public void onComplete(Throwable throwable, List<JsonNode> albums) throws Throwable {
                                                if (throwable != null) {
                                                    asyncResponse.resume(json.createError(throwable));
                                                } else {
                                                    asyncResponse.resume(json.createResult(mbData, description, albums));
                                                }
                                            }
                                        }, actorSystem.dispatcher());

                            }
                        }, actorSystem.dispatcher());
                }
            },actorSystem.dispatcher());
     }

    private Future<Object> fetchDataFromMusicBrainz(String mbId) {
        return sendMusicBrainzRequest(mbId);
    }

    private Future<Optional<String>> fetchWikipediaDescription(JsonNode mbData) {
        Optional<String> name = json.scrapeWikipediaName(mbData);
        return name.isPresent()
                ? sendWikipediaRequest(name.get()).map(new Mapper<Object, Optional<String>>() {
                                                        @Override
                                                        public Optional<String> apply(Object parameter) {
                                                            return json.scrapeDescription((JsonNode)parameter);
                                                        }
                                                    }, actorSystem.dispatcher())
                : Futures.successful(Optional.<String>empty());
    }

    private List<Future<Object>> fetchAllCoverArt(JsonNode mbData) {
        return json.scrapeAlbums(mbData)
                    .map(this::fetchCoverArtFor)
                    .collect(Collectors.toList());
     }

    private Future<Object> fetchCoverArtFor(final JsonNode album) {
        final String albumId = json.id(album);
        Future<Object> coverArtFuture = sendCoverArtRequest(albumId);
        return coverArtFuture.map(new Mapper<Object,Object>(){
            @Override
            public Object apply(Object object) {
                return json.createCoverArtEntry(album, (JsonNode) object);
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


}
