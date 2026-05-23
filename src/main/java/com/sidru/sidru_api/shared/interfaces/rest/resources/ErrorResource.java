package com.sidru.sidru_api.shared.interfaces.rest.resources;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
public class ErrorResource {
    private String code;
    private String message;
    private List<String> details;
    private LocalDateTime timeStamp;
}
