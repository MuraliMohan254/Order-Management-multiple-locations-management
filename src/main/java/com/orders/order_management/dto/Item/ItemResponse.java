package com.orders.order_management.dto.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String name;
    private BigDecimal price;
    private boolean isAvailable;
}
