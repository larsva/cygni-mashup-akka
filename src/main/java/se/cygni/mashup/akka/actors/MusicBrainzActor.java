package se.cygni.mashup.akka.actors;

import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Created by lasse on 2016-03-12.
 */
@Component("MusicBrainzActor")
@Scope("prototype")
public class MusicBrainzActor extends AbstractAPIActor<MusicBrainzActor.MBId> {

    private static String URL = "http://musicbrainz.org/ws/2/artist/%s?fmt=json&inc=url-rels+release-groups";

    public static class MBId {
        private String id;

        public MBId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public MusicBrainzActor() {
        receive(ReceiveBuilder.
                match(MBId.class, mbId -> {
                    logger.info("Received MBId message: {}", mbId.getId());
                    handleRequestMessage(mbId);
                }).
                matchAny(o -> logger.info("Received unknown message: {}",o)).build()
        );
    }

    protected String url(MBId message) {
        return String.format(URL,message.getId());
    }

    protected Status.Failure createFailure(Exception e,MusicBrainzActor.MBId message) {
        return new Status.Failure(new RuntimeException(String.format("Unable to fetch data from MusicBrainz for id '%s' due to '%s'.",message.getId(),e.getMessage())));
    }


}
