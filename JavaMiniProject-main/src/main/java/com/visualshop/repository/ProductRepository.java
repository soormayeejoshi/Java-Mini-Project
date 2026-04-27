package com.visualshop.repository;

import com.visualshop.db.DatabaseManager;
import com.visualshop.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {
    public List<Product> findAll() {
        List<Product> result = new ArrayList<>();
        String sql = "SELECT id, name, category, price, color, brand, image_url, description FROM products";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
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
        } catch (SQLException e) {
            throw new RuntimeException("Database connectivity error while loading products: " + e.getMessage(), e);
        }
        return result;
    }

    public List<Product> findProductsPaginated(String category, int limit, int offset) {
        List<Product> result = new ArrayList<>();
        boolean hasCategory = category != null && !category.equalsIgnoreCase("All");
        String sql = "SELECT id, name, category, price, color, brand, image_url, description FROM products " +
                     (hasCategory ? "WHERE category = ? " : "") +
                     "LIMIT ? OFFSET ?";
                     
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
             
            int paramIndex = 1;
            if (hasCategory) {
                ps.setString(paramIndex++, category);
            }
            ps.setInt(paramIndex++, limit);
            ps.setInt(paramIndex, offset);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
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
            throw new RuntimeException("Database error loading paginated products: " + e.getMessage(), e);
        }
        return result;
    }

    public void addProduct(Product p) {
        String sql = "INSERT INTO products(id, name, category, price, color, brand, image_url, description) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getId());
            ps.setString(2, p.getName());
            ps.setString(3, p.getCategory());
            ps.setDouble(4, p.getPrice());
            ps.setString(5, p.getColor());
            ps.setString(6, p.getBrand());
            ps.setString(7, p.getImageUrl());
            ps.setString(8, p.getDescription());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Add product failed: " + e.getMessage(), e);
        }
    }

    public void deleteProduct(int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM products WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete product failed: " + e.getMessage(), e);
        }
    }
}
