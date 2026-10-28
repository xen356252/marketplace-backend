package com.marketplace.backend.controller

import com.marketplace.backend.model.*
import com.marketplace.backend.repository.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

// ==========================================
// DTOs FOR VITRINE, CATEGORIES, BOOTHS, PRODUCTS & CART
// ==========================================
data class BannerDto(val id: Long, val title: String, val imageUrl: String, val targetUrl: String?)

data class CategoryDto(
    val id: Long,
    val name: String,
    val iconUrl: String?,
    val parentId: Long?,
    val subCategories: List<CategoryDto> = emptyList()
)

data class BoothDto(
    val id: Long,
    val sellerId: Long,
    val sellerName: String,
    val name: String,
    val description: String?,
    val type: BoothType,
    val logoUrl: String?,
    val bannerUrl: String?,
    val rating: Double
)

data class ProductDto(
    val id: Long,
    val boothId: Long,
    val boothName: String,
    val categoryId: Long,
    val categoryName: String,
    val title: String,
    val description: String?,
    val price: BigDecimal,
    val discountPrice: BigDecimal?,
    val stockQuantity: Int,
    val imageUrl: String?,
    val isHotDeal: Boolean,
    val rating: Double
)

data class VitrineResponse(
    val mode: String, // "GUEST" or "PERSONALIZED"
    val welcomeMessage: String,
    val banners: List<BannerDto>,
    val rootCategories: List<CategoryDto>,
    val hotDeals: List<ProductDto>,
    val recommendedProducts: List<ProductDto>
)

data class CreateBoothRequest(
    val sellerId: Long,
    val name: String,
    val description: String?,
    val type: BoothType = BoothType.RETAIL,
    val logoUrl: String? = null,
    val bannerUrl: String? = null
)

data class CreateProductRequest(
    val categoryId: Long,
    val title: String,
    val description: String?,
    val price: BigDecimal,
    val discountPrice: BigDecimal? = null,
    val stockQuantity: Int = 10,
    val imageUrl: String? = null,
    val isHotDeal: Boolean = false
)

data class CartItemDto(
    val id: Long,
    val product: ProductDto,
    val quantity: Int,
    val lineTotal: BigDecimal
)

data class CartSummaryDto(
    val items: List<CartItemDto>,
    val totalPrice: BigDecimal
)

data class AddToCartRequest(val productId: Long, val quantity: Int = 1)
data class CheckoutRequest(val addressId: Long)

data class OrderItemDto(
    val productId: Long,
    val productTitle: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

data class OrderDto(
    val id: Long,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val shippingAddress: String,
    val createdAt: String,
    val items: List<OrderItemDto>
)

// ==========================================
// 3. HOME VITRINE CONTROLLER (Guest & Personalized Feeds)
// ==========================================
@RestController
@RequestMapping("/api/v1/vitrine")
@Tag(name = "3. Home Vitrine", description = "Guest Vitrine and Personalized User Feeds")
class VitrineController(
    private val bannerRepository: BannerRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val wishlistItemRepository: WishlistItemRepository
) {

    @GetMapping("/guest")
    @Operation(summary = "Get Guest Home Vitrine (Banners, Root Categories, Hot Deals)")
    fun getGuestVitrine(): VitrineResponse {
        val banners = bannerRepository.findByIsActiveTrue().map { it.toDto() }
        val rootCats = categoryRepository.findByParentIsNull().map { it.toSimpleDto() }
        val hotDeals = productRepository.findByIsHotDealTrue().map { it.toDto() }
        val latest = productRepository.findAll().takeLast(10).map { it.toDto() }

        return VitrineResponse(
            mode = "GUEST",
            welcomeMessage = "به بازارچه خوش آمدید",
            banners = banners,
            rootCategories = rootCats,
            hotDeals = hotDeals,
            recommendedProducts = latest
        )
    }

    @GetMapping("/personalized/{userId}")
    @Operation(summary = "Get Personalized Home Vitrine tailored to logged-in user")
    fun getPersonalizedVitrine(@PathVariable userId: Long): VitrineResponse {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "کاربر یافت نشد")
        }
        val banners = bannerRepository.findByIsActiveTrue().map { it.toDto() }
        val rootCats = categoryRepository.findByParentIsNull().map { it.toSimpleDto() }
        val hotDeals = productRepository.findByIsHotDealTrue().map { it.toDto() }

        // Tailor feed based on user's wishlist categories if present, otherwise return all products
        val wishedCategories = wishlistItemRepository.findByUserId(userId)
            .map { it.product.category.id }
            .toSet()

        val allProducts = productRepository.findAll()
        val tailored = if (wishedCategories.isNotEmpty()) {
            allProducts.sortedByDescending { wishedCategories.contains(it.category.id) }
        } else {
            allProducts
        }.map { it.toDto() }

        return VitrineResponse(
            mode = "PERSONALIZED",
            welcomeMessage = "سلام ${user.fullName} عزیز، خوش آمدید!",
            banners = banners,
            rootCategories = rootCats,
            hotDeals = hotDeals,
            recommendedProducts = tailored
        )
    }
}

