package se.cygni.mashup.akka.actors;

import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Created by lasse on 2016-03-12.
 */
@Component("WikipediaActor")
@Scope("prototype")
public class WikipediaActor extends AbstractAPIActor<WikipediaActor.ArtistName> {

    private static String URL = "https://en.wikipedia.org/w/api.php?action=query&format=json&prop=extracts&exintro=true&redirects=true&titles=%s";

    public static class ArtistName  {
        private String name;

        public ArtistName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }


    public WikipediaActor() {
        receive(ReceiveBuilder.
                match(ArtistName.class, artistName -> {
                    logger.debug("Received ArtistName message: {}", artistName.getName());
                    handleRequestMessage(artistName);
                }).
                matchAny(o -> logger.error("Received unknown message: {}",o)).build()
        );
    }

    @Override
    protected String url(ArtistName message) {
        return String.format(URL,message.getName());
    }

    protected Status.Failure createFailure(Exception e, ArtistName message) {
        return new Status.Failure(new RuntimeException(String.format("Unable to get description from Wikipedia for name '%s' due to '%s'.",message.getName(),e.getMessage())));
    }}
