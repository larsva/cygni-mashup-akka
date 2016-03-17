package se.cygni.mashup.akka.common;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Created by lasse on 2016-03-17.
 */
public class AlbumNode {
    private final JsonNode node;

    public AlbumNode(JsonNode node) {
        this.node = node;
    }

    public JsonNode getNode() {
        return node;
    }
}