// ==========================================
// 4. CATEGORY & SEARCH CONTROLLER
// ==========================================
@RestController
@RequestMapping("/api/v1")
@Tag(name = "4. Categories & Search", description = "Hierarchical Category Tree and Product Search/Filters")
class CategoryAndSearchController(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository
) {

    @GetMapping("/categories")
    @Operation(summary = "Get full hierarchical category tree (Root categories + subcategories)")
    fun getCategories(): List<CategoryDto> {
        val roots = categoryRepository.findByParentIsNull()
        return roots.map { root ->
            val subs = categoryRepository.findByParentId(root.id).map { it.toSimpleDto() }
            CategoryDto(
                id = root.id,
                name = root.name,
                iconUrl = root.iconUrl,
                parentId = null,
                subCategories = subs
            )
        }
    }

    @GetMapping("/categories/{categoryId}/products")
    @Operation(summary = "Get products inside a category (including its subcategories)")
    fun getProductsByCategory(@PathVariable categoryId: Long): List<ProductDto> {
        val subIds = categoryRepository.findByParentId(categoryId).map { it.id }.toSet()
        val targetIds = subIds + categoryId
        return productRepository.findAll()
            .filter { targetIds.contains(it.category.id) }
            .map { it.toDto() }
    }

    @GetMapping("/products/search")
    @Operation(summary = "Search and filter products by keyword, category, price range, or hot deals")
    fun searchProducts(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) minPrice: BigDecimal?,
        @RequestParam(required = false) maxPrice: BigDecimal?,
        @RequestParam(required = false) onlyHotDeals: Boolean?
    ): List<ProductDto> {
        return productRepository.findAll()
            .filter { p ->
                val matchesQuery = q.isNullOrBlank() ||
                        p.title.contains(q, ignoreCase = true) ||
                        (p.description?.contains(q, ignoreCase = true) == true)
                val matchesCat = categoryId == null || p.category.id == categoryId || p.category.parent?.id == categoryId
                val effectivePrice = p.discountPrice ?: p.price
                val matchesMin = minPrice == null || effectivePrice >= minPrice
                val matchesMax = maxPrice == null || effectivePrice <= maxPrice
                val matchesHot = onlyHotDeals != true || p.isHotDeal

                matchesQuery && matchesCat && matchesMin && matchesMax && matchesHot
            }
            .map { it.toDto() }
    }
}

