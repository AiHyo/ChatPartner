package com.aih.chatpartner.model.dto.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class IdBatchRequest implements Serializable {
    private List<Long> ids;
}
