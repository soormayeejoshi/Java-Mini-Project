# Visual Product Discovery & Shopping Management System

JavaFX + MySQL e-commerce desktop application that loads products from `Dataset/Fashion Dataset v2.csv`.

## Implemented Modules

- User authentication (Login/Register for USER and ADMIN roles)
- Product gallery using `GridPane` + `ScrollPane` with Shopify-like item cards (`VBox` + fixed-size `ImageView`)
- Search and category filter
- Dynamic shopping cart with real-time subtotal, tax, and total
- Checkout flow with order persistence
- Admin dashboard with inventory CRUD (add/delete)
- Database schema for `users`, `products`, `orders`, and `cart` with FK relationships
- Exception handling for CSV parsing and JDBC/database operations

## Tech Stack

- Java 17
- JavaFX
- MySQL (JDBC)
- Maven

## Database Setup

1. Install MySQL and create DB:
   - `CREATE DATABASE visual_shop;`
2. Update DB credentials in:
   - `src/main/java/com/visualshop/config/AppConfig.java`
3. On startup, tables are auto-created and products are imported from CSV.

## Run

```bash
mvn javafx:run
```

If Maven is not installed globally, use your IDE Maven runner or Maven wrapper (`mvnw`) after adding it.

## Default Admin

- Username: `admin`
- Password: `admin123`

Password is hashed with SHA-256 in storage.