// ==========================================
// 5. RETAIL BOOTH & PRODUCT CONTROLLER
// ==========================================
@RestController
@RequestMapping("/api/v1")
@Tag(name = "5. Retail Booths & Products", description = "Storefront setup, booth views, and product management")
class BoothAndProductController(
    private val boothRepository: BoothRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository
) {

    @GetMapping("/booths")
    @Operation(summary = "List all booths")
    fun getAllBooths(): List<BoothDto> = boothRepository.findAll().map { it.toDto() }

    @GetMapping("/booths/{boothId}")
    @Operation(summary = "Get retail booth storefront details")
    fun getBooth(@PathVariable boothId: Long): BoothDto {
        val booth = boothRepository.findById(boothId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "غرفه یافت نشد")
        }
        return booth.toDto()
    }

    @GetMapping("/booths/{boothId}/products")
    @Operation(summary = "Get all products in a specific booth")
    fun getBoothProducts(@PathVariable boothId: Long): List<ProductDto> {
        return productRepository.findByBoothId(boothId).map { it.toDto() }
    }

    @PostMapping("/booths")
    @Operation(summary = "Create a new Retail Booth (Auto-upgrades user role to SELLER)")
    fun createBooth(@RequestBody req: CreateBoothRequest): ResponseEntity<BoothDto> {
        val seller = userRepository.findById(req.sellerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "فروشنده یافت نشد")
        }
        if (seller.role == UserRole.BUYER) {
            seller.role = UserRole.SELLER
            userRepository.save(seller)
        }
        val saved = boothRepository.save(
            Booth(
                seller = seller,
                name = req.name,
                description = req.description,
                type = req.type,
                logoUrl = req.logoUrl,
                bannerUrl = req.bannerUrl
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toDto())
    }

    @PostMapping("/booths/{boothId}/products")
    @Operation(summary = "Add a new fixed-price product to a Retail Booth")
    fun addProductToBooth(
        @PathVariable boothId: Long,
        @RequestBody req: CreateProductRequest
    ): ResponseEntity<ProductDto> {
        val booth = boothRepository.findById(boothId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "غرفه یافت نشد")
        }
        val category = categoryRepository.findById(req.categoryId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "دسته‌بندی یافت نشد")
        }
        val saved = productRepository.save(
            Product(
                booth = booth,
                category = category,
                title = req.title,
                description = req.description,
                price = req.price,
                discountPrice = req.discountPrice,
                stockQuantity = req.stockQuantity,
                imageUrl = req.imageUrl,
                isHotDeal = req.isHotDeal
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toDto())
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Get single product details")
    fun getProductDetails(@PathVariable productId: Long): ProductDto {
        val product = productRepository.findById(productId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "محصول یافت نشد")
        }
        return product.toDto()
    }
}

