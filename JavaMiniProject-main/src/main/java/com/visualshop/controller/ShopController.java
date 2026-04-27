package com.visualshop.controller;

import com.visualshop.model.CartItem;
import com.visualshop.model.Product;
import com.visualshop.model.User;
import com.visualshop.repository.CartRepository;
import com.visualshop.repository.OrderRepository;
import com.visualshop.repository.ProductRepository;
import com.visualshop.repository.WishlistRepository;
import com.jpro.webapi.WebAPI;
import javafx.stage.FileChooser;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ShopController {
    private final User user;
    private final ProductRepository productRepository;
    private final Runnable onLogout;
    private final CartRepository cartRepository = new CartRepository();
    private final WishlistRepository wishlistRepository = new WishlistRepository();
    private final OrderRepository orderRepository = new OrderRepository();

    private final ArrayList<Product> masterList = new ArrayList<>();
    private final HashMap<Integer, CartItem> cartMap = new HashMap<>();
    private final HashMap<Integer, Product> wishlistMap = new HashMap<>();

    private final BorderPane root = new BorderPane();
    private final ScrollPane productScrollPane = new ScrollPane();

    private FlowPane gridPane;
    private VBox listPane;

    // ── Filter state ────────────────────────────────────────────────────────
    private final Set<String> selectedColors = new HashSet<>();
    private final Set<String> selectedBrands = new HashSet<>();
    private double maxPriceFilter = 50000;
    private String searchText = "";

    private String currentPage = "Home";
    private int offset = 0;
    private boolean isLoading = false;
    private java.sql.Connection sessionConnection;

    // ── Known filter values from dataset ────────────────────────────────────
    private static final String[] COLORS = {
        "Black", "White", "Red", "Blue", "Pink", "Green", "Yellow",
        "Orange", "Maroon", "Beige", "Navy Blue", "Brown", "Grey",
        "Olive", "Purple", "Teal", "Mustard", "Lavender", "Cream",
        "Coral", "Gold", "Rust", "Mauve", "Nude", "Peach", "Multi"
    };
    private static final String[] BRANDS = {
        "Anouk", "Biba", "W", "MANGO", "H&M", "Zara", "ONLY",
        "AND", "Chemistry", "Rare", "20Dresses", "Nayo", "InWeave",
        "Anubhutee", "Khushal K", "Libas", "Varanga", "Mitera"
    };

    public ShopController(User user, ProductRepository productRepository, Runnable onLogout) {
        this.user = user;
        this.productRepository = productRepository;
        this.onLogout = () -> {
            try { if (sessionConnection != null) sessionConnection.close(); } catch (Exception ignored) {}
            onLogout.run();
        };
        try {
            this.sessionConnection = com.visualshop.db.DatabaseManager.getConnection();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }

        loadInitialData();
        buildUI();
    }

    private void loadInitialData() {
        for (CartItem ci : cartRepository.getCart(user.getId())) {
            cartMap.put(ci.getProduct().getId(), ci);
        }
        for (Product p : wishlistRepository.getWishlist(user.getId())) {
            wishlistMap.put(p.getId(), p);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TOP NAVIGATION BAR
    // ══════════════════════════════════════════════════════════════════════
    private HBox buildNavbar() {
        // Brand / Logo (left side)
        Label logoText = new Label("Visual");
        logoText.setStyle(
            "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #6c3483;" +
            "-fx-font-family: 'Segoe UI';"
        );
        Label logoAccent = new Label("Shop");
        logoAccent.setStyle(
            "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #e91e8c;" +
            "-fx-font-family: 'Segoe UI';"
        );
        HBox logo = new HBox(0, logoText, logoAccent);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.setPrefWidth(160);

        // Center search bar
        TextField searchField = new TextField();
        searchField.setPromptText("Search for products, brands and more…");
        searchField.setPrefWidth(380);
        searchField.setPrefHeight(36);
        searchField.setStyle(
            "-fx-background-color: #f5f5f5; -fx-border-color: #e2e8f0; -fx-border-radius: 20;" +
            "-fx-background-radius: 20; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';" +
            "-fx-padding: 0 14 0 14;"
        );

        Button searchBtn = new Button("🔍");
        searchBtn.setStyle(
            "-fx-background-color: #6c3483; -fx-text-fill: white; -fx-background-radius: 0 20 20 0;" +
            "-fx-pref-height: 36px; -fx-pref-width: 40px; -fx-cursor: hand; -fx-font-size: 13px;"
        );

        Runnable doSearch = () -> {
            searchText = searchField.getText().trim().toLowerCase();
            navigateTo(currentPage);
        };
        searchBtn.setOnAction(e -> doSearch.run());
        searchField.setOnAction(e -> doSearch.run());

        // ── Image / Visual Search button (camera icon, left of search box) ───────────
        Button visualSearchBtn = new Button("🖼 Visual Search");
        visualSearchBtn.setStyle(
            "-fx-background-color: #e91e8c; -fx-text-fill: white; -fx-font-size: 12px;" +
            "-fx-font-weight: bold; -fx-background-radius: 20; -fx-cursor: hand;" +
            "-fx-padding: 6 14; -fx-font-family: 'Segoe UI';"
        );
        visualSearchBtn.setOnMouseEntered(e -> visualSearchBtn.setStyle(
            "-fx-background-color: #c2185b; -fx-text-fill: white; -fx-font-size: 12px;" +
            "-fx-font-weight: bold; -fx-background-radius: 20; -fx-cursor: hand;" +
            "-fx-padding: 6 14; -fx-font-family: 'Segoe UI';"
        ));
        visualSearchBtn.setOnMouseExited(e -> visualSearchBtn.setStyle(
            "-fx-background-color: #e91e8c; -fx-text-fill: white; -fx-font-size: 12px;" +
            "-fx-font-weight: bold; -fx-background-radius: 20; -fx-cursor: hand;" +
            "-fx-padding: 6 14; -fx-font-family: 'Segoe UI';"
        ));

        // ── Wire up file upload: JPro WebAPI for browser, FileChooser for desktop ──
        // Desktop fallback first — will be cleared if JPro wires successfully
        visualSearchBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select an image for Visual Search");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.webp")
            );
            File chosen = fc.showOpenDialog(visualSearchBtn.getScene().getWindow());
            if (chosen != null) {
                System.out.println("[VisualSearch] Desktop file chosen: " + chosen.getAbsolutePath());
                onFileSelected(chosen);
            }
        });

        // JPro web mode: replace click handling with browser-native file input overlay
        visualSearchBtn.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                try {
                    WebAPI webAPI = WebAPI.getWebAPI(newScene);
                    WebAPI.FileUploader uploader = webAPI.makeFileUploadNode(visualSearchBtn);
                    uploader.supportedExtensions().addAll(".jpg", ".png", ".jpeg", ".webp");

                    // Clear the desktop onAction so it doesn't interfere with JPro's overlay
                    visualSearchBtn.setOnAction(null);
                    System.out.println("[VisualSearch] Cleared desktop onAction — JPro overlay handles clicks.");

                    uploader.setOnFileSelected(fileName -> {
                        System.out.println("[VisualSearch] JPro file selected: " + fileName);
                        uploader.uploadFile();
                    });

                    uploader.uploadedFileProperty().addListener((obs2, oldFile, newFile) -> {
                        if (newFile != null) {
                            System.out.println("[VisualSearch] JPro file uploaded to: " + newFile.getAbsolutePath());
                            Platform.runLater(() -> onFileSelected(newFile));
                        }
                    });
                    System.out.println("[VisualSearch] JPro WebAPI FileUploader wired successfully.");
                } catch (Exception ex) {
                    System.out.println("[VisualSearch] JPro WebAPI not available, desktop FileChooser active. (" + ex.getMessage() + ")");
                }
            }
        });

        HBox searchBox = new HBox(8, visualSearchBtn, searchField, searchBtn);
        searchBox.setAlignment(Pos.CENTER);

        // Right nav buttons
        Button btnHome     = navButton("🏠 Home");
        Button btnCart     = navButton("🛒 Cart");
        Button btnWishlist = navButton("♥ Wishlist");
        Button btnLogout   = navButton("Logout");

        btnHome.setOnAction(e     -> navigateTo("Home"));
        btnCart.setOnAction(e     -> navigateTo("Cart"));
        btnWishlist.setOnAction(e -> navigateTo("Wishlist"));
        btnLogout.setOnAction(e   -> onLogout.run());

        btnLogout.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #e53e3e; -fx-font-size: 13px;" +
            "-fx-font-family: 'Segoe UI'; -fx-cursor: hand; -fx-border-width: 0;" +
            "-fx-font-weight: bold;"
        );

        Label userLabel = new Label("Hi, " + user.getUsername());
        userLabel.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: #4a5568; -fx-font-family: 'Segoe UI';"
        );

        HBox rightNav = new HBox(8, userLabel, btnHome, btnCart, btnWishlist, btnLogout);
        rightNav.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(rightNav, Priority.ALWAYS);

        HBox navbar = new HBox(20, logo, searchBox, rightNav);
        navbar.setAlignment(Pos.CENTER);
        navbar.setPadding(new Insets(10, 20, 10, 20));
        navbar.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 3);"
        );
        return navbar;
    }

    private Button navButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #2d3748; -fx-font-size: 13px;" +
            "-fx-font-family: 'Segoe UI'; -fx-cursor: hand; -fx-border-width: 0;" +
            "-fx-padding: 6 10 6 10;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: #f3e5f5; -fx-text-fill: #6c3483; -fx-font-size: 13px;" +
            "-fx-font-family: 'Segoe UI'; -fx-cursor: hand; -fx-border-width: 0;" +
            "-fx-background-radius: 6; -fx-padding: 6 10 6 10;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #2d3748; -fx-font-size: 13px;" +
            "-fx-font-family: 'Segoe UI'; -fx-cursor: hand; -fx-border-width: 0;" +
            "-fx-padding: 6 10 6 10;"
        ));
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FILTER SIDEBAR
    // ══════════════════════════════════════════════════════════════════════
    private ScrollPane buildFilterSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.setPrefWidth(210);
        sidebar.setMinWidth(210);
        sidebar.setStyle("-fx-background-color: #fafafa; -fx-border-color: #e8e8e8; -fx-border-width: 0 1 0 0;");

        Label filterTitle = new Label("FILTERS");
        filterTitle.setStyle(
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a202c;" +
            "-fx-font-family: 'Segoe UI'; -fx-padding: 16 16 8 16;"
        );

        // Price range
        VBox priceSection = filterSection("Price Range");
        Slider priceSlider = new Slider(169, 48000, 48000);
        priceSlider.setMajorTickUnit(10000);
        priceSlider.setShowTickLabels(false);
        priceSlider.setStyle("-fx-accent: #6c3483;");
        Label priceLabel = new Label("Up to ₹48,000");
        priceLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c3483; -fx-font-weight: bold;");
        priceSlider.valueProperty().addListener((obs, o, nv) -> {
            maxPriceFilter = nv.doubleValue();
            priceLabel.setText(String.format("Up to ₹%.0f", maxPriceFilter));
        });
        priceSlider.setOnMouseReleased(e -> navigateTo(currentPage));
        priceSlider.setPadding(new Insets(0, 12, 0, 12));
        priceSection.getChildren().addAll(priceSlider, priceLabel);

        // Color filter
        VBox colorSection = filterSection("Color");
        for (String color : COLORS) {
            CheckBox cb = filterCheckBox(color);
            cb.setOnAction(e -> {
                if (cb.isSelected()) selectedColors.add(color);
                else selectedColors.remove(color);
                navigateTo(currentPage);
            });
            colorSection.getChildren().add(cb);
        }

        // Brand filter
        VBox brandSection = filterSection("Brand");
        for (String brand : BRANDS) {
            CheckBox cb = filterCheckBox(brand);
            cb.setOnAction(e -> {
                if (cb.isSelected()) selectedBrands.add(brand);
                else selectedBrands.remove(brand);
                navigateTo(currentPage);
            });
            brandSection.getChildren().add(cb);
        }

        // Clear filters
        Button clearBtn = new Button("Clear All Filters");
        clearBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #6c3483; -fx-font-size: 12px;" +
            "-fx-font-weight: bold; -fx-cursor: hand; -fx-border-color: #6c3483;" +
            "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 12;"
        );
        clearBtn.setOnAction(e -> {
            selectedColors.clear();
            selectedBrands.clear();
            maxPriceFilter = 48000;
            priceSlider.setValue(48000);
            priceLabel.setText("Up to ₹48,000");
            // uncheck all
            colorSection.getChildren().filtered(n -> n instanceof CheckBox)
                        .forEach(n -> ((CheckBox) n).setSelected(false));
            brandSection.getChildren().filtered(n -> n instanceof CheckBox)
                        .forEach(n -> ((CheckBox) n).setSelected(false));
            navigateTo(currentPage);
        });
        VBox clearBox = new VBox(clearBtn);
        clearBox.setPadding(new Insets(12, 16, 16, 16));

        sidebar.getChildren().addAll(
            filterTitle,
            new Separator(),
            priceSection,
            new Separator(),
            colorSection,
            new Separator(),
            brandSection,
            new Separator(),
            clearBox
        );

        ScrollPane sp = new ScrollPane(sidebar);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: #fafafa; -fx-background-color: #fafafa;");
        sp.setPrefWidth(215);
        return sp;
    }

    private VBox filterSection(String title) {
        Label lbl = new Label(title.toUpperCase());
        lbl.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #718096;" +
            "-fx-font-family: 'Segoe UI'; -fx-padding: 12 16 6 16;"
        );
        VBox section = new VBox(4, lbl);
        section.setPadding(new Insets(0, 0, 8, 0));
        return section;
    }

    private CheckBox filterCheckBox(String label) {
        CheckBox cb = new CheckBox(label);
        cb.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: #4a5568; -fx-font-family: 'Segoe UI';" +
            "-fx-padding: 2 16;"
        );
        return cb;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MAIN UI BUILD
    // ══════════════════════════════════════════════════════════════════════
    private void buildUI() {
        root.setTop(buildNavbar());

        productScrollPane.setFitToWidth(true);
        productScrollPane.setStyle("-fx-background: #f7f7f7; -fx-background-color: #f7f7f7;");
        productScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 0.90 && !isLoading && currentPage.equals("Home")) {
                fetchNextBatchFromJDBC();
            }
        });

        // Left sidebar + product area
        ScrollPane filterPanel = buildFilterSidebar();
        HBox contentArea = new HBox(filterPanel, productScrollPane);
        HBox.setHgrow(productScrollPane, Priority.ALWAYS);
        root.setCenter(contentArea);

        navigateTo("Home");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════════════════
    private void navigateTo(String page) {
        currentPage = page;
        offset = 0;
        masterList.clear();

        if (page.equals("Cart") || page.equals("Wishlist")) {
            listPane = new VBox(10);
            listPane.setPadding(new Insets(16));
            listPane.setStyle("-fx-background-color: #f7f7f7;");
            productScrollPane.setContent(listPane);
            renderListPage();
        } else {
            gridPane = new FlowPane();
            gridPane.setHgap(16);
            gridPane.setVgap(16);
            gridPane.setPadding(new Insets(20));
            gridPane.setStyle("-fx-background-color: #f7f7f7;");
            productScrollPane.setContent(gridPane);
            fetchNextBatchFromJDBC();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA FETCHING WITH FILTERS
    // ══════════════════════════════════════════════════════════════════════
    private void fetchNextBatchFromJDBC() {
        if (isLoading) return;
        isLoading = true;

        try {
            // Build WHERE clause dynamically
            StringBuilder where = new StringBuilder(" WHERE price <= ?");
            List<Object> params = new ArrayList<>();
            params.add(maxPriceFilter);

            if (!searchText.isEmpty()) {
                where.append(" AND (LOWER(name) LIKE ? OR LOWER(brand) LIKE ? OR LOWER(category) LIKE ?)");
                String like = "%" + searchText + "%";
                params.add(like);
                params.add(like);
                params.add(like);
            }
            if (!selectedColors.isEmpty()) {
                where.append(" AND color IN (");
                for (int i = 0; i < selectedColors.size(); i++) {
                    where.append(i == 0 ? "?" : ",?");
                }
                where.append(")");
                params.addAll(selectedColors);
            }
            if (!selectedBrands.isEmpty()) {
                where.append(" AND brand IN (");
                for (int i = 0; i < selectedBrands.size(); i++) {
                    where.append(i == 0 ? "?" : ",?");
                }
                where.append(")");
                params.addAll(selectedBrands);
            }

            String sql = "SELECT * FROM products" + where + " LIMIT 24 OFFSET ?";
            params.add(offset);

            java.sql.PreparedStatement ps = sessionConnection.prepareStatement(sql);
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else if (p instanceof Double) ps.setDouble(i + 1, (Double) p);
                else ps.setString(i + 1, p.toString());
            }

            java.sql.ResultSet rs = ps.executeQuery();
            List<Product> fetched = new ArrayList<>();
            while (rs.next()) {
                fetched.add(new Product(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getDouble("price"),
                    rs.getString("color"),
                    rs.getString("brand"),
                    rs.getString("image_url"),
                    rs.getString("description")));
            }

            if (offset == 0 && fetched.isEmpty()) {
                Label noResult = new Label("No products found matching your filters.");
                noResult.setStyle(
                    "-fx-font-size: 15px; -fx-text-fill: #718096; -fx-font-family: 'Segoe UI';" +
                    "-fx-padding: 40;"
                );
                gridPane.getChildren().add(noResult);
            } else if (!fetched.isEmpty()) {
                masterList.addAll(fetched);
                offset += 24;
                for (Product p : fetched) {
                    gridPane.getChildren().add(createProductCard(p));
                }
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        isLoading = false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRODUCT CARD
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Loads an image from a URL on a background thread so the UI never blocks.
     * A grey rectangle placeholder is shown immediately; the real image swaps in
     * once it is downloaded.
     */
    private ImageView loadImageAsync(String url, double fitW, double fitH) {
        ImageView iv = new ImageView();
        iv.setFitWidth(fitW);
        iv.setFitHeight(fitH);
        iv.setPreserveRatio(true);
        // Placeholder: a solid light-grey region while the real image loads
        javafx.scene.shape.Rectangle placeholder = new javafx.scene.shape.Rectangle(fitW, fitH);
        placeholder.setFill(javafx.scene.paint.Color.web("#e8e8e8"));
        iv.setImage(null);

        if (url != null && !url.isBlank()) {
            Thread loader = new Thread(() -> {
                try {
                    Image img = new Image(url, fitW, fitH, true, true, false);
                    if (!img.isError()) {
                        Platform.runLater(() -> iv.setImage(img));
                    }
                } catch (Exception ignored) {}
            }, "img-loader");
            loader.setDaemon(true);
            loader.start();
        }
        return iv;
    }

    private VBox createProductCard(Product p) {
        ImageView imageView = loadImageAsync(p.getImageUrl(), 180, 200);

        String nameStr = p.getName().length() > 32 ? p.getName().substring(0, 29) + "…" : p.getName();
        Label title = new Label(nameStr);
        title.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: #2d3748; -fx-font-family: 'Segoe UI';" +
            "-fx-wrap-text: true;"
        );
        title.setWrapText(true);
        title.setPrefWidth(175);

        Label brandLbl = new Label(p.getBrand());
        brandLbl.setStyle(
            "-fx-font-size: 11px; -fx-text-fill: #718096; -fx-font-family: 'Segoe UI';"
        );

        Label price = new Label(String.format("₹ %.0f", p.getPrice()));
        price.setStyle(
            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a202c;" +
            "-fx-font-family: 'Segoe UI';"
        );

        boolean inWishlist = wishlistMap.containsKey(p.getId());
        Button btnWishlist = new Button(inWishlist ? "♥" : "♡");
        btnWishlist.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: " + (inWishlist ? "#e91e8c" : "#a0aec0") + ";" +
            "-fx-font-size: 18px; -fx-cursor: hand; -fx-padding: 0;"
        );
        btnWishlist.setOnAction(e -> {
            if (wishlistMap.containsKey(p.getId())) {
                wishlistRepository.removeItem(user.getId(), p.getId());
                wishlistMap.remove(p.getId());
                btnWishlist.setText("♡");
                btnWishlist.setStyle(
                    "-fx-background-color: transparent; -fx-text-fill: #a0aec0;" +
                    "-fx-font-size: 18px; -fx-cursor: hand; -fx-padding: 0;"
                );
            } else {
                wishlistRepository.addItem(user.getId(), p.getId());
                wishlistMap.put(p.getId(), p);
                btnWishlist.setText("♥");
                btnWishlist.setStyle(
                    "-fx-background-color: transparent; -fx-text-fill: #e91e8c;" +
                    "-fx-font-size: 18px; -fx-cursor: hand; -fx-padding: 0;"
                );
            }
        });

        Button btnCart = new Button("Add to Bag");
        btnCart.setStyle(
            "-fx-background-color: #6c3483; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-background-radius: 4; -fx-cursor: hand; -fx-pref-height: 28px;" +
            "-fx-font-weight: bold;"
        );
        btnCart.setOnMouseEntered(ev -> btnCart.setStyle(
            "-fx-background-color: #512e6f; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-background-radius: 4; -fx-cursor: hand; -fx-pref-height: 28px;" +
            "-fx-font-weight: bold;"
        ));
        btnCart.setOnMouseExited(ev -> btnCart.setStyle(
            "-fx-background-color: #6c3483; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-background-radius: 4; -fx-cursor: hand; -fx-pref-height: 28px;" +
            "-fx-font-weight: bold;"
        ));
        btnCart.setOnAction(e -> {
            try {
                java.sql.PreparedStatement ps = sessionConnection.prepareStatement(
                    "INSERT INTO cart(user_id, product_id, quantity) VALUES (?, ?, 1)" +
                    " ON DUPLICATE KEY UPDATE quantity = quantity + 1");
                ps.setInt(1, user.getId());
                ps.setInt(2, p.getId());
                ps.executeUpdate();
            } catch (Exception ex) { ex.printStackTrace(); }
            if (cartMap.containsKey(p.getId())) {
                CartItem ci = cartMap.get(p.getId());
                cartMap.put(p.getId(), new CartItem(p, ci.getQuantity() + 1));
            } else {
                cartMap.put(p.getId(), new CartItem(p, 1));
            }
            btnCart.setText("✓ Added");
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> btnCart.setText("Add to Bag"));
            }).start();
        });

        HBox.setHgrow(btnCart, Priority.ALWAYS);
        HBox actions = new HBox(8, btnWishlist, btnCart);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, imageView, brandLbl, title, price, actions);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(12));
        card.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);"
        );
        card.setPrefWidth(200);
        card.setMaxWidth(200);

        // Hover shadow lift effect
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 16, 0, 0, 6);" +
            "-fx-translate-y: -2;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);" +
            "-fx-translate-y: 0;"
        ));

        return card;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CART / WISHLIST LIST VIEW
    // ══════════════════════════════════════════════════════════════════════
    private void renderListPage() {
        listPane.getChildren().clear();

        Label pageTitle = new Label(currentPage.equals("Cart") ? "🛒 Shopping Bag" : "♥ My Wishlist");
        pageTitle.setStyle(
            "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1a202c;" +
            "-fx-font-family: 'Segoe UI'; -fx-padding: 0 0 12 0;"
        );
        listPane.getChildren().add(pageTitle);

        if (currentPage.equals("Wishlist")) {
            if (wishlistMap.isEmpty()) {
                listPane.getChildren().add(emptyStateLabel("Your wishlist is empty."));
                return;
            }
            for (Product p : wishlistMap.values()) {
                listPane.getChildren().add(createListRow(p, null));
            }
        } else if (currentPage.equals("Cart")) {
            if (cartMap.isEmpty()) {
                listPane.getChildren().add(emptyStateLabel("Your cart is empty."));
                return;
            }
            double total = 0;
            for (CartItem ci : cartMap.values()) {
                listPane.getChildren().add(createListRow(ci.getProduct(), ci));
                total += ci.getSubtotal();
            }

            double tax = total * 0.18;
            Label lblTotal = new Label(String.format("Subtotal: ₹ %.2f   |   GST (18%%): ₹ %.2f   |   Total: ₹ %.2f", total, tax, total + tax));
            lblTotal.setStyle(
                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1a202c;" +
                "-fx-font-family: 'Segoe UI';"
            );

            final double finalTotal = total;
            Button checkout = new Button("Place Order");
            checkout.setStyle(
                "-fx-background-color: #6c3483; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-font-size: 14px; -fx-background-radius: 6; -fx-pref-height: 42px;" +
                "-fx-pref-width: 180px; -fx-cursor: hand;"
            );
            checkout.setOnAction(e -> {
                orderRepository.saveOrder(user.getId(), finalTotal, finalTotal * 0.18);
                for (Integer pId : cartMap.keySet()) {
                    cartRepository.removeItem(user.getId(), pId);
                }
                cartMap.clear();
                navigateTo("Cart");
            });

            VBox checkoutBox = new VBox(12, new Separator(), lblTotal, checkout);
            checkoutBox.setAlignment(Pos.CENTER_RIGHT);
            checkoutBox.setPadding(new Insets(20, 0, 0, 0));
            listPane.getChildren().add(checkoutBox);
        }
    }

    private Label emptyStateLabel(String msg) {
        Label lbl = new Label(msg);
        lbl.setStyle(
            "-fx-font-size: 15px; -fx-text-fill: #a0aec0; -fx-font-family: 'Segoe UI';" +
            "-fx-padding: 40;"
        );
        return lbl;
    }

    private HBox createListRow(Product p, CartItem ci) {
        ImageView imageView = loadImageAsync(p.getImageUrl(), 72, 72);

        Label nameLabel = new Label(p.getName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2d3748; -fx-font-family: 'Segoe UI'; -fx-wrap-text: true;");
        nameLabel.setPrefWidth(280);
        nameLabel.setWrapText(true);

        Label brandLabel = new Label(p.getBrand());
        brandLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #718096; -fx-font-family: 'Segoe UI';");

        VBox nameBox = new VBox(3, nameLabel, brandLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Label priceLabel = new Label(String.format("₹ %.0f", p.getPrice()));
        priceLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a202c; -fx-pref-width: 100;");

        Label extraLabel = new Label(ci != null ? "Qty: " + ci.getQuantity() : "");
        extraLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #718096; -fx-pref-width: 70;");

        // ── "Add to Bag" button — visible only in Wishlist view ──────────────
        Button btnAddToCart = new Button("Add to Bag");
        btnAddToCart.setStyle(
            "-fx-background-color: #6c3483; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 12; -fx-font-weight: bold;"
        );
        btnAddToCart.setOnMouseEntered(ev -> btnAddToCart.setStyle(
            "-fx-background-color: #512e6f; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 12; -fx-font-weight: bold;"
        ));
        btnAddToCart.setOnMouseExited(ev -> btnAddToCart.setStyle(
            "-fx-background-color: #6c3483; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 12; -fx-font-weight: bold;"
        ));
        btnAddToCart.setOnAction(e -> {
            try {
                java.sql.PreparedStatement ps = sessionConnection.prepareStatement(
                    "INSERT INTO cart(user_id, product_id, quantity) VALUES (?, ?, 1)" +
                    " ON DUPLICATE KEY UPDATE quantity = quantity + 1");
                ps.setInt(1, user.getId());
                ps.setInt(2, p.getId());
                ps.executeUpdate();
            } catch (Exception ex) { ex.printStackTrace(); }
            if (cartMap.containsKey(p.getId())) {
                CartItem existing = cartMap.get(p.getId());
                cartMap.put(p.getId(), new CartItem(p, existing.getQuantity() + 1));
            } else {
                cartMap.put(p.getId(), new CartItem(p, 1));
            }
            btnAddToCart.setText("✓ Added");
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> btnAddToCart.setText("Add to Bag"));
            }).start();
        });
        // Only show in Wishlist view (ci == null means no quantity/cart context)
        btnAddToCart.setVisible(ci == null);
        btnAddToCart.setManaged(ci == null);

        Button btnRemove = new Button("Remove");
        btnRemove.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #e53e3e; -fx-font-size: 12px;" +
            "-fx-cursor: hand; -fx-border-color: #e53e3e; -fx-border-radius: 4; -fx-background-radius: 4;" +
            "-fx-padding: 4 10;"
        );
        btnRemove.setOnAction(e -> {
            if (currentPage.equals("Wishlist")) {
                wishlistRepository.removeItem(user.getId(), p.getId());
                wishlistMap.remove(p.getId());
            } else if (currentPage.equals("Cart")) {
                cartRepository.removeItem(user.getId(), p.getId());
                cartMap.remove(p.getId());
            }
            renderListPage();
        });

        VBox actionBox = new VBox(6, btnAddToCart, btnRemove);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(14, imageView, nameBox, priceLabel, extraLabel, actionBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14));
        row.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 1);"
        );
        return row;
    }


    public Parent getView() {
        return root;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VISUAL SEARCH (CLIP model via Flask REST API)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Opens a FileChooser, lets the user pick an image, then sends it to the
     * Flask visual-search server at http://localhost:5000/search.
     * Results are rendered on the product grid.
     */
    private void onFileSelected(File chosen) {
        if (chosen == null) return;

        // Show a "Searching…" placeholder immediately
        currentPage = "VisualSearch";
        gridPane = new FlowPane();
        gridPane.setHgap(16);
        gridPane.setVgap(16);
        gridPane.setPadding(new Insets(20));
        gridPane.setStyle("-fx-background-color: #f7f7f7;");
        Label searching = new Label("🔍  Analyzing image with AI — please wait…");
        searching.setStyle(
            "-fx-font-size: 16px; -fx-text-fill: #6c3483; -fx-font-weight: bold;" +
            "-fx-font-family: 'Segoe UI'; -fx-padding: 40;"
        );
        gridPane.getChildren().add(searching);
        productScrollPane.setContent(gridPane);

        final File imageFile = chosen;
        Thread t = new Thread(() -> {
            try {
                List<Product> results = callVisualSearchApi(imageFile, 10);
                Platform.runLater(() -> renderVisualSearchResults(results, imageFile));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    gridPane.getChildren().clear();
                    Label err = new Label("⚠  Visual search failed: " + ex.getMessage() +
                        "\n\nMake sure the AI server is running:  python visual_search_api.py");
                    err.setStyle(
                        "-fx-font-size: 13px; -fx-text-fill: #e53e3e;" +
                        "-fx-font-family: 'Segoe UI'; -fx-padding: 40; -fx-wrap-text: true;"
                    );
                    err.setWrapText(true);
                    gridPane.getChildren().add(err);
                });
            }
        }, "visual-search");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Sends the image via multipart/form-data to the Flask API and returns the
     * matched products fetched from the database (by p_id).
     */
    private List<Product> callVisualSearchApi(File imageFile, int topk) throws Exception {
        // Build multipart body manually (Java 11 HttpClient doesn't have built-in
        // multipart support, so we do it with boundary strings)
        String boundary = "----VisualShopBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String mimeType   = imageFile.getName().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";

        // Multipart body: two byte arrays joined
        byte[] header = (
            "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"image\"; filename=\"" + imageFile.getName() + "\"\r\n" +
            "Content-Type: " + mimeType + "\r\n\r\n"
        ).getBytes();
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes();

        byte[] body = new byte[header.length + imageBytes.length + footer.length];
        System.arraycopy(header,     0, body, 0,                              header.length);
        System.arraycopy(imageBytes, 0, body, header.length,                  imageBytes.length);
        System.arraycopy(footer,     0, body, header.length + imageBytes.length, footer.length);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:5000/search?topk=" + topk))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("API returned HTTP " + resp.statusCode() + ": " + resp.body());
        }

        // ── Minimal JSON parser (no external library needed) ─────────────
        // Response is a JSON array: [{p_id: X, name: "...", ...}, ...]
        List<Product> results = new ArrayList<>();
        String json = resp.body().trim();
        // Strip outer [ ]
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]"))   json = json.substring(0, json.length() - 1);

        // Split on top-level },{
        String[] objects = json.split("\\},\\s*\\{");
        for (String obj : objects) {
            obj = obj.replaceAll("^\\{", "").replaceAll("\\}$", "");
            int    pid       = extractInt(obj, "p_id");
            String name      = extractStr(obj, "name");
            String brand     = extractStr(obj, "brand");
            String imageUrl  = extractStr(obj, "image_url");
            String category  = extractStr(obj, "category");
            String color     = extractStr(obj, "color");
            double price     = extractDouble(obj, "price");

            // Try to fetch full product from DB; fall back to API data
            Product full = null;
            try {
                java.sql.PreparedStatement ps = sessionConnection.prepareStatement(
                    "SELECT * FROM products WHERE id = ?");
                ps.setInt(1, pid);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    full = new Product(
                        rs.getInt("id"), rs.getString("name"), rs.getString("category"),
                        rs.getDouble("price"), rs.getString("color"), rs.getString("brand"),
                        rs.getString("image_url"), rs.getString("description"));
                }
            } catch (Exception ignored) {}

            if (full == null) {
                full = new Product(pid, name, category, price, color, brand, imageUrl, "");
            }
            results.add(full);
        }
        return results;
    }

    /** Renders the visual-search results into the product grid with a header. */
    private void renderVisualSearchResults(List<Product> products, File queryFile) {
        gridPane.getChildren().clear();

        VBox header = new VBox(8);
        header.setPadding(new Insets(0, 0, 16, 0));
        
        HBox queryImgBox = new HBox(12);
        queryImgBox.setAlignment(Pos.CENTER_LEFT);
        
        try {
            Image qImg = new Image(queryFile.toURI().toString(), 80, 80, true, true);
            ImageView qView = new ImageView(qImg);
            qView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 4, 0, 0, 1);");
            
            VBox textLabels = new VBox(4);
            Label titleLbl = new Label("🖼  Visual Search Results — top " + products.size() + " matches");
            titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a202c; -fx-font-family: 'Segoe UI';");
            Label subLbl = new Label("Source image: " + queryFile.getName() + "\nPowered by CLIP + color/pattern + garment-type AI model");
            subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #a0aec0; -fx-font-family: 'Segoe UI';");
            textLabels.getChildren().addAll(titleLbl, subLbl);
            
            queryImgBox.getChildren().addAll(qView, textLabels);
        } catch (Exception e) {
            Label titleLbl = new Label("🖼  Visual Search Results — top " + products.size() + " matches for: " + queryFile.getName());
            titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a202c; -fx-font-family: 'Segoe UI';");
            queryImgBox.getChildren().add(titleLbl);
        }

        header.getChildren().add(queryImgBox);

        // FlowPane doesn't support spanning children; use a wrapper VBox
        VBox wrapper = new VBox(16, header);
        FlowPane resultsGrid = new FlowPane();
        resultsGrid.setHgap(16);
        resultsGrid.setVgap(16);
        for (Product p : products) {
            resultsGrid.getChildren().add(createProductCard(p));
        }
        wrapper.getChildren().add(resultsGrid);
        wrapper.setPadding(new Insets(20));
        wrapper.setStyle("-fx-background-color: #f7f7f7;");

        productScrollPane.setContent(wrapper);

        if (products.isEmpty()) {
            Label noRes = new Label("No matching products found. Try a different image.");
            noRes.setStyle("-fx-font-size: 14px; -fx-text-fill: #718096; -fx-padding: 40;");
            wrapper.getChildren().add(noRes);
        }
    }

    // ── Primitive JSON field extractors (avoids adding a JSON library) ────────

    private int extractInt(String obj, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        java.util.regex.Matcher m = p.matcher(obj);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private double extractDouble(String obj, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(-?[\\d.]+)");
        java.util.regex.Matcher m = p.matcher(obj);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    private String extractStr(String obj, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        java.util.regex.Matcher m = p.matcher(obj);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
    }
}

