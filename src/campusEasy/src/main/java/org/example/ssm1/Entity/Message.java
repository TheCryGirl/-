package org.example.ssm1.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    private Long id;
    private String senderId;
    private String receiverId;
    private String content;
    private String createTime;
    private Boolean isRead;
}
