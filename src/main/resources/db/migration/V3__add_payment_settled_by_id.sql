-- V3: Add settled_by_id foreign key reference to payments table
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS settled_by_id bigint;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_payments_settled_by_user'
    ) THEN
        ALTER TABLE public.payments 
        ADD CONSTRAINT fk_payments_settled_by_user 
        FOREIGN KEY (settled_by_id) 
        REFERENCES public.users(id) 
        ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_payments_settled_by_id ON public.payments(settled_by_id);
