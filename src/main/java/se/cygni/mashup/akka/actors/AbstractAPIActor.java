package se.cygni.mashup.akka.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import se.cygni.mashup.akka.JsonTraverser;

import java.util.logging.Logger;

/**
 * Created by lasse on 2016-03-15.
 */
public abstract class AbstractAPIActor<M> extends AbstractActor {

    protected final LoggingAdapter logger = Logging.getLogger(context().system(), this);

    @Autowired
    protected JsonTraverser json;

    @Autowired
    protected RestTemplate restTemplate;

    protected void sendRequest(ActorRef sender, String url) {
        JsonNode result = restTemplate.getForObject(url, JsonNode.class);
        sender.tell(result, self());
    }

    protected void handleException(ActorRef sender, Exception e, M message) {
         sender.tell(createFailure(e,message),self());
    }

    protected Status.Failure createFailure(Exception e, @SuppressWarnings("UnusedParameters") M message) {
        return new Status.Failure(e);
    }

    protected void handleRequestMessage(M message) {
        ActorRef sender = sender();
        String url = url(message);
        try {
            logger.info("Sending request to " + url);
            sendRequest(sender, url);
        } catch (Exception e) {
            logger.error(String.format("Error: %s, Url: %s",e.getMessage(),url));
            handleException(sender, e, message);
        } finally {
            logger.debug("Done! I'll kill myself.");
            context().stop(self());
        }
    }

    protected abstract String url(M message);
}
