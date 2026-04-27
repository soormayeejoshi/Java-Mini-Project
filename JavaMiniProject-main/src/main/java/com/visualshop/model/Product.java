package com.visualshop.model;

public class Product {
    private final int id;
    private final String name;
    private final String category;
    private final double price;
    private final String color;
    private final String brand;
    private final String imageUrl;
    private final String description;

    public Product(int id, String name, String category, double price, String color, String brand, String imageUrl, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.color = color;
        this.brand = brand;
        this.imageUrl = imageUrl;
        this.description = description;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public String getColor() { return color; }
    public String getBrand() { return brand; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
}
