--
-- PostgreSQL database dump
--

-- Dumped from database version 15.4
-- Dumped by pg_dump version 15.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    is_active boolean NOT NULL,
    name character varying(100) NOT NULL
);


--
-- Name: items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.items (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    is_available boolean NOT NULL,
    name character varying(150) NOT NULL,
    price numeric(15,2) NOT NULL,
    category_id uuid NOT NULL,
    description character varying(500)
);


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_items (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    notes character varying(255),
    quantity integer NOT NULL,
    total_price numeric(15,2) NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    item_id uuid NOT NULL,
    order_id uuid NOT NULL
);


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    discount_amount numeric(15,2) NOT NULL,
    grand_total numeric(15,2) NOT NULL,
    kitchen_status character varying(20),
    order_number character varying(50) NOT NULL,
    order_type character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    sub_total numeric(15,2) NOT NULL,
    tax_amount numeric(15,2) NOT NULL,
    table_id uuid,
    outlet_id character varying(36) NOT NULL,
    CONSTRAINT orders_order_type_check CHECK (((order_type)::text = ANY (ARRAY[('DINE_IN'::character varying)::text, ('TAKE_AWAY'::character varying)::text]))),
    CONSTRAINT orders_status_check CHECK (((status)::text = ANY (ARRAY[('SOFT_ORDER'::character varying)::text, ('CONFIRMED'::character varying)::text, ('PREPARING'::character varying)::text, ('READY'::character varying)::text, ('SERVED'::character varying)::text, ('BILLED'::character varying)::text, ('PAID'::character varying)::text, ('CANCELLED'::character varying)::text])))
);


--
-- Name: outlets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outlets (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(50),
    address text,
    contact_number character varying(50),
    is_active boolean DEFAULT true,
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    amount_paid numeric(15,2) NOT NULL,
    payment_method character varying(50) NOT NULL,
    payment_status character varying(20) NOT NULL,
    order_id uuid NOT NULL,
    outlet_id character varying(36) NOT NULL,
    CONSTRAINT payments_payment_method_check CHECK (((payment_method)::text = ANY (ARRAY[('CASH'::character varying)::text, ('CARD'::character varying)::text, ('UPI'::character varying)::text]))),
    CONSTRAINT payments_payment_status_check CHECK (((payment_status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('SUCCESS'::character varying)::text, ('FAILED'::character varying)::text])))
);


--
-- Name: restaurant_tables; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.restaurant_tables (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    qr_token uuid NOT NULL,
    status character varying(50) NOT NULL,
    table_number character varying(20) NOT NULL,
    outlet_id character varying(36) NOT NULL,
    CONSTRAINT restaurant_tables_status_check CHECK (((status)::text = ANY (ARRAY[('AVAILABLE'::character varying)::text, ('OCCUPIED'::character varying)::text, ('RESERVED'::character varying)::text])))
);


