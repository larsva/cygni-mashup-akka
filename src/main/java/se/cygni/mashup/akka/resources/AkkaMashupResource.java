package se.cygni.mashup.akka.resources;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import com.fasterxml.jackson.databind.JsonNode;
import org.glassfish.jersey.server.ManagedAsync;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import scala.concurrent.duration.FiniteDuration;
import se.cygni.mashup.akka.AsyncFunctions;
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
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static akka.pattern.PatternsCS.ask;
import static java.util.concurrent.CompletableFuture.completedFuture;
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
        fetchDataFromMusicBrainz(mbid)
                 .thenCompose(mbData -> fetchWikipediaDescription(mbData)
                                        .thenCombine(fetchAllCoverArt(mbData),
                                                (description,albums) -> asyncResponse.resume(json.createResult(mbData,description,albums))))
                 .exceptionally(t -> asyncResponse.resume(json.createError(t)));

    }

    private CompletionStage<JsonNode> fetchDataFromMusicBrainz(String mbId) {
        return sendMusicBrainzRequest(mbId)
                .thenApply(data -> (JsonNode)data);
    }

    private CompletionStage<Optional<String>> fetchWikipediaDescription(JsonNode mbData) {
        return json.scrapeWikipediaName(mbData)
                .map(name -> sendWikipediaRequest(name)
                                .thenApply(data -> (JsonNode)data)
                                .thenApply(jsonNode -> json.scrapeDescription(jsonNode)))
                .orElse(completedFuture(Optional.empty()));
    }

    private CompletionStage<Stream<JsonNode>> fetchAllCoverArt(JsonNode mbData) {
        return AsyncFunctions.allOf(json.scrapeAlbums(mbData)
                                     .map(album -> fetchCoverArtFor(album).toCompletableFuture()));
    }

    private CompletionStage<JsonNode> fetchCoverArtFor(final JsonNode album) {
        final String albumId = json.id(album);
        return sendCoverArtRequest(albumId)
                .exceptionally(t -> json.createError(t))
                .thenApply(data -> (JsonNode)data)
                .thenApply(jsonNode -> json.createCoverArtEntry(album, jsonNode));
    }

    private CompletionStage<Object> sendWikipediaRequest(String name) {
        return ask(wikipediaActor(), new WikipediaActor.ArtistName(name),
                Timeout.durationToTimeout(FiniteDuration.create(5, TimeUnit.SECONDS)));

    }

    private CompletionStage<Object> sendMusicBrainzRequest(String mbid) {
        return ask(musicBrainzActor(), new MusicBrainzActor.MBId(mbid),
                Timeout.durationToTimeout(FiniteDuration.create(5, TimeUnit.SECONDS)));

    }

    private CompletionStage<Object> sendCoverArtRequest(String albumId) {
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
