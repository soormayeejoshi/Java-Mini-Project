package com.visualshop.service;

import com.visualshop.config.AppConfig;
import com.visualshop.model.CartItem;
import com.visualshop.model.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartService {
    private final HashMap<Integer, CartItem> items = new HashMap<>();

    public void add(Product product) {
        CartItem item = items.get(product.getId());
        if (item == null) {
            items.put(product.getId(), new CartItem(product, 1));
        } else {
            item.setQuantity(item.getQuantity() + 1);
        }
    }

    public List<CartItem> getItems() {
        return new ArrayList<>(items.values());
    }

    public void clear() {
        items.clear();
    }

    public double subtotal() {
        double sum = 0;
        for (Map.Entry<Integer, CartItem> e : items.entrySet()) {
            sum += e.getValue().getSubtotal();
        }
        return sum;
    }

    public double tax() {
        return subtotal() * AppConfig.TAX_RATE;
    }

    public double total() {
        return subtotal() + tax();
    }
}
