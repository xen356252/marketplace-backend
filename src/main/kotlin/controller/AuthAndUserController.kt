package com.marketplace.backend.controller

import com.marketplace.backend.model.Address
import com.marketplace.backend.model.User
import com.marketplace.backend.model.UserRole
import com.marketplace.backend.repository.AddressRepository
import com.marketplace.backend.repository.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// ==========================================
// SMS SERVICE (Mock OTP now -> Swappable for Real SMS later)
// ==========================================
interface SmsService {
    fun sendOtp(phone: String, code: String)
}

@Service
class MockSmsService : SmsService {
    override fun sendOtp(phone: String, code: String) {
        println("📩 [MOCK SMS] Sending OTP '$code' to phone: $phone (Master test code '1234' is also active)")
    }
}

// ==========================================
// JWT TOKEN SERVICE
// ==========================================
@Service
class JwtService {
    private val secret = "SnapMarketplaceSuperSecretKeyForJwtSigning2026Sprint1!"
    private val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    fun generateToken(user: User): String {
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("phone", user.phone)
            .claim("role", user.role.name)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 7)) // 7 Days
            .signWith(key)
            .compact()
    }
}

// ==========================================
// DTOs (Request / Response Models)
// ==========================================
data class SendOtpRequest(val phone: String)
data class SendOtpResponse(val message: String, val mockOtpHint: String = "1234")

data class VerifyOtpRequest(val phone: String, val otpCode: String)
data class RegisterRequest(val phone: String, val fullName: String, val role: UserRole = UserRole.BUYER, val otpCode: String = "1234")

data class AuthResponse(
    val token: String,
    val user: UserProfileDto
)

data class UserProfileDto(
    val id: Long,
    val phone: String,
    val fullName: String,
    val role: UserRole,
    val avatarUrl: String?
)

data class UpdateProfileRequest(
    val fullName: String,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val role: UserRole? = null
)

data class AddressDto(
    val id: Long,
    val title: String,
    val city: String,
    val fullAddress: String,
    val postalCode: String,
    val isDefault: Boolean
)

data class CreateAddressRequest(
    val title: String,
    val city: String,
    val fullAddress: String,
    val postalCode: String,
    val isDefault: Boolean = false
)

// ==========================================
// 1. AUTH CONTROLLER (Register & Login with OTP + JWT)
// ==========================================
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "1. Authentication", description = "OTP Login & Buyer/Seller Registration APIs")
class AuthController(
    private val userRepository: UserRepository,
    private val smsService: SmsService,
    private val jwtService: JwtService
) {
    private val otpStorage = ConcurrentHashMap<String, String>()

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP to phone number (Mock OTP is 1234)")
    fun sendOtp(@RequestBody req: SendOtpRequest): ResponseEntity<SendOtpResponse> {
        val generatedCode = "1234" // Fixed for easy Sprint 1 testing
        otpStorage[req.phone] = generatedCode
        smsService.sendOtp(req.phone, generatedCode)
        return ResponseEntity.ok(SendOtpResponse(message = "کد تایید ارسال شد"))
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new Buyer or Seller account")
    fun register(@RequestBody req: RegisterRequest): ResponseEntity<AuthResponse> {
        if (req.otpCode != "1234" && otpStorage[req.phone] != req.otpCode) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "کد تایید نامعتبر است")
        }
        if (userRepository.existsByPhone(req.phone)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "این شماره قبلا ثبت شده است")
        }

        val newUser = userRepository.save(
            User(phone = req.phone, fullName = req.fullName, role = req.role)
        )
        val token = jwtService.generateToken(newUser)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            AuthResponse(token = token, user = newUser.toDto())
        )
    }

    @PostMapping("/login")
    @Operation(summary = "Verify OTP and return JWT token for existing user")
    fun login(@RequestBody req: VerifyOtpRequest): ResponseEntity<AuthResponse> {
        if (req.otpCode != "1234" && otpStorage[req.phone] != req.otpCode) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "کد تایید اشتباه است")
        }

        val user = userRepository.findByPhone(req.phone)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "کاربری با این شماره یافت نشد. ابتدا ثبت نام کنید.")

        val token = jwtService.generateToken(user)
        return ResponseEntity.ok(AuthResponse(token = token, user = user.toDto()))
    }
}

// ==========================================
// 2. PROFILE & ADDRESS CONTROLLER
// ==========================================
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "2. User Profile & Addresses", description = "Profile quick-edits and Shipping Address CRUD")
class UserProfileController(
    private val userRepository: UserRepository,
    private val addressRepository: AddressRepository
) {

    @GetMapping("/{userId}/profile")
    @Operation(summary = "Get user profile details")
    fun getProfile(@PathVariable userId: Long): UserProfileDto {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "کاربر یافت نشد")
        }
        return user.toDto()
    }

    @PutMapping("/{userId}/profile")
    @Operation(summary = "Update user profile (Name, Phone, Avatar, or Role)")
    fun updateProfile(
        @PathVariable userId: Long,
        @RequestBody req: UpdateProfileRequest
    ): UserProfileDto {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "کاربر یافت نشد")
        }
        user.fullName = req.fullName
        req.phone?.let { user.phone = it }
        req.avatarUrl?.let { user.avatarUrl = it }
        req.role?.let { user.role = it }

        return userRepository.save(user).toDto()
    }

    @GetMapping("/{userId}/addresses")
    @Operation(summary = "Get all shipping addresses for a user")
    fun getAddresses(@PathVariable userId: Long): List<AddressDto> {
        return addressRepository.findByUserId(userId).map { it.toDto() }
    }

    @PostMapping("/{userId}/addresses")
    @Operation(summary = "Add a new shipping address")
    fun addAddress(
        @PathVariable userId: Long,
        @RequestBody req: CreateAddressRequest
    ): ResponseEntity<AddressDto> {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "کاربر یافت نشد")
        }
        val saved = addressRepository.save(
            Address(
                user = user,
                title = req.title,
                city = req.city,
                fullAddress = req.fullAddress,
                postalCode = req.postalCode,
                isDefault = req.isDefault
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toDto())
    }

    @DeleteMapping("/{userId}/addresses/{addressId}")
    @Operation(summary = "Delete a shipping address")
    fun deleteAddress(@PathVariable userId: Long, @PathVariable addressId: Long): ResponseEntity<Void> {
        addressRepository.deleteById(addressId)
        return ResponseEntity.noContent().build()
    }
}

private fun User.toDto() = UserProfileDto(id, phone, fullName, role, avatarUrl)
private fun Address.toDto() = AddressDto(id, title, city, fullAddress, postalCode, isDefault)