package com.ecommerce.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch document for product indexing.
 * Uses Spanish analyzer for full-text fields to support synonym expansion and stemming.
 * Requirements: 5.1, 5.9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products")
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String name;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String description;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Keyword)
    private String sku;

    private Double price;

    private Double discountPrice;

    @Field(type = FieldType.Long)
    private Long categoryId;

    private Long sellerId;

    @Field(type = FieldType.Keyword)
    private String status;

    private Double averageRating;

    private Integer reviewCount;

    private List<String> imageUrls;

    private LocalDateTime createdAt;
}
