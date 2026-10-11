-- V2: Add settled_by to payments table
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS settled_by character varying(100);
