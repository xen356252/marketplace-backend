package com.marketplace.backend.repository

import com.marketplace.backend.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface UserRepository : JpaRepository<User, Long> {
    fun findByPhone(phone: String): User?
    fun existsByPhone(phone: String): Boolean
}

interface AddressRepository : JpaRepository<Address, Long> {
    fun findByUserId(userId: Long): List<Address>
}

interface CategoryRepository : JpaRepository<Category, Long> {
    fun findByParentIsNull(): List<Category>
    fun findByParentId(parentId: Long): List<Category>
}

interface BoothRepository : JpaRepository<Booth, Long> {
    fun findBySellerId(sellerId: Long): List<Booth>
}

interface ProductRepository : JpaRepository<Product, Long> {
    fun findByIsHotDealTrue(): List<Product>
    fun findByCategoryId(categoryId: Long): List<Product>
    fun findByBoothId(boothId: Long): List<Product>
    fun findByTitleContainingIgnoreCase(query: String): List<Product>
}

interface BannerRepository : JpaRepository<Banner, Long> {
    fun findByIsActiveTrue(): List<Banner>
}

interface CartItemRepository : JpaRepository<CartItem, Long> {
    fun findByUserId(userId: Long): List<CartItem>
    fun findByUserIdAndProductId(userId: Long, productId: Long): CartItem?
    @Transactional
    fun deleteByUserId(userId: Long)
}

interface WishlistItemRepository : JpaRepository<WishlistItem, Long> {
    fun findByUserId(userId: Long): List<WishlistItem>
    fun findByUserIdAndProductId(userId: Long, productId: Long): WishlistItem?
}

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Order>
}

interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findByOrderId(orderId: Long): List<OrderItem>
}