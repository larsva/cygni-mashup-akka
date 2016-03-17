package se.cygni.mashup.akka.common;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;

public class MashupResult {
    private final JsonNode mbData;
    private final String descriptiion;
    private final List<JsonNode> albums;

    public MashupResult(JsonNode mbData, String descriptiion, List<JsonNode> albums) {
        this.mbData = mbData;
        this.descriptiion = descriptiion;
        this.albums = albums != null ? albums : Collections.emptyList();
    }

    public JsonNode mbData() {
        return mbData;
    }

    public String description() {
        return descriptiion;
    }

    public List<JsonNode> albums() {
        return albums;
    }
}
