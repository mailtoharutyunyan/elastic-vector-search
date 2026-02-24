package com.example.productsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.SemanticTextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.DoubleNumberProperty;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.productsearch.model.Product;
import com.example.productsearch.model.SearchExplanation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final String INDEX_NAME = "products";

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${elasticsearch.host:localhost}")
    private String esHost;

    @Value("${elasticsearch.port:9200}")
    private int esPort;

    public ProductService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public void createIndexIfNotExists() throws IOException {
        boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();
        if (exists) {
            log.info("Index '{}' already exists", INDEX_NAME);
            return;
        }

        esClient.indices().create(c -> c
                .index(INDEX_NAME)
                .mappings(m -> m
                        .properties("id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                        .properties("name", Property.of(p -> p.text(TextProperty.of(t -> t))))
                        .properties("description", Property.of(p -> p.semanticText(
                                SemanticTextProperty.of(st -> st)
                        )))
                        .properties("category", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                        .properties("price", Property.of(p -> p.double_(DoubleNumberProperty.of(d -> d))))
                        .properties("image_url", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                )
        );
        log.info("Index '{}' created with semantic_text mapping", INDEX_NAME);
    }

    public void indexProduct(Product product) throws IOException {
        IndexResponse response = esClient.index(i -> i
                .index(INDEX_NAME)
                .id(product.id())
                .document(product)
        );
        log.info("Indexed product '{}' — result: {}", product.name(), response.result());
    }

    public List<Product> semanticSearch(String query) throws IOException {
        SearchResponse<Product> response = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .query(q -> q
                                .semantic(sem -> sem
                                        .field("description")
                                        .query(query)
                                )
                        ),
                Product.class
        );

        List<Product> results = new ArrayList<>();
        for (Hit<Product> hit : response.hits().hits()) {
            if (hit.source() != null) {
                results.add(hit.source());
            }
        }
        log.info("Semantic search for '{}' returned {} results", query, results.size());
        return results;
    }

    public List<Product> hybridSearch(String query) throws IOException {
        SearchResponse<Product> response = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .query(q -> q
                                .bool(b -> b
                                        .should(sh -> sh
                                                .semantic(sem -> sem
                                                        .field("description")
                                                        .query(query)
                                                )
                                        )
                                        .should(sh -> sh
                                                .multiMatch(mm -> mm
                                                        .query(query)
                                                        .fields("name^2", "category")
                                                )
                                        )
                                )
                        ),
                Product.class
        );

        List<Product> results = new ArrayList<>();
        for (Hit<Product> hit : response.hits().hits()) {
            if (hit.source() != null) {
                results.add(hit.source());
            }
        }
        log.info("Hybrid search for '{}' returned {} results", query, results.size());
        return results;
    }

    public SearchExplanation explainSearch(String query) throws IOException {
        // 1. Get ELSER tokens via the _inference API
        Map<String, Double> queryTokens = fetchElserTokens(query);

        // 2. Run semantic search with scores
        SearchResponse<Product> response = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .query(q -> q
                                .semantic(sem -> sem
                                        .field("description")
                                        .query(query)
                                )
                        ),
                Product.class
        );

        double maxScore = response.hits().maxScore() != null ? response.hits().maxScore() : 0.0;

        List<SearchExplanation.ScoredResult> scoredResults = new ArrayList<>();
        for (Hit<Product> hit : response.hits().hits()) {
            if (hit.source() != null) {
                scoredResults.add(new SearchExplanation.ScoredResult(
                        hit.source(),
                        hit.score() != null ? hit.score() : 0.0,
                        maxScore
                ));
            }
        }

        log.info("Explain search for '{}': {} tokens, {} results", query, queryTokens.size(), scoredResults.size());
        return new SearchExplanation(query, queryTokens, scoredResults);
    }

    private Map<String, Double> fetchElserTokens(String query) throws IOException {
        String esUrl = "http://" + esHost + ":" + esPort;
        // Try the default ELSER inference endpoint that semantic_text uses
        String[] endpoints = {
                esUrl + "/_inference/sparse_embedding/.elser-2-elasticsearch",
                esUrl + "/_inference/sparse_embedding/.elser_model_2_linux-x86_64"
        };

        String requestBody = objectMapper.writeValueAsString(Map.of("input", List.of(query)));

        for (String endpoint : endpoints) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseElserTokens(response.body());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching ELSER tokens", e);
            } catch (Exception e) {
                log.debug("Inference endpoint {} not available: {}", endpoint, e.getMessage());
            }
        }

        // Fallback: try to discover the inference endpoint
        try {
            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(esUrl + "/_inference/sparse_embedding"))
                    .GET()
                    .build();

            HttpResponse<String> listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());
            if (listResponse.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(listResponse.body());
                JsonNode endpoints2 = root.get("endpoints");
                if (endpoints2 != null && endpoints2.isArray() && !endpoints2.isEmpty()) {
                    String inferenceId = endpoints2.get(0).get("inference_id").asText();
                    String endpoint = esUrl + "/_inference/sparse_embedding/" + inferenceId;

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        return parseElserTokens(response.body());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while discovering inference endpoints", e);
        } catch (Exception e) {
            log.warn("Could not discover inference endpoints: {}", e.getMessage());
        }

        log.warn("No ELSER inference endpoint found, returning empty tokens");
        return Map.of();
    }

    private Map<String, Double> parseElserTokens(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        // Response format: { "sparse_embedding": [ { "is_truncated": false, "embedding": { token: weight, ... } } ] }
        JsonNode sparseEmbedding = root.at("/sparse_embedding/0/embedding");
        if (sparseEmbedding.isMissingNode()) {
            // Try alternate response formats
            sparseEmbedding = root.at("/sparse_embedding/0");
        }
        if (sparseEmbedding.isMissingNode()) {
            sparseEmbedding = root.at("/results/0/sparse_embedding");
        }
        if (sparseEmbedding.isMissingNode()) {
            log.warn("Could not parse ELSER tokens from response");
            return Map.of();
        }

        Map<String, Double> tokens = new LinkedHashMap<>();
        sparseEmbedding.fields().forEachRemaining(entry ->
                tokens.put(entry.getKey(), entry.getValue().asDouble())
        );

        // Sort by weight descending and take top 20
        return tokens.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}
