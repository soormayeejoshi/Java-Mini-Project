package com.visualshop.db;

import com.visualshop.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class SchemaInitializer {
    public void init(List<Product> products) {
        try (Connection conn = DatabaseManager.getConnection(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        username VARCHAR(100) UNIQUE NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        role VARCHAR(20) NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS products (
                        id INT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        category VARCHAR(255),
                        price DOUBLE,
                        color VARCHAR(100),
                        brand VARCHAR(100),
                        image_url TEXT,
                        description TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cart (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        user_id INT NOT NULL,
                        product_id INT NOT NULL,
                        quantity INT NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
                        UNIQUE KEY uk_cart_user_product (user_id, product_id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS wishlist (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        user_id INT NOT NULL,
                        product_id INT NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
                        UNIQUE KEY uk_wishlist_user_product (user_id, product_id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        user_id INT NOT NULL,
                        total DOUBLE NOT NULL,
                        tax DOUBLE NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);

            st.executeUpdate("""
                    INSERT IGNORE INTO users(username, password, role)
                    VALUES ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN')
                    """);
            upsertProducts(conn, products);
        } catch (SQLException e) {
            throw new RuntimeException("Database connectivity/init error: " + e.getMessage(), e);
        }
    }

    private void upsertProducts(Connection conn, List<Product> products) throws SQLException {
        String sql = """
                INSERT INTO products(id, name, category, price, color, brand, image_url, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  name = VALUES(name),
                  category = VALUES(category),
                  price = VALUES(price),
                  color = VALUES(color),
                  brand = VALUES(brand),
                  image_url = VALUES(image_url),
                  description = VALUES(description)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int limit = products.size(); // Load ALL products from the dataset
            for (int i = 0; i < limit; i++) {
                Product p = products.get(i);
                ps.setInt(1, p.getId());
                ps.setString(2, p.getName());
                ps.setString(3, p.getCategory());
                ps.setDouble(4, p.getPrice());
                ps.setString(5, p.getColor());
                ps.setString(6, p.getBrand());
                ps.setString(7, p.getImageUrl());
                ps.setString(8, p.getDescription());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
