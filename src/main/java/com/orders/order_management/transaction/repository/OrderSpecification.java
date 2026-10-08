package com.orders.order_management.transaction.repository;


import com.orders.order_management.core.enums.OrderStatus;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.menu.entity.Item;
import com.orders.order_management.transaction.entity.Order;
import com.orders.order_management.transaction.entity.OrderItem;
import com.orders.order_management.transaction.entity.RestaurantTable;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderSpecification {

    public static Specification<Order> filter(
            List<OrderStatus> statuses,
            OrderType orderType,
            String search) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Status Filter
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }

            // 2. Order Type Filter (DINE_IN vs TAKE_AWAY)
            if (orderType != null) {
                predicates.add(cb.equal(root.get("orderType"), orderType));
            }

            // 3. Search Filter across order number, table number, grand total, items, order type, and status
            if (search != null && !search.trim().isEmpty()) {
                String raw = search.trim();
                String pattern = "%" + raw.toLowerCase() + "%";
                List<Predicate> searchPredicates = new ArrayList<>();

                // A. Order Number (case-insensitive substring match)
                searchPredicates.add(cb.like(cb.lower(root.get("orderNumber")), pattern));

                // B. Table Number (via left join)
                Join<Order, RestaurantTable> tableJoin = root.join("table", JoinType.LEFT);
                searchPredicates.add(cb.like(cb.lower(tableJoin.get("tableNumber")), pattern));

                // If user typed e.g. "Table 4" or "T4" or "T-4", also search the stripped number "4"
                String strippedTable = raw.replaceAll("(?i)^(table|tbl|t)[\\s\\-_]*", "").trim();
                if (!strippedTable.isEmpty() && !strippedTable.equalsIgnoreCase(raw)) {
                    searchPredicates.add(cb.like(cb.lower(tableJoin.get("tableNumber")), "%" + strippedTable.toLowerCase() + "%"));
                }

                // C. Item Name search via subquery (ensures no row duplication in pagination)
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<OrderItem> oiRoot = subquery.from(OrderItem.class);
                subquery.select(oiRoot.get("id"))
                        .where(
                                cb.equal(oiRoot.get("order"), root),
                                cb.like(cb.lower(oiRoot.get("item").get("name")), pattern)
                        );
                searchPredicates.add(cb.exists(subquery));

                // D. Amount (grand total / subtotal) - Safe numeric comparison without DB cast errors
                String numericOnly = raw.replaceAll("[^0-9.]", "").trim();
                if (!numericOnly.isEmpty() && !numericOnly.equals(".")) {
                    try {
                        BigDecimal searchAmount = new BigDecimal(numericOnly);
                        searchPredicates.add(cb.equal(root.get("grandTotal"), searchAmount));
                        searchPredicates.add(cb.equal(root.get("subTotal"), searchAmount));
                    } catch (NumberFormatException ignored) {}
                }

                // E. Search by Order Type Keyword (e.g. "takeaway", "dine")
                String lowerRaw = raw.toLowerCase();
                if (lowerRaw.contains("take")) {
                    searchPredicates.add(cb.equal(root.get("orderType"), OrderType.TAKE_AWAY));
                } else if (lowerRaw.contains("dine")) {
                    searchPredicates.add(cb.equal(root.get("orderType"), OrderType.DINE_IN));
                }

                // F. Search by Status Keyword (e.g. "billed", "preparing", "ready")
                for (OrderStatus os : OrderStatus.values()) {
                    if (os.name().toLowerCase().contains(lowerRaw)) {
                        searchPredicates.add(cb.equal(root.get("status"), os));
                    }
                }

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
