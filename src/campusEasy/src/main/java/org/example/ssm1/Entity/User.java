package org.example.ssm1.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private String userId;
    private String name;
    private String age;
    private String sex;
    private String address;
    private String phone;
    private String password;
    private String createTime;
}