--
-- Name: system_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_config (
    id bigint NOT NULL,
    default_discount_percentage numeric(38,2),
    kitchen_workflow_enabled boolean NOT NULL,
    tax_name character varying(255),
    tax_rate numeric(38,2),
    thermal_printing_enabled boolean DEFAULT false,
    thermal_paper_size character varying(20) DEFAULT '80mm'::character varying,
    printer_name character varying(255),
    outlet_id character varying(36),
    restaurant_name character varying(255) DEFAULT 'WildBar'::character varying
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id bigint NOT NULL,
    role character varying(255),
    CONSTRAINT user_roles_role_check CHECK (((role)::text = ANY (ARRAY[('ADMIN'::character varying)::text, ('CASHIER'::character varying)::text, ('KITCHEN'::character varying)::text])))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    last_login timestamp(6) with time zone,
    password character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    outlet_id character varying(36)
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.users ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: items items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items
    ADD CONSTRAINT items_pkey PRIMARY KEY (id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: outlets outlets_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outlets
    ADD CONSTRAINT outlets_code_key UNIQUE (code);


--
-- Name: outlets outlets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outlets
    ADD CONSTRAINT outlets_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: restaurant_tables restaurant_tables_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tables
    ADD CONSTRAINT restaurant_tables_pkey PRIMARY KEY (id);


--
-- Name: system_config system_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_config
    ADD CONSTRAINT system_config_pkey PRIMARY KEY (id);


--
-- Name: system_config uk_config_outlet; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_config
    ADD CONSTRAINT uk_config_outlet UNIQUE (outlet_id);


--
-- Name: restaurant_tables uk_tables_number_outlet; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tables
    ADD CONSTRAINT uk_tables_number_outlet UNIQUE (table_number, outlet_id);


--
-- Name: user_roles ukkpjrhqbpn9eqscssk9ttp7glu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT ukkpjrhqbpn9eqscssk9ttp7glu UNIQUE (user_id, role);


--
-- Name: orders uknthkiu7pgmnqnu86i2jyoe2v7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT uknthkiu7pgmnqnu86i2jyoe2v7 UNIQUE (order_number);


--
-- Name: users ukr43af9ap4edm43mmtq01oddj6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT ukr43af9ap4edm43mmtq01oddj6 UNIQUE (username);


--
-- Name: restaurant_tables uksu0vhnju8l3s3p4rb3uob0g2n; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tables
    ADD CONSTRAINT uksu0vhnju8l3s3p4rb3uob0g2n UNIQUE (qr_token);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_order_items_item_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_items_item_id ON public.order_items USING btree (item_id);


--
-- Name: idx_order_items_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_items_order_id ON public.order_items USING btree (order_id);


--
-- Name: idx_orders_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_created_at ON public.orders USING btree (created_at);


--
-- Name: idx_orders_kitchen_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_kitchen_status ON public.orders USING btree (kitchen_status);


--
-- Name: idx_orders_order_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_order_number ON public.orders USING btree (order_number);


--
-- Name: idx_orders_order_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_order_type ON public.orders USING btree (order_type);


--
-- Name: idx_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_status ON public.orders USING btree (status);


--
-- Name: idx_orders_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_status_created_at ON public.orders USING btree (status, created_at);


--
-- Name: idx_orders_table_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_table_id ON public.orders USING btree (table_id);


--
-- Name: payments fk81gagumt0r8y3rmudcgpbk42l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk81gagumt0r8y3rmudcgpbk42l FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: order_items fk88tn2oqcxl1034banqif9r70x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fk88tn2oqcxl1034banqif9r70x FOREIGN KEY (item_id) REFERENCES public.items(id);


--
-- Name: orders fk8nathddqyn5kpjldujiu5lhhv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk8nathddqyn5kpjldujiu5lhhv FOREIGN KEY (table_id) REFERENCES public.restaurant_tables(id);


--
-- Name: system_config fk_config_outlet; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_config
    ADD CONSTRAINT fk_config_outlet FOREIGN KEY (outlet_id) REFERENCES public.outlets(id);


--
-- Name: orders fk_orders_outlet; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_outlet FOREIGN KEY (outlet_id) REFERENCES public.outlets(id);


--
-- Name: payments fk_payments_outlet; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk_payments_outlet FOREIGN KEY (outlet_id) REFERENCES public.outlets(id);


--
-- Name: restaurant_tables fk_tables_outlet; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.restaurant_tables
    ADD CONSTRAINT fk_tables_outlet FOREIGN KEY (outlet_id) REFERENCES public.outlets(id);


--
-- Name: users fk_users_outlet; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_outlet FOREIGN KEY (outlet_id) REFERENCES public.outlets(id);


--
-- Name: order_items fkbioxgbv59vetrxe0ejfubep1w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: items fkjcdcde7htb3tyjgouo4g9xbmr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items
    ADD CONSTRAINT fkjcdcde7htb3tyjgouo4g9xbmr FOREIGN KEY (category_id) REFERENCES public.categories(id);


--
-- PostgreSQL database dump complete
--


--
-- Default Seed Data
--
INSERT INTO public.outlets (id, name, code, is_active, created_at, updated_at) 
VALUES ('default-outlet-123', 'Main Outlet', 'MAIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;