package com.orders.order_management.core.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
public class PaginatedResponse<T> {
    private List<T> data;
    private boolean hasMore;
    private long total;
    private int page;
    private int pageSize;
}
