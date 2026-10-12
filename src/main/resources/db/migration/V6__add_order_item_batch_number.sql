-- ==============================================================================
-- V6: Add batch_number to order_items for Kitchen Display & Thermal KOT tracking
-- ==============================================================================

ALTER TABLE public.order_items 
ADD COLUMN IF NOT EXISTS batch_number integer DEFAULT 1 NOT NULL;

CREATE INDEX IF NOT EXISTS idx_order_items_order_batch 
ON public.order_items (order_id, batch_number);
