package org.example.ssm1.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAddress {
    private Long id;
    private String userId;
    private String address;
    private String label;
    private Boolean isDefault;
    private String createTime;
}
