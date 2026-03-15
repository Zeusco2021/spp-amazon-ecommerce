-- Initialize databases for all microservices
CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS product_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS cart_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS payment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS inventory_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS recommendation_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant permissions to ecommerce user
GRANT ALL PRIVILEGES ON user_db.* TO 'ecommerce'@'%';
GRANT ALL PRIVILEGES ON product_db.* TO 'ecommerce'@'%';
GRANT ALL PRIVILEGES ON cart_db.* TO 'ecommerce'@'%';
GRANT ALL PRIVILEGES ON order_db.* TO 'ecommerce'@'%';
GRANT ALL PRIVILEGES ON payment_db.* TO 'ecommerce'@'%';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'ecommerce'@'%';
GRANT ALL PRIVILEGES ON recommendation_db.* TO 'ecommerce'@'%';

FLUSH PRIVILEGES;
