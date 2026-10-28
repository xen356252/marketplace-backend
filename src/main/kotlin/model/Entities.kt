package com.marketplace.backend.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

// ==========================================
// 1. USERS & ROLES TABLE ([DB] Create Users & Roles Table)
// ==========================================
enum class UserRole {
    BUYER, SELLER, ADMIN
}

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 15)
    var phone: String,

    @Column(nullable = false)
    var fullName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole = UserRole.BUYER,

    var avatarUrl: String? = null,

    val createdAt: LocalDateTime = LocalDateTime.now()
)

// ==========================================
// 2. ADDRESSES TABLE ([DB] Create Addresses Table)
// ==========================================
@Entity
@Table(name = "addresses")
class Address(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var title: String, // e.g., "خانه", "محل کار"

    @Column(nullable = false)
    var city: String,

    @Column(nullable = false, length = 500)
    var fullAddress: String,

    @Column(nullable = false, length = 20)
    var postalCode: String,

    var isDefault: Boolean = false
)

// ==========================================
// 3. CATEGORIES TABLE ([DB] Create Categories Table)
// ==========================================
@Entity
@Table(name = "categories")
class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String, // e.g., "موبایل", "قاب گوشی", "اپل"

    var iconUrl: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Category? = null
)

// ==========================================
// 4. BOOTHS & PRODUCTS TABLES ([DB] Create Booths & Products Tables)
// ==========================================
enum class BoothType {
    RETAIL,     // Sprint 1: Fixed-price retail booth
    SUPPLIER,   // Future sprint: B2B wholesale
    PRODUCTION  // Future sprint: Factory/RFQ
}

@Entity
@Table(name = "booths")
class Booth(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    val seller: User,

    @Column(nullable = false)
    var name: String,

    @Column(length = 1000)
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: BoothType = BoothType.RETAIL,

    var logoUrl: String? = null,
    var bannerUrl: String? = null,
    var rating: Double = 4.5,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booth_id", nullable = false)
    val booth: Booth,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: Category,

    @Column(nullable = false)
    var title: String,

    @Column(length = 2000)
    var description: String? = null,

    @Column(nullable = false)
    var price: BigDecimal,

    var discountPrice: BigDecimal? = null,

    @Column(nullable = false)
    var stockQuantity: Int = 10,

    var imageUrl: String? = null,

    var isHotDeal: Boolean = false, // For Home Screen "Hot Deals" section

    var rating: Double = 4.5,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "banners")
class Banner(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var imageUrl: String,

    var targetUrl: String? = null,
    var isActive: Boolean = true
)

// ==========================================
// 5. CART, WISHLIST & ORDER TABLES ([DB] Create Cart & Order Tables)
// ==========================================
@Entity
@Table(name = "cart_items")
class CartItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false)
    var quantity: Int = 1
)

@Entity
@Table(name = "wishlist_items")
class WishlistItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    val addedAt: LocalDateTime = LocalDateTime.now()
)

enum class OrderStatus {
    PENDING, PAID, SHIPPED, DELIVERED, CANCELLED
}

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    val shippingAddress: Address,

    @Column(nullable = false)
    var totalAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PAID,

    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "order_items")
class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val unitPrice: BigDecimal
)