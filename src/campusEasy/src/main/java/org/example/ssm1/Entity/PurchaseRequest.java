package org.example.ssm1.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseRequest {
    private Long requestId;
    private String productId;
    private String sellerId;
    private String buyerId;
    private String status;
    private String createTime;
}
