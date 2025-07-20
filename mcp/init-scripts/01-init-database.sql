-- Create additional schemas if needed
CREATE SCHEMA IF NOT EXISTS analytics;
CREATE SCHEMA IF NOT EXISTS reporting;

-- Create extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create extension for full-text search
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Grant permissions
GRANT USAGE ON SCHEMA public TO mcp_user;
GRANT USAGE ON SCHEMA analytics TO mcp_user;
GRANT USAGE ON SCHEMA reporting TO mcp_user;

GRANT CREATE ON SCHEMA public TO mcp_user;
GRANT CREATE ON SCHEMA analytics TO mcp_user;
GRANT CREATE ON SCHEMA reporting TO mcp_user;

-- Create sample tables for demonstration
CREATE TABLE IF NOT EXISTS public.sample_customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20),
    address TEXT,
    city VARCHAR(50),
    state VARCHAR(50),
    zip_code VARCHAR(10),
    country VARCHAR(50) DEFAULT 'USA',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.sample_orders (
    id SERIAL PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES sample_customers(id),
    order_date DATE NOT NULL DEFAULT CURRENT_DATE,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    shipping_address TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.sample_products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    price DECIMAL(10,2) NOT NULL,
    stock_quantity INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.sample_order_items (
    id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES sample_orders(id),
    product_id INTEGER NOT NULL REFERENCES sample_products(id),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) GENERATED ALWAYS AS (quantity * unit_price) STORED
);

-- Create indexes for better performance
CREATE INDEX idx_customers_email ON public.sample_customers(email);
CREATE INDEX idx_customers_name ON public.sample_customers(first_name, last_name);
CREATE INDEX idx_orders_customer_id ON public.sample_orders(customer_id);
CREATE INDEX idx_orders_date ON public.sample_orders(order_date);
CREATE INDEX idx_orders_status ON public.sample_orders(status);
CREATE INDEX idx_products_category ON public.sample_products(category);
CREATE INDEX idx_products_price ON public.sample_products(price);
CREATE INDEX idx_order_items_order_id ON public.sample_order_items(order_id);
CREATE INDEX idx_order_items_product_id ON public.sample_order_items(product_id);

-- Insert sample data
INSERT INTO public.sample_customers (first_name, last_name, email, phone, city, state) VALUES
('John', 'Doe', 'john.doe@email.com', '555-0101', 'New York', 'NY'),
('Jane', 'Smith', 'jane.smith@email.com', '555-0102', 'Los Angeles', 'CA'),
('Bob', 'Johnson', 'bob.johnson@email.com', '555-0103', 'Chicago', 'IL'),
('Alice', 'Brown', 'alice.brown@email.com', '555-0104', 'Houston', 'TX'),
('Charlie', 'Davis', 'charlie.davis@email.com', '555-0105', 'Phoenix', 'AZ');

INSERT INTO public.sample_products (name, description, category, price, stock_quantity) VALUES
('Laptop Pro', 'High-performance laptop for professionals', 'Electronics', 1299.99, 50),
('Wireless Mouse', 'Ergonomic wireless mouse', 'Electronics', 29.99, 200),
('Office Chair', 'Comfortable ergonomic office chair', 'Furniture', 299.99, 25),
('Coffee Mug', 'Ceramic coffee mug with company logo', 'Office Supplies', 12.99, 100),
('Notebook Set', 'Set of 3 premium notebooks', 'Office Supplies', 24.99, 75);

INSERT INTO public.sample_orders (customer_id, order_date, total_amount, status) VALUES
((SELECT id FROM public.sample_customers WHERE email = 'john.doe@email.com'), '2024-01-15', 1329.98, 'completed'),
((SELECT id FROM public.sample_customers WHERE email = 'jane.smith@email.com'), '2024-01-16', 342.98, 'completed'),
((SELECT id FROM public.sample_customers WHERE email = 'bob.johnson@email.com'), '2024-01-17', 37.98, 'pending'),
((SELECT id FROM public.sample_customers WHERE email = 'alice.brown@email.com'), '2024-01-18', 299.99, 'shipped'),
((SELECT id FROM public.sample_customers WHERE email = 'charlie.davis@email.com'), '2024-01-19', 54.97, 'processing');

-- Insert order items (this would normally be done through the application)
WITH order_data AS (
  SELECT id as order_id FROM public.sample_orders LIMIT 1
)
INSERT INTO public.sample_order_items (order_id, product_id, quantity, unit_price)
SELECT
  (SELECT order_id FROM order_data),
  p.id,
  1,
  p.price
FROM public.sample_products p
WHERE p.name IN ('Laptop Pro', 'Wireless Mouse');