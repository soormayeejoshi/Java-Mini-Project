package com.visualshop.util;

import com.visualshop.model.Product;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSVProductParser {
    public List<Product> parse(String pathString) {
        List<Product> products = new ArrayList<>();
        Path path = Path.of(pathString);
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                return products;
            }
            List<String> headers = parseCsvLine(headerLine);
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                index.put(headers.get(i).trim(), i);
            }

            String line;
            while ((line = br.readLine()) != null) {
                try {
                    List<String> cols = parseCsvLine(line);
                    int id = parseInt(read(cols, index, "p_id"), 0);
                    String name = read(cols, index, "name");
                    String category = read(cols, index, "products");
                    double price = parseDouble(read(cols, index, "price"), 0.0);
                    String color = read(cols, index, "colour");
                    String brand = read(cols, index, "brand");
                    String img = read(cols, index, "img");
                    String desc = read(cols, index, "description");
                    products.add(new Product(id, name, category, price, color, brand, img, desc));
                } catch (Exception ignored) {
                    // Skip malformed rows.
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV parsing error: " + e.getMessage(), e);
        }
        return products;
    }

    private String read(List<String> row, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i >= row.size()) return "";
        return row.get(i).trim();
    }

    private int parseInt(String v, int fallback) {
        try { return Integer.parseInt(v); } catch (Exception e) { return fallback; }
    }

    private double parseDouble(String v, double fallback) {
        try { return Double.parseDouble(v); } catch (Exception e) { return fallback; }
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    token.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(token.toString());
                token.setLength(0);
            } else {
                token.append(c);
            }
        }
        out.add(token.toString());
        return out;
    }
}
