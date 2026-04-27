package com.visualshop.repository;

import com.visualshop.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class OrderRepository {
    public void saveOrder(int userId, double subtotal, double tax) {
        String sql = "INSERT INTO orders(user_id, total, tax) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDouble(2, subtotal + tax);
            ps.setDouble(3, tax);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Order save failed: " + e.getMessage(), e);
        }
    }
}
