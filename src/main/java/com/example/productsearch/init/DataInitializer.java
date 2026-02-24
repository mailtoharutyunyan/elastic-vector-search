package com.example.productsearch.init;

import com.example.productsearch.model.Product;
import com.example.productsearch.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ProductService productService;

    public DataInitializer(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Initializing Elasticsearch index and sample data...");

        productService.createIndexIfNotExists();

        List<Product> sampleProducts = List.of(
                new Product("1", "Running Shoes Pro",
                        "Lightweight breathable running shoes with cushioned sole for marathon training and daily jogging on pavement or trail",
                        "Footwear", 129.99,
                        "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=300&h=200&fit=crop"),
                new Product("2", "Wireless Noise-Cancelling Headphones",
                        "Over-ear Bluetooth headphones with active noise cancellation, 30-hour battery life, perfect for music lovers and remote workers",
                        "Electronics", 249.99,
                        "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=300&h=200&fit=crop"),
                new Product("3", "Organic Green Tea Collection",
                        "Premium Japanese matcha and sencha green tea variety pack, rich in antioxidants, calming and refreshing natural beverage",
                        "Food & Beverage", 24.99,
                        "https://images.unsplash.com/photo-1556881286-fc6915169721?w=300&h=200&fit=crop"),
                new Product("4", "Ergonomic Office Chair",
                        "Adjustable lumbar support mesh office chair with headrest, designed for long hours of comfortable sitting and back health",
                        "Furniture", 449.99,
                        "https://images.unsplash.com/photo-1580480055273-228ff5388ef8?w=300&h=200&fit=crop"),
                new Product("5", "Stainless Steel Water Bottle",
                        "Double-wall vacuum insulated water bottle keeps drinks cold for 24 hours or hot for 12, eco-friendly reusable container",
                        "Kitchen", 34.99,
                        "https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=300&h=200&fit=crop"),
                new Product("6", "Yoga Mat Premium",
                        "Extra thick non-slip yoga mat for home workouts, pilates, stretching and meditation, made from eco-friendly TPE material",
                        "Sports", 49.99,
                        "https://images.unsplash.com/photo-1601925260368-ae2f83cf8b7f?w=300&h=200&fit=crop"),
                new Product("7", "Mechanical Keyboard RGB",
                        "Compact tenkeyless mechanical keyboard with Cherry MX switches, programmable RGB lighting for gaming and programming",
                        "Electronics", 159.99,
                        "https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?w=300&h=200&fit=crop"),
                new Product("8", "Portable Bluetooth Speaker",
                        "Waterproof portable speaker with 360-degree surround sound, 20-hour battery, great for outdoor adventures and pool parties",
                        "Electronics", 89.99,
                        "https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=300&h=200&fit=crop"),
                new Product("9", "French Press Coffee Maker",
                        "Borosilicate glass french press for brewing rich full-bodied coffee at home, stainless steel filter for smooth extraction",
                        "Kitchen", 39.99,
                        "https://images.unsplash.com/photo-1517256064527-9d164d0e5961?w=300&h=200&fit=crop"),
                new Product("10", "Backpack Laptop Bag",
                        "Water-resistant travel backpack with padded laptop compartment fits 15.6 inch laptops, multiple pockets for organization",
                        "Bags", 79.99,
                        "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=300&h=200&fit=crop")
        );

        for (Product product : sampleProducts) {
            try {
                productService.indexProduct(product);
            } catch (Exception e) {
                log.warn("Failed to index product '{}': {}", product.name(), e.getMessage());
            }
        }

        log.info("Sample data initialization complete. Indexed {} products.", sampleProducts.size());
    }
}
