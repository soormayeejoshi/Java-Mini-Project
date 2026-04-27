package com.visualshop.controller;

import com.visualshop.model.Product;
import com.visualshop.repository.ProductRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AdminController {
    private final ProductRepository productRepository;
    private final Runnable onLogout;
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final Parent view;

    public AdminController(ProductRepository productRepository, Runnable onLogout) {
        this.productRepository = productRepository;
        this.onLogout = onLogout;
        this.products.setAll(productRepository.findAll());
        this.view = build();
    }

    private Parent build() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Button logout = new Button("Logout");
        logout.setOnAction(e -> onLogout.run());
        root.setTop(new HBox(10, new Label("Admin Dashboard - Inventory CRUD"), logout));

        ListView<Product> list = new ListView<>(products);
        list.setCellFactory(x -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " | " + item.getName() + " | " + item.getPrice());
            }
        });
        root.setCenter(list);

        TextField id = new TextField();
        TextField name = new TextField();
        TextField category = new TextField();
        TextField price = new TextField();
        TextField color = new TextField();
        TextField brand = new TextField();
        TextField image = new TextField();
        TextField desc = new TextField();
        id.setPromptText("ID");
        name.setPromptText("Name");
        category.setPromptText("Category");
        price.setPromptText("Price");
        color.setPromptText("Color");
        brand.setPromptText("Brand");
        image.setPromptText("Image URL");
        desc.setPromptText("Description");

        Button add = new Button("Add Product");
        Button delete = new Button("Delete by ID");
        Label status = new Label();
        add.setOnAction(e -> {
            try {
                Product p = new Product(
                        Integer.parseInt(id.getText()),
                        name.getText(),
                        category.getText(),
                        Double.parseDouble(price.getText()),
                        color.getText(),
                        brand.getText(),
                        image.getText(),
                        desc.getText()
                );
                productRepository.addProduct(p);
                products.setAll(productRepository.findAll());
                status.setText("Product added.");
            } catch (Exception ex) {
                status.setText("Add failed: " + ex.getMessage());
            }
        });
        delete.setOnAction(e -> {
            try {
                productRepository.deleteProduct(Integer.parseInt(id.getText()));
                products.setAll(productRepository.findAll());
                status.setText("Product deleted.");
            } catch (Exception ex) {
                status.setText("Delete failed: " + ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setVgap(8);
        form.setHgap(8);
        form.addRow(0, id, name, category, price);
        form.addRow(1, color, brand, image, desc);
        VBox bottom = new VBox(8, form, new HBox(8, add, delete), status);
        bottom.setPadding(new Insets(12));
        root.setBottom(bottom);

        return root;
    }

    public Parent getView() {
        return view;
    }
}
