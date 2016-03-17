package se.cygni.mashup.akka.common;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.Futures;
import akka.dispatch.OnFailure;
import akka.pattern.AskTimeoutException;
import akka.util.Timeout;
import com.fasterxml.jackson.databind.JsonNode;
import jersey.repackaged.com.google.common.collect.Lists;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;
import se.cygni.mashup.akka.actors.CoverArtActor;
import se.cygni.mashup.akka.actors.MusicBrainzActor;
import se.cygni.mashup.akka.actors.WikipediaActor;

import javax.ws.rs.container.AsyncResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static akka.pattern.Patterns.ask;
import static se.cygni.mashup.akka.extension.SpringExtension.SpringExtProvider;

public class MashupFunctions {

    public static final Timeout DEFAULT_TIMEOUT = Timeout.durationToTimeout(FiniteDuration.create(5, TimeUnit.SECONDS));
    private static final AtomicLong musicBrainzCount = new AtomicLong(0);
    private static final AtomicLong wikipediaCount = new AtomicLong(0);
    private static final AtomicLong coverArtCount = new AtomicLong(0);

    public static OnFailure failureHandler(final AsyncResponse asyncResponse, final JsonTraverser json) {
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

    public static Future<Iterable<Object>> combine(Future<Object> future,Collection<Future<Object>> futures,ExecutionContext context) {
        List<Future<Object>> futureList = Lists.newArrayList(future);
        futureList.addAll(futures);
        return Futures.sequence(futureList, context);
    }

    public static MashupResult createResult(JsonNode mbData,Iterable<Object> descriptionAndAlbums) {
        Map<? extends Class<?>, List<Object>> resultByClass = StreamSupport
                                                                .stream(descriptionAndAlbums.spliterator(), false)
                                                                .collect(Collectors.groupingBy(o -> o.getClass()));
        List<Object> descriptions = resultByClass.get(Description.class);
        String description = descriptions.isEmpty() ? "n/a" : ((Description)descriptions.get(0)).getDescription();
        List<JsonNode> albums = resultByClass.get(AlbumNode.class)
                                    .stream()
                                    .map(a -> ((AlbumNode)a).getNode()).collect(Collectors.toList());
        return new MashupResult(mbData, description, albums);
    }

    public static Future<Object> sendWikipediaRequest(String name,ActorSystem actorSystem) {
        return ask(wikipediaActor(actorSystem), new WikipediaActor.ArtistName(name), DEFAULT_TIMEOUT);

    }

    public static Future<Object> sendMusicBrainzRequest(String mbid,ActorSystem actorSystem) {
        return ask(musicBrainzActor(actorSystem), new MusicBrainzActor.MBId(mbid), DEFAULT_TIMEOUT);

    }

    public static Future<Object> sendCoverArtRequest(String albumId,ActorSystem actorSystem) {
        return ask(coverArtActor(actorSystem), new CoverArtActor.AlbumId(albumId), DEFAULT_TIMEOUT);

    }

    private static ActorRef musicBrainzActor(ActorSystem actorSystem) {
        return actorSystem.actorOf(
                SpringExtProvider.get(actorSystem).props("MusicBrainzActor"), "MusicBrainz:" + musicBrainzCount.getAndIncrement());

    }

    private static ActorRef wikipediaActor(ActorSystem actorSystem) {
        return actorSystem.actorOf(
                SpringExtProvider.get(actorSystem).props("WikipediaActor"), "Wikipedia:" + wikipediaCount.getAndIncrement());

    }

    private static ActorRef coverArtActor(ActorSystem actorSystem) {
        return actorSystem.actorOf(
                SpringExtProvider.get(actorSystem).props("CoverArtActor"), "CoverArt:" + coverArtCount.getAndIncrement());

    }

}
