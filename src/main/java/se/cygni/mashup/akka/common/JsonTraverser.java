package se.cygni.mashup.akka.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

/**
 * Methods for traversing the MusicBrainz JSON structure and to support creation of the mashup output.
 */
@Component
public class JsonTraverser {
    private final ObjectMapper mapper;

    @Autowired
    JsonTraverser(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode createCoverArtEntry(final JsonNode releaseGroup, final JsonNode coverArtResult) {
        return objectNode()
                .put("id", id(releaseGroup))
                .put("title", releaseGroup.at("/title").textValue())
                .set("cover-art", coverArtResult);
    }

    public JsonNode createError(final Throwable error) {
        return createError(error instanceof CompletionException ? error.getCause().getMessage() : error.getMessage());
    }

    public JsonNode createError(final String error) {
        return objectNode().put("Error", error);
    }

    public JsonNode createResult(final JsonNode mbData, String description, final List<JsonNode> albums) {
        return objectNode()
                .put("id", id(mbData))
                .put("description", description)
                .set("albums", arrayNode().addAll(albums));
    }

    public String id(final JsonNode identifiable) {
        return identifiable.at("/id").textValue();
    }

    public Stream<JsonNode> scrapeAlbums(final JsonNode mbData) {
        return stream(mbData.at("/release-groups"))
                .filter(rg -> "Album".equals(rg.at("/primary-type").textValue()))
                .filter(rg -> rg.at("/secondary-types").size() == 0);
    }

    public Optional<String> scrapeDescription(final JsonNode wikipediaResult) {
        return Optional.of(wikipediaResult.at("/query/pages"))
                .filter(pages -> pages.fieldNames().hasNext())
                .map(pages -> pages.get(pages.fieldNames().next()).at("/extract").textValue());
    }

    public Optional<String> scrapeWikipediaName(final JsonNode mbData) {
        return stream(mbData.at("/relations"))
                .filter(relation -> "wikipedia".equals(relation.at("/type").textValue()))
                .map(relation -> relation.at("/url/resource").textValue())
                .map((url) -> nameFromUrl(url))
                .findFirst();
    }

    private ArrayNode arrayNode() {
        return mapper.createArrayNode();
    }

    private String nameFromUrl(final String url) {
        // Everything after the last slash
        return url.substring(url.lastIndexOf("/") + 1);
    }

    private ObjectNode objectNode() {
        return mapper.createObjectNode();
    }

    private Stream<JsonNode> stream(final JsonNode jsonNode) {
        return StreamSupport.stream(jsonNode.spliterator(), false);
    }
}
