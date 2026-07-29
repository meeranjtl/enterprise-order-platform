package com.enterprise.order.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerLookupDTO {
    private Long id;
    private String email;
    private String status;
}
