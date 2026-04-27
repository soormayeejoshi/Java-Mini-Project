package com.visualshop.controller;

import com.visualshop.model.User;
import com.visualshop.repository.UserRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.util.function.Consumer;

public class AuthController {
    private final UserRepository userRepository;
    private final Consumer<User> onSuccess;
    private final Parent view;

    // Track which form to show
    private final VBox loginCard = new VBox(16);
    private final VBox registerCard = new VBox(16);

    public AuthController(UserRepository userRepository, Consumer<User> onSuccess) {
        this.userRepository = userRepository;
        this.onSuccess = onSuccess;
        this.view = build();
    }

    private Parent build() {
        // ── Outer full-screen container ──────────────────────────────────────
        StackPane root = new StackPane();
        root.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #1a1a2e, #16213e, #0f3460);"
        );

        // ── Brand header ──────────────────────────────────────────────────────
        Label brand = new Label("VisualShop");
        brand.setStyle(
            "-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #ffffff;" +
            "-fx-font-family: 'Segoe UI';"
        );
        Label tagline = new Label("Discover Fashion, Elevate Your Style");
        tagline.setStyle(
            "-fx-font-size: 13px; -fx-text-fill: #a0aec0; -fx-font-family: 'Segoe UI';"
        );

        VBox branding = new VBox(4, brand, tagline);
        branding.setAlignment(Pos.CENTER);

        // ── Build forms ───────────────────────────────────────────────────────
        buildLoginCard();
        buildRegisterCard();

        registerCard.setVisible(false);
        registerCard.setManaged(false);

        // Container that swaps between login and register
        StackPane formStack = new StackPane(loginCard, registerCard);
        formStack.setMaxWidth(380);

        VBox centerBox = new VBox(28, branding, formStack);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setMaxWidth(400);

        root.getChildren().add(centerBox);
        StackPane.setAlignment(centerBox, Pos.CENTER);

        return root;
    }

    private void buildLoginCard() {
        styleCard(loginCard);

        Label title = new Label("Sign In");
        title.setStyle(
            "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1a202c;" +
            "-fx-font-family: 'Segoe UI';"
        );

        TextField username = styledField("Username");
        PasswordField password = styledPassword("Password");
        Label status = new Label();
        status.setStyle("-fx-font-size: 11px; -fx-text-fill: #718096;");
        status.setWrapText(true);
        status.setTextAlignment(TextAlignment.CENTER);

        Button loginBtn = primaryButton("Sign In");
        loginBtn.setOnAction(e -> {
            User user = userRepository.login(username.getText(), password.getText());
            if (user != null) {
                onSuccess.accept(user);
            } else {
                status.setText("Invalid credentials. Please try again.");
                status.setStyle("-fx-font-size: 11px; -fx-text-fill: #e53e3e;");
            }
        });

        Label switchLabel = new Label("Don't have an account?");
        switchLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #718096;");
        Button switchBtn = linkButton("Register");
        switchBtn.setOnAction(e -> showRegister());

        HBox switchRow = new HBox(6, switchLabel, switchBtn);
        switchRow.setAlignment(Pos.CENTER);

        loginCard.getChildren().addAll(title, username, password, loginBtn, status, divider(), switchRow);
        loginCard.setAlignment(Pos.CENTER);
    }

    private void buildRegisterCard() {
        styleCard(registerCard);

        Label title = new Label("Create Account");
        title.setStyle(
            "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1a202c;" +
            "-fx-font-family: 'Segoe UI';"
        );

        TextField username = styledField("Username");
        PasswordField password = styledPassword("Password");

        Label status = new Label();
        status.setStyle("-fx-font-size: 11px; -fx-text-fill: #718096;");
        status.setWrapText(true);
        status.setTextAlignment(TextAlignment.CENTER);

        Button regBtn = primaryButton("Create Account");
        regBtn.setOnAction(e -> {
            // All self-registered users are plain USER accounts
            boolean ok = userRepository.register(username.getText(), password.getText(), "USER");
            if (ok) {
                status.setText("Registration successful! Please sign in.");
                status.setStyle("-fx-font-size: 11px; -fx-text-fill: #38a169;");
            } else {
                status.setText("Username already taken. Try another.");
                status.setStyle("-fx-font-size: 11px; -fx-text-fill: #e53e3e;");
            }
        });

        Label switchLabel = new Label("Already have an account?");
        switchLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #718096;");
        Button switchBtn = linkButton("Sign In");
        switchBtn.setOnAction(e -> showLogin());

        HBox switchRow = new HBox(6, switchLabel, switchBtn);
        switchRow.setAlignment(Pos.CENTER);

        registerCard.getChildren().addAll(title, username, password, regBtn, status, divider(), switchRow);
        registerCard.setAlignment(Pos.CENTER);
    }

    private void showLogin() {
        registerCard.setVisible(false);
        registerCard.setManaged(false);
        loginCard.setVisible(true);
        loginCard.setManaged(true);
    }

    private void showRegister() {
        loginCard.setVisible(false);
        loginCard.setManaged(false);
        registerCard.setVisible(true);
        registerCard.setManaged(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void styleCard(VBox card) {
        card.setPadding(new Insets(36, 40, 36, 40));
        card.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 16;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 24, 0, 0, 8);"
        );
        card.setMaxWidth(360);
    }

    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.setStyle(
            "-fx-background-color: #f7fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6;" +
            "-fx-background-radius: 6; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';" +
            "-fx-pref-height: 38px; -fx-padding: 0 12 0 12;"
        );
        return tf;
    }

    private PasswordField styledPassword(String prompt) {
        PasswordField pf = new PasswordField();
        pf.setPromptText(prompt);
        pf.setMaxWidth(Double.MAX_VALUE);
        pf.setStyle(
            "-fx-background-color: #f7fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6;" +
            "-fx-background-radius: 6; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';" +
            "-fx-pref-height: 38px; -fx-padding: 0 12 0 12;"
        );
        return pf;
    }

    private Button primaryButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
            "-fx-background-color: #6c3483; -fx-text-fill: #ffffff; -fx-font-weight: bold;" +
            "-fx-font-size: 13px; -fx-background-radius: 6; -fx-pref-height: 40px;" +
            "-fx-font-family: 'Segoe UI'; -fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: #512e6f; -fx-text-fill: #ffffff; -fx-font-weight: bold;" +
            "-fx-font-size: 13px; -fx-background-radius: 6; -fx-pref-height: 40px;" +
            "-fx-font-family: 'Segoe UI'; -fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: #6c3483; -fx-text-fill: #ffffff; -fx-font-weight: bold;" +
            "-fx-font-size: 13px; -fx-background-radius: 6; -fx-pref-height: 40px;" +
            "-fx-font-family: 'Segoe UI'; -fx-cursor: hand;"
        ));
        return btn;
    }

    private Button linkButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #6c3483; -fx-font-weight: bold;" +
            "-fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 0; -fx-border-width: 0;"
        );
        return btn;
    }

    private Region divider() {
        Region div = new Region();
        div.setPrefHeight(1);
        div.setMaxWidth(Double.MAX_VALUE);
        div.setStyle("-fx-background-color: #e2e8f0;");
        return div;
    }

    public Parent getView() {
        return view;
    }
}
