package com.mall.cart.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.cart.client.dto.ProductDetailDTO;
import com.mall.cart.client.dto.ProductStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductDetailDTOJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesProductServiceDetailResponse() throws Exception {
        String json = """
                {
                  "productId": "P30001",
                  "sellerId": "U20001",
                  "title": "罗技 G102 鼠标",
                  "subtitle": null,
                  "mainImage": "/images/p30001.jpg",
                  "categoryId": "C10001",
                  "status": "ON_SALE",
                  "price": 5900,
                  "skus": [
                    {
                      "skuId": "S40001",
                      "specName": "黑色",
                      "price": 5900,
                      "stockAvailable": 12,
                      "stockLocked": 0
                    }
                  ]
                }
                """;

        ProductDetailDTO product = objectMapper.readValue(json, ProductDetailDTO.class);

        assertEquals("P30001", product.getProductId());
        assertEquals(ProductStatus.ON_SALE, product.getStatus());
        assertEquals(5900L, product.getPrice());
        assertEquals("S40001", product.getSkus().get(0).getSkuId());
        assertEquals(12, product.getSkus().get(0).getStockAvailable());
    }
}
