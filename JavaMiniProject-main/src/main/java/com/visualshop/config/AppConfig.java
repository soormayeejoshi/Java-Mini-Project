package com.visualshop.config;

public final class AppConfig {
    private AppConfig() {
    }

    public static final String CSV_PATH = new java.io.File("JavaMiniProject-main/Dataset/Fashion Dataset v2.csv").exists() 
        ? "JavaMiniProject-main/Dataset/Fashion Dataset v2.csv" 
        : "Dataset/Fashion Dataset v2.csv";
    public static final String DB_URL = "jdbc:mysql://localhost:3306/visual_shop";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "root";
    public static final double TAX_RATE = 0.08;
}

