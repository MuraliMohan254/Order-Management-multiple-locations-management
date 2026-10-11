-- V5: Add performance composite indexes for multi-tenant scale (25+ outlets) and high volume reporting
-- Optimizes order filtering, kitchen active queues, billing lookups, and financial aggregations

-- 1. Orders: Compound indexes for date range, status, and outlet queries
CREATE INDEX IF NOT EXISTS idx_orders_outlet_created_at_status 
    ON public.orders (outlet_id, created_at DESC, status);

CREATE INDEX IF NOT EXISTS idx_orders_outlet_status_kitchen 
    ON public.orders (outlet_id, status, kitchen_status);

CREATE INDEX IF NOT EXISTS idx_orders_table_status 
    ON public.orders (table_id, status);

-- 2. Payments: Compound indexes for multi-tenant payment reconciliation
CREATE INDEX IF NOT EXISTS idx_payments_outlet_created_status 
    ON public.payments (outlet_id, created_at DESC, payment_status);

CREATE INDEX IF NOT EXISTS idx_payments_order_status 
    ON public.payments (order_id, payment_status);

-- 3. Order Items: Accelerates category sales and fast/slow mover aggregations
CREATE INDEX IF NOT EXISTS idx_order_items_item_order 
    ON public.order_items (item_id, order_id);

-- 4. Tables: Speeds up table status lookups per outlet in POS and dine-in flows
CREATE INDEX IF NOT EXISTS idx_restaurant_tables_outlet_status 
    ON public.restaurant_tables (outlet_id, status);
