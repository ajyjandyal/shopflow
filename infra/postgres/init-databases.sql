-- Runs automatically the FIRST time the Docker PostgreSQL volume is created.
-- (Homebrew users: use scripts/create-databases.sh instead.)
CREATE DATABASE shopflow_users;
CREATE DATABASE shopflow_products;
CREATE DATABASE shopflow_orders;
