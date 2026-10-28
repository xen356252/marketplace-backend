package com.marketplace.backend.config

import com.marketplace.backend.model.*
import com.marketplace.backend.repository.*
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DataSeeder(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val boothRepository: BoothRepository,
    private val productRepository: ProductRepository,
    private val bannerRepository: BannerRepository
) : CommandLineRunner {

    override fun run(vararg args: String) {
        if (categoryRepository.count() > 0) return // Already seeded

        // 1. Seed Default Seller & Buyer
        val seller = userRepository.save(
            User(phone = "09120000001", fullName = "فروشگاه دیجیتال مهیار", role = UserRole.SELLER)
        )
        userRepository.save(
            User(phone = "09120000002", fullName = "خریدار تست", role = UserRole.BUYER)
        )

        // 2. Seed Hierarchical Categories (Mobile, Cases, Brands)
        val mobileCat = categoryRepository.save(Category(name = "موبایل", iconUrl = "https://picsum.photos/seed/mob/200"))
        val caseCat = categoryRepository.save(Category(name = "قاب گوشی", iconUrl = "https://picsum.photos/seed/case/200", parent = mobileCat))
        val appleCat = categoryRepository.save(Category(name = "اپل", iconUrl = "https://picsum.photos/seed/apple/200", parent = mobileCat))
        val samsungCat = categoryRepository.save(Category(name = "سامسونگ", iconUrl = "https://picsum.photos/seed/sam/200", parent = mobileCat))

        // 3. Seed Retail Booth
        val retailBooth = boothRepository.save(
            Booth(
                seller = seller,
                name = "غرفه موبایل و لوازم جانبی پارس",
                description = "فروش مستقیم انواع گوشی‌های هوشمند و قاب‌های اورجینال",
                type = BoothType.RETAIL,
                logoUrl = "https://picsum.photos/seed/boothlogo/200",
                bannerUrl = "https://picsum.photos/seed/boothbanner/800/300",
                rating = 4.8
            )
        )

        // 4. Seed Products & Hot Deals
        productRepository.saveAll(
            listOf(
                Product(
                    booth = retailBooth,
                    category = appleCat,
                    title = "گوشی موبایل اپل مدل iPhone 16 Pro ظرفیت 256 گیگابایت",
                    description = "پارت نامبر ZAA دو سیم‌کارت با گارانتی ۱۸ ماهه",
                    price = BigDecimal("82000000"),
                    discountPrice = BigDecimal("78500000"),
                    stockQuantity = 15,
                    imageUrl = "https://picsum.photos/seed/iphone16/400",
                    isHotDeal = true,
                    rating = 4.9
                ),
                Product(
                    booth = retailBooth,
                    category = samsungCat,
                    title = "گوشی موبایل سامسونگ مدل Galaxy S24 Ultra ظرفیت 256 گیگابایت",
                    description = "رم 12 گیگابایت، ساخت ویتنام",
                    price = BigDecimal("74000000"),
                    discountPrice = BigDecimal("69900000"),
                    stockQuantity = 8,
                    imageUrl = "https://picsum.photos/seed/s24/400",
                    isHotDeal = true,
                    rating = 4.7
                ),
                Product(
                    booth = retailBooth,
                    category = caseCat,
                    title = "قاب مگ‌سیف سیلیکونی مناسب برای آیفون ۱۶",
                    description = "ضد ضربه با پوشش میکروفایبر داخلی",
                    price = BigDecimal("650000"),
                    discountPrice = BigDecimal("490000"),
                    stockQuantity = 50,
                    imageUrl = "https://picsum.photos/seed/case16/400",
                    isHotDeal = false,
                    rating = 4.5
                )
            )
        )

        // 5. Seed Home Vitrine Banners
        bannerRepository.saveAll(
            listOf(
                Banner(title = "جشنواره فروش ویژه موبایل", imageUrl = "https://picsum.photos/seed/banner1/800/350", targetUrl = "/categories/1"),
                Banner(title = "تخفیف ۳۰ درصدی لوازم جانبی", imageUrl = "https://picsum.photos/seed/banner2/800/350", targetUrl = "/categories/2")
            )
        )
    }
}