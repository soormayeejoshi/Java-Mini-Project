package com.visualshop;

import com.visualshop.config.AppConfig;
import com.visualshop.controller.AdminController;
import com.visualshop.controller.AuthController;
import com.visualshop.controller.ShopController;
import com.visualshop.db.SchemaInitializer;
import com.visualshop.model.Product;
import com.visualshop.model.User;
import com.visualshop.repository.ProductRepository;
import com.visualshop.repository.UserRepository;
import com.visualshop.util.CSVProductParser;
import javafx.application.Application;
import javafx.scene.Scene;

import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class MainApplication extends Application {
    private Stage stage;
    private final UserRepository userRepository = new UserRepository();
    private final ProductRepository productRepository = new ProductRepository();
    private List<Product> productCache = new ArrayList<>();

    private javafx.scene.layout.BorderPane mainRoot;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.stage.setTitle("Visual Product Discovery & Shopping Management System");
        bootstrapData();
        
        mainRoot = new javafx.scene.layout.BorderPane();
        Scene mainScene = new Scene(mainRoot, 1200, 780);
        stage.setScene(mainScene);
        
        showAuth();
        stage.show();
    }

    private void bootstrapData() {
        List<Product> parsed = new CSVProductParser().parse(AppConfig.CSV_PATH);
        new SchemaInitializer().init(parsed);
        productCache = productRepository.findAll();
    }

    public void showAuth() {
        AuthController controller = new AuthController(userRepository, this::showForUser);
        mainRoot.setCenter(controller.getView());
    }

    public void showForUser(User user) {
        if (user.isAdmin()) {
            AdminController admin = new AdminController(productRepository, this::showAuth);
            mainRoot.setCenter(admin.getView());
        } else {
            ShopController shop = new ShopController(user, productRepository, this::showAuth);
            mainRoot.setCenter(shop.getView());
        }
    }

    public static void main(String[] args) {
        try {
            launch(args);
        } catch (Exception e) {
            System.err.println("Application startup failed:");
            e.printStackTrace();
        }
    }
}
