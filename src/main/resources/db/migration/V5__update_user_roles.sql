ALTER TABLE user_roles DROP CONSTRAINT user_roles_role_check;
ALTER TABLE user_roles ADD CONSTRAINT user_roles_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'CASHIER'::character varying, 'KITCHEN'::character varying])::text[])));