// ==========================================
// 6. CART, WISHLIST & CHECKOUT CONTROLLER
// ==========================================
@RestController
@RequestMapping("/api/v1/users/{userId}")
@Tag(name = "6. Cart, Wishlist & Checkout", description = "Shopping cart, wishlist, and order checkout flow")
class CartAndOrderController(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val addressRepository: AddressRepository,
    private val cartItemRepository: CartItemRepository,
    private val wishlistItemRepository: WishlistItemRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository
) {

    @GetMapping("/cart")
    @Operation(summary = "Get user's current shopping cart and total price")
    fun getCart(@PathVariable userId: Long): CartSummaryDto {
        val items = cartItemRepository.findByUserId(userId).map { item ->
            val unitPrice = item.product.discountPrice ?: item.product.price
            CartItemDto(
                id = item.id,
                product = item.product.toDto(),
                quantity = item.quantity,
                lineTotal = unitPrice.multiply(BigDecimal(item.quantity))
            )
        }
        val total = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.lineTotal) }
        return CartSummaryDto(items = items, totalPrice = total)
    }

    @PostMapping("/cart")
    @Operation(summary = "Add product to cart or update quantity")
    fun addToCart(
        @PathVariable userId: Long,
        @RequestBody req: AddToCartRequest
    ): CartSummaryDto {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "کاربر یافت نشد")
        }
        val product = productRepository.findById(req.productId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "محصول یافت نشد")
        }

        val existing = cartItemRepository.findByUserIdAndProductId(userId, req.productId)
        if (existing != null) {
            existing.quantity += req.quantity
            cartItemRepository.save(existing)
        } else {
            cartItemRepository.save(CartItem(user = user, product = product, quantity = req.quantity))
        }
        return getCart(userId)
    }

    @DeleteMapping("/cart/{productId}")
    @Operation(summary = "Remove a product from the cart")
    fun removeFromCart(@PathVariable userId: Long, @PathVariable productId: Long): CartSummaryDto {
        val existing = cartItemRepository.findByUserIdAndProductId(userId, productId)
        if (existing != null) {
            cartItemRepository.delete(existing)
        }
        return getCart(userId)
    }

    @GetMapping("/wishlist")
    @Operation(summary = "Get user's saved wishlist products")
    fun getWishlist(@PathVariable userId: Long): List<ProductDto> {
        return wishlistItemRepository.findByUserId(userId).map { it.product.toDto() }
    }

    @PostMapping("/wishlist/{productId}")
    @Operation(summary = "Toggle or add product to user's wishlist")
    fun addToWishlist(@PathVariable userId: Long, @PathVariable productId: Long): List<ProductDto> {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "کاربر یافت نشد")
        }
        val product = productRepository.findById(productId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "محصول یافت نشد")
        }
        val existing = wishlistItemRepository.findByUserIdAndProductId(userId, productId)
        if (existing == null) {
            wishlistItemRepository.save(WishlistItem(user = user, product = product))
        }
        return getWishlist(userId)
    }

    @DeleteMapping("/wishlist/{productId}")
    @Operation(summary = "Remove product from user's wishlist")
    fun removeFromWishlist(@PathVariable userId: Long, @PathVariable productId: Long): List<ProductDto> {
        val existing = wishlistItemRepository.findByUserIdAndProductId(userId, productId)
        if (existing != null) {
            wishlistItemRepository.delete(existing)
        }
        return getWishlist(userId)
    }

    @PostMapping("/orders/checkout")
    @Transactional
    @Operation(summary = "Checkout current cart and create a paid Order")
    fun checkout(
        @PathVariable userId: Long,
        @RequestBody req: CheckoutRequest
    ): ResponseEntity<OrderDto> {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "کاربر یافت نشد")
        }
        val address = addressRepository.findById(req.addressId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "آدرس ارسال یافت نشد")
        }
        val cartItems = cartItemRepository.findByUserId(userId)
        if (cartItems.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "سبد خرید شما خالی است")
        }

        val total = cartItems.fold(BigDecimal.ZERO) { acc, item ->
            val unitPrice = item.product.discountPrice ?: item.product.price
            acc.add(unitPrice.multiply(BigDecimal(item.quantity)))
        }

        val savedOrder = orderRepository.save(
            Order(user = user, shippingAddress = address, totalAmount = total, status = OrderStatus.PAID)
        )

        val savedItems = cartItems.map { item ->
            val unitPrice = item.product.discountPrice ?: item.product.price
            orderItemRepository.save(
                OrderItem(
                    order = savedOrder,
                    product = item.product,
                    quantity = item.quantity,
                    unitPrice = unitPrice
                )
            )
        }

        cartItemRepository.deleteByUserId(userId)

        return ResponseEntity.status(HttpStatus.CREATED).body(savedOrder.toDto(savedItems))
    }

    @GetMapping("/orders")
    @Operation(summary = "Get user's order history")
    fun getOrders(@PathVariable userId: Long): List<OrderDto> {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).map { order ->
            val items = orderItemRepository.findByOrderId(order.id)
            order.toDto(items)
        }
    }
}

// ==========================================
// MAPPERS
// ==========================================
private fun Banner.toDto() = BannerDto(id, title, imageUrl, targetUrl)
private fun Category.toSimpleDto() = CategoryDto(id, name, iconUrl, parent?.id)
private fun Booth.toDto() = BoothDto(id, seller.id, seller.fullName, name, description, type, logoUrl, bannerUrl, rating)
private fun Product.toDto() = ProductDto(
    id = id,
    boothId = booth.id,
    boothName = booth.name,
    categoryId = category.id,
    categoryName = category.name,
    title = title,
    description = description,
    price = price,
    discountPrice = discountPrice,
    stockQuantity = stockQuantity,
    imageUrl = imageUrl,
    isHotDeal = isHotDeal,
    rating = rating
)
private fun Order.toDto(items: List<OrderItem>) = OrderDto(
    id = id,
    status = status,
    totalAmount = totalAmount,
    shippingAddress = "${shippingAddress.city} - ${shippingAddress.fullAddress}",
    createdAt = createdAt.toString(),
    items = items.map { OrderItemDto(it.product.id, it.product.title, it.quantity, it.unitPrice) }
)