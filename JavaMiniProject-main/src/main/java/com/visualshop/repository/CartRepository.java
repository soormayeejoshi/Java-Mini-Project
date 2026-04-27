package com.visualshop.repository;

import com.visualshop.db.DatabaseManager;
import com.visualshop.model.CartItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class CartRepository {
    public void replaceCart(int userId, List<CartItem> items) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement clear = conn.prepareStatement("DELETE FROM cart WHERE user_id=?")) {
                clear.setInt(1, userId);
                clear.executeUpdate();
            }

            String sql = "INSERT INTO cart(user_id, product_id, quantity) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (CartItem item : items) {
                    ps.setInt(1, userId);
                    ps.setInt(2, item.getProduct().getId());
                    ps.setInt(3, item.getQuantity());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Cart persistence failed: " + e.getMessage(), e);
        }
    }
    public void addItem(int userId, int productId, int quantity) {
        String sql = "INSERT INTO cart(user_id, product_id, quantity) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE quantity = quantity + ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.setInt(4, quantity);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Add to cart failed: " + e.getMessage(), e);
        }
    }

    public void removeItem(int userId, int productId) {
        String sql = "DELETE FROM cart WHERE user_id=? AND product_id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Remove from cart failed: " + e.getMessage(), e);
        }
    }
    
    public List<CartItem> getCart(int userId) {
        List<CartItem> result = new java.util.ArrayList<>();
        String sql = "SELECT p.*, c.quantity FROM products p JOIN cart c ON p.id = c.product_id WHERE c.user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.visualshop.model.Product p = new com.visualshop.model.Product(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getDouble("price"),
                            rs.getString("color"),
                            rs.getString("brand"),
                            rs.getString("image_url"),
                            rs.getString("description")
                    );
                    result.add(new CartItem(p, rs.getInt("quantity")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Get cart failed: " + e.getMessage(), e);
        }
        return result;
    }
}
