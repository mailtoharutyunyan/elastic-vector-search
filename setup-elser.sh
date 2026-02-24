#!/bin/bash
# Waits for Elasticsearch 9 to be ready and activates trial license.
# In ES 9, semantic_text defaults to ELSER which auto-deploys on first use.

ES_URL="http://localhost:9200"

echo "=== Elasticsearch 9 Setup ==="
echo ""
echo "Waiting for Elasticsearch to be ready..."
until curl -sf "$ES_URL/_cluster/health" > /dev/null 2>&1; do
    sleep 2
    printf "."
done
echo ""

VERSION=$(curl -sf "$ES_URL" | grep -o '"number" : "[^"]*"' | head -1)
echo "Elasticsearch is ready! $VERSION"

echo ""
echo "Activating trial license (required for inference/ELSER)..."
curl -s -X POST "$ES_URL/_license/start_trial?acknowledge=true" 2>&1
echo ""

echo ""
echo "Done! semantic_text fields will auto-deploy ELSER on first use."
echo ""
echo "Start the Spring Boot app:  ./mvnw spring-boot:run"
echo "Then open:                  http://localhost:8080"
