package com.visualshop.repository;

import com.visualshop.db.DatabaseManager;
import com.visualshop.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WishlistRepository {
    
    public void addItem(int userId, int productId) {
        String sql = "INSERT IGNORE INTO wishlist(user_id, product_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Add to wishlist failed: " + e.getMessage(), e);
        }
    }
    
    public void removeItem(int userId, int productId) {
        String sql = "DELETE FROM wishlist WHERE user_id=? AND product_id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Remove from wishlist failed: " + e.getMessage(), e);
        }
    }
    
    public List<Product> getWishlist(int userId) {
        List<Product> result = new ArrayList<>();
        String sql = "SELECT p.* FROM products p JOIN wishlist w ON p.id = w.product_id WHERE w.user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    result.add(new Product(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getDouble("price"),
                            rs.getString("color"),
                            rs.getString("brand"),
                            rs.getString("image_url"),
                            rs.getString("description")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Get wishlist failed: " + e.getMessage(), e);
        }
        return result;
    }
}
