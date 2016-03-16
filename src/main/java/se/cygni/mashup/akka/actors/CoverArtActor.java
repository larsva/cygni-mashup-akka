package se.cygni.mashup.akka.actors;

import akka.japi.pf.ReceiveBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Created by lasse on 2016-03-12.
 */
@Component("CoverArtActor")
@Scope("prototype")
public class CoverArtActor extends AbstractAPIActor<CoverArtActor.AlbumId> {

    private static String URL = "http://coverartarchive.org/release-group/%s";

    public static class AlbumId {
        private String id;

        public AlbumId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public CoverArtActor() {
        receive(ReceiveBuilder.
                match(AlbumId.class, albumId -> {
                    logger.info("Received AlbumId message: {}", albumId.getId());
                    handleRequestMessage(albumId);
                }).
                matchAny(o -> logger.info("Received unknown message: {}",o)).build()
        );
    }

    @Override
    protected String url(AlbumId message) {
        return String.format(URL,message.getId());
    }

}
