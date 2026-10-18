package com.cloudnative.ms_notifications.DTO;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Message implements Serializable {
    private String user;
    private String content;
    private String endpoint;
    private LocalDateTime date;

}
