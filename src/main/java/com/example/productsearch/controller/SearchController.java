package com.example.productsearch.controller;

import com.example.productsearch.model.Product;
import com.example.productsearch.model.SearchExplanation;
import com.example.productsearch.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final ProductService productService;

    public SearchController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> semanticSearch(@RequestParam String q) throws IOException {
        List<Product> results = productService.semanticSearch(q);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/hybrid")
    public ResponseEntity<List<Product>> hybridSearch(@RequestParam String q) throws IOException {
        List<Product> results = productService.hybridSearch(q);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/explain")
    public ResponseEntity<SearchExplanation> explainSearch(@RequestParam String q) throws IOException {
        SearchExplanation explanation = productService.explainSearch(q);
        return ResponseEntity.ok(explanation);
    }
}
