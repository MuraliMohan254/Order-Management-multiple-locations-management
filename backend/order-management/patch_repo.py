import sys

with open("src/main/java/com/orders/order_management/transaction/repository/OrderRepository.java", "r", encoding="utf-8") as f:
    content = f.read()

import_entity_graph = "import org.springframework.data.jpa.repository.EntityGraph;\nimport org.springframework.lang.Nullable;\nimport org.springframework.data.domain.Page;\nimport org.springframework.data.domain.Pageable;\nimport org.springframework.data.jpa.domain.Specification;\n"

import_index = content.find("import org.springframework.stereotype.Repository;")
if import_index != -1:
    content = content[:import_index] + import_entity_graph + content[import_index:]

content = content.replace("Optional<Order> findByTableIdAndStatusIn(UUID tableId, List<OrderStatus> statuses);", "@EntityGraph(attributePaths = {\"items\", \"table\"})\n    Optional<Order> findByTableIdAndStatusIn(UUID tableId, List<OrderStatus> statuses);")

content = content.replace("@Query(\"SELECT o FROM Order o WHERE o.status IN :statuses AND (o.kitchenStatus IS NULL OR (o.kitchenStatus != 'SERVED' AND o.kitchenStatus != 'CANCELLED')) ORDER BY o.createdAt ASC\")\n    List<Order> findActiveOrdersByStatusIn(@Param(\"statuses\") List<OrderStatus> statuses);", "@EntityGraph(attributePaths = {\"items\", \"table\"})\n    @Query(\"SELECT o FROM Order o WHERE o.status IN :statuses AND (o.kitchenStatus IS NULL OR (o.kitchenStatus != 'SERVED' AND o.kitchenStatus != 'CANCELLED')) ORDER BY o.createdAt ASC\")\n    List<Order> findActiveOrdersByStatusIn(@Param(\"statuses\") List<OrderStatus> statuses);")

interface_body = """    @EntityGraph(attributePaths = {"items", "table"})
    Page<Order> findAll(@Nullable Specification<Order> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "table"})
    Optional<Order> findByTableIdAndStatusIn(UUID tableId, List<OrderStatus> statuses);"""

content = content.replace("@EntityGraph(attributePaths = {\"items\", \"table\"})\n    Optional<Order> findByTableIdAndStatusIn(UUID tableId, List<OrderStatus> statuses);", interface_body)

with open("src/main/java/com/orders/order_management/transaction/repository/OrderRepository.java", "w", encoding="utf-8") as f:
    f.write(content)
