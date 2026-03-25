package in.supporthub.faq.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;


/**
 * Generates text embeddings using OpenAI's {@code text-embedding-3-small} model via Spring AI.
 *
 * <p>Embeddings are generated from a combined representation of the FAQ question and answer,
 * producing a 1536-dimensional float vector suitable for pgvector cosine similarity search.
 *
 * <p>Failure handling: if the OpenAI call fails for any reason (network, quota, model error),
 * this service logs a warning and returns an empty {@code float[0]} array. The caller MUST
 * check for an empty array and handle the graceful degradation path (FAQ still saved, but
 * not semantically searchable).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /** Maximum character length of the combined text sent to the embedding API. */
    private static final int MAX_TEXT_LENGTH = 8000;

    /**
     * Generates a 1536-dimensional embedding for the given question and answer.
     *
     * <p>The combined text is formatted as:
     * <pre>{@code Question: {question}\nAnswer: {answer}}</pre>
     *
     * <p>On any exception the method returns {@code float[0]} — the FAQ entry is still
     * persisted without an embedding. It will appear in keyword search but not in
     * semantic similarity results until the embedding is regenerated.
     *
     * @param question the FAQ question text
     * @param answer   the FAQ answer text
     * @return 1536-dimensional float array, or {@code float[0]} on error
     */
    public float[] generateEmbedding(String question, String answer) {
        String combinedText = buildCombinedText(question, answer);
        try {
            return embeddingModel.embed(combinedText);
        } catch (Exception ex) {
            log.warn("Embedding generation failed — FAQ will be saved without embedding. error={}",
                    ex.getMessage());
            return new float[0];
        }
    }

    /**
     * Generates a 1536-dimensional embedding for a query string (used during search).
     *
     * @param query the natural-language search query
     * @return 1536-dimensional float array, or {@code float[0]} on error
     */
    public float[] generateQueryEmbedding(String query) {
        try {
            return embeddingModel.embed(query);
        } catch (Exception ex) {
            log.warn("Query embedding generation failed — will fall back to keyword search. error={}",
                    ex.getMessage());
            return new float[0];
        }
    }

    private String buildCombinedText(String question, String answer) {
        String combined = "Question: " + question + "\nAnswer: " + answer;
        if (combined.length() > MAX_TEXT_LENGTH) {
            combined = combined.substring(0, MAX_TEXT_LENGTH);
        }
        return combined;
    }


}
