package se.cygni.mashup.akka;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * This class exists since the method {@link CompletableFuture#allOf(CompletableFuture[])}-method returns a
 * <code>CompletableFuture</code> with a <code>Void</code> type (which is pretty useless).
 */
public class AsyncFunctions {
    /**
     * Returns a {@link CompletableFuture} that contains the entire stream of the other futures. The returned {@link
     * CompletableFuture} is completed when all of the provided futures are completed. This is very similar to the {@link
     * CompletableFuture#allOf(CompletableFuture[])}-method but has a nicer signature.
     */
    public static <T> CompletableFuture<Stream<T>> allOf(final Stream<CompletableFuture<T>> futures) {
        return allOf(toArray(futures));
    }

    private static <T> CompletableFuture<Stream<T>> allOf(final CompletableFuture<T>[] promises) {
        return supplyAsync(() -> CompletableFuture.allOf(promises))
                .thenCompose(v -> completedFuture(stream(promises).map(CompletableFuture::join)));
    }

    // This is a known Java weakness
    // http://stackoverflow.com/questions/529085/how-to-create-a-generic-array-in-java
    @SuppressWarnings("unchecked")
    private static <T> CompletableFuture<T>[] toArray(final Stream<CompletableFuture<T>> futures) {
        return (CompletableFuture<T>[]) futures.toArray(CompletableFuture[]::new);
    }
}
