package org.example.ssm1.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Product implements Serializable {
    private static final long serialVersionUID = 1L;

    private String productId;
    private String userId;
    private String name;
    private double price;
    private String description;
    private String status;
    private String classification;
    private String createTime;
    private String imageUrl;
    private String pendingBuyerId;
    private Integer version;
}
