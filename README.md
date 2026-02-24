# Semantic Product Search with Spring Boot & Elasticsearch ELSER

A Spring Boot application demonstrating **semantic search** using Elasticsearch 9's built-in **ELSER** (Elastic Learned Sparse EncodeR) model. No external AI services needed — all embedding generation happens inside Elasticsearch.

## Architecture

```
Browser → Spring Boot (:8080) → Elasticsearch (:9200) → ELSER model → Ranked results
```

The `semantic_text` field type in Elasticsearch 9 handles everything automatically:
- Deploys the ELSER model on first use
- Generates sparse embeddings at index time and query time
- Chunks long text (250 words, 100-word overlap)
- No pipeline configuration or model management needed

## Tech Stack

| Component            | Version |
|----------------------|---------|
| Spring Boot          | 4.0.2   |
| Java                 | 21      |
| Elasticsearch        | 9.0.0   |
| elasticsearch-java   | 9.0.1   |
| Docker Compose       | v2+     |

## Project Structure

```
src/main/java/com/example/productsearch/
├── ProductSearchApplication.java           # Entry point
├── config/
│   └── ElasticsearchConfig.java            # ES client bean
├── controller/
│   └── SearchController.java               # REST endpoints
├── service/
│   └── ProductService.java                 # Search logic + ELSER inference
├── model/
│   ├── Product.java                        # Product record
│   └── SearchExplanation.java              # Explain response DTO
└── init/
    └── DataInitializer.java                # Sample data on startup
```

## Quick Start

### 1. Start Elasticsearch

```bash
docker compose up -d
```

### 2. Activate trial license (required for ELSER)

```bash
./setup-elser.sh
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

### 4. Open the UI

Navigate to [http://localhost:8080](http://localhost:8080)

## API Endpoints

| Method | Path                   | Description                                  |
|--------|------------------------|----------------------------------------------|
| GET    | `/api/search?q=...`         | Semantic search (ELSER on description)  |
| GET    | `/api/search/hybrid?q=...`  | Hybrid search (semantic + keyword)      |
| GET    | `/api/search/explain?q=...` | Search with ELSER tokens and scores     |

## Live Search Visualization

Toggle **Visualize** mode in the UI to see a step-by-step animated breakdown of what happens when you search:

1. **Your Query** — the raw text you typed
2. **ELSER Processing** — embedding generation inside Elasticsearch
3. **Query Tokens** — real ELSER sparse vector tokens with weights
4. **Matching & Scoring** — each product scored against the query vector
5. **Ranked Results** — final product cards ordered by relevance

This uses the `/api/search/explain` endpoint which calls the ES `_inference` API to retrieve actual ELSER tokens.

## How ELSER Embeddings Work

ELSER generates **sparse vectors** — a set of weighted tokens that capture semantic meaning. Unlike dense embeddings (fixed-length float arrays), sparse vectors are interpretable: you can see exactly which concepts the model extracted.

### Example: Embedded Model Output

When the text `"Compact tenkeyless mechanical keyboard with Cherry MX switches, programmable RGB lighting for gaming and programming"` is processed by ELSER, it produces the following sparse embedding:

```json
{
  "sparse_embedding": [
    {
      "is_truncated": false,
      "embedding": {
        "cherry": 1.8424,
        "keyboard": 1.8164,
        "mx": 1.8008,
        "gaming": 1.5010,
        "ten": 1.4464,
        "compact": 1.3828,
        "mechanical": 1.3120,
        "program": 1.2150,
        "switches": 1.1738,
        "keyboards": 1.1660,
        "programming": 1.0508,
        "mouse": 1.0480,
        "mini": 1.0354,
        "portable": 1.0075,
        "switch": 0.9721,
        "key": 0.9299,
        "color": 0.9297,
        "lighting": 0.9174,
        "gamer": 0.7015,
        "keys": 0.6583,
        "small": 0.6444,
        "game": 0.5815,
        "light": 0.5723,
        "hardware": 0.4817,
        "display": 0.4710,
        "tool": 0.4622,
        "electronic": 0.4176,
        "buttons": 0.3998,
        "software": 0.2952,
        "computer": 0.2032,
        "laptop": 0.1900,
        "wireless": 0.2168,
        "...": "~90 tokens total"
      }
    }
  ]
}
```

Key observations:
- **Directly mentioned terms** like `keyboard`, `cherry`, `mx`, `gaming` score highest
- **Inferred concepts** like `mouse`, `gamer`, `portable`, `hardware`, `computer` appear even though they're not in the original text — this is the power of semantic understanding
- **Weights indicate relevance** — `cherry` (1.84) is strongly associated with this product, while `laptop` (0.19) is only loosely related
- **~90 tokens** are generated per chunk, creating a rich semantic fingerprint

This is the output from Elasticsearch's `_inference` API:

```bash
POST /_inference/sparse_embedding/.elser-2-elasticsearch
{
  "input": ["Compact tenkeyless mechanical keyboard with Cherry MX switches, programmable RGB lighting for gaming and programming"]
}
```

### ELSER Model Configuration

The model is auto-deployed by Elasticsearch when a `semantic_text` field is first used:

```json
{
  "inference_id": ".elser-2-elasticsearch",
  "task_type": "sparse_embedding",
  "service": "elasticsearch",
  "service_settings": {
    "num_allocations": 1,
    "num_threads": 1,
    "model_id": ".elser_model_2",
    "adaptive_allocations": {
      "enabled": true,
      "min_number_of_allocations": 0,
      "max_number_of_allocations": 32
    }
  },
  "chunking_settings": {
    "strategy": "sentence",
    "max_chunk_size": 250,
    "sentence_overlap": 1
  }
}
```

### Index Mapping

```json
{
  "products": {
    "mappings": {
      "properties": {
        "id":          { "type": "keyword" },
        "name":        { "type": "text" },
        "description": { "type": "semantic_text", "inference_id": ".elser-2-elasticsearch" },
        "category":    { "type": "keyword" },
        "price":       { "type": "double" },
        "image_url":   { "type": "keyword" }
      }
    }
  }
}
```

The `description` field is typed as `semantic_text` — Elasticsearch automatically generates and stores ELSER sparse vectors when documents are indexed, and generates query vectors at search time.

## Search Modes

### Semantic Search
Uses pure ELSER on the `description` field. Understands meaning, synonyms, and related concepts:

```
Query: "gift for a programmer"  →  finds Mechanical Keyboard, Laptop Bag, Headphones
Query: "warm drink"             →  finds Green Tea, Coffee Maker
```

### Hybrid Search
Combines semantic search (ELSER on description) with keyword matching (BM25 on name + category):

```java
bool:
  should:
    - semantic(description, query)    // meaning
    - multi_match(name^2, category)   // exact terms
```

## Sample Data

10 products are automatically indexed on startup across categories: Footwear, Electronics, Food & Beverage, Furniture, Kitchen, Sports, and Bags.
