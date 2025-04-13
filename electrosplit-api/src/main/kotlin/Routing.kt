package com.electrosplit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.postgresql.ds.PGSimpleDataSource
import java.security.MessageDigest
import javax.sql.DataSource

fun Application.configureRouting() {
    routing {
        post("/register") {
            val request = call.receive<UserRequest>()

            // Validate input
            if (request.phoneNumber.isBlank() || request.password.isBlank() ||
                request.consumerNumber.isBlank() || request.operator.isBlank()) {
                call.respond(HttpStatusCode.BadRequest,
                    AuthResponse(false, "All fields are required"))
                return@post
            }

            // Check if phone number exists
            if (userExists(request.phoneNumber)) {
                call.respond(HttpStatusCode.Conflict,
                    AuthResponse(false, "Phone number already registered"))
                return@post
            }

            // Verify bill exists
            if (!billExists(request.consumerNumber, request.operator)) {
                call.respond(HttpStatusCode.BadRequest,
                    AuthResponse(false, "No bill found for provided consumer details"))
                return@post
            }

            // Hash password
            val passwordHash = hashPassword(request.password)

            // Create user
            val userId = createUser(
                phoneNumber = request.phoneNumber,
                passwordHash = passwordHash,
                name = request.name ?: "", // Handle optional name
                consumerNumber = request.consumerNumber,
                operator = request.operator
            )

            call.respond(HttpStatusCode.Created,
                AuthResponse(
                    success = true,
                    message = "Registration successful",
                    userId = userId,
                    name = request.name, // Return the name
                    consumerNumber = request.consumerNumber,
                    operator = request.operator
                )
            )
        }

        post("/login") {
            val request = call.receive<UserRequest>()

            if (request.phoneNumber.isBlank() || request.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest,
                    AuthResponse(false, "Phone number and password required"))
                return@post
            }

            // Get user from database
            val user = getUserByPhone(request.phoneNumber)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound,
                    AuthResponse(false, "User not found"))
                return@post
            }

            // Verify password
            if (!verifyPassword(request.password, user["password_hash"] as String)) {
                call.respond(HttpStatusCode.Unauthorized,
                    AuthResponse(false, "Invalid credentials"))
                return@post
            }

            // Successful login
            call.respond(HttpStatusCode.OK,
                AuthResponse(
                    success = true,
                    message = "Login successful",
                    userId = user["id"] as Int,
                    name = user["name"] as String, // Return stored name
                    consumerNumber = user["consumer_number"] as String,
                    operator = user["operator"] as String
                )
            )
        }

        post("/fetchBill") {
            val request = call.receive<BillRequest>()
            val bill = fetchBillFromDB(request.consumerNumber, request.operator)
            if (bill != null) {
                call.respond(bill)
            } else {
                call.respond(HttpStatusCode.NotFound, "Bill not found")
            }
        }
    }
}

// Database helper functions
private fun createDataSource(): DataSource {
    return PGSimpleDataSource().apply {
        user = "electrosplit"
        password = "electrosplit123"
        databaseName = "electrosplit_db"
        serverNames = arrayOf("localhost")
        portNumbers = intArrayOf(5432)
    }
}

private fun fetchBillFromDB(consumerNumber: String, operator: String): BillResponse? {
    val query = """
        SELECT total_units, total_amount 
        FROM bills 
        WHERE consumer_number = ? AND operator = ?
    """.trimIndent()

    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, consumerNumber)
            stmt.setString(2, operator)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    BillResponse(rs.getInt("total_units"), rs.getDouble("total_amount"))
                } else {
                    null
                }
            }
        }
    }
}

private fun userExists(phoneNumber: String): Boolean {
    val query = "SELECT 1 FROM users WHERE phone_number = ?"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, phoneNumber)
            stmt.executeQuery().use { rs ->
                rs.next()
            }
        }
    }
}

private fun billExists(consumerNumber: String, operator: String): Boolean {
    val query = "SELECT 1 FROM bills WHERE consumer_number = ? AND operator = ?"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, consumerNumber)
            stmt.setString(2, operator)
            stmt.executeQuery().use { rs ->
                rs.next()
            }
        }
    }
}

private fun createUser(
    phoneNumber: String,
    passwordHash: String,
    name: String,
    consumerNumber: String,
    operator: String
): Int {
    val query = """
        INSERT INTO users 
        (phone_number, password_hash, name, consumer_number, operator)
        VALUES (?, ?, ?, ?, ?)
        RETURNING id
    """.trimIndent()

    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, phoneNumber)
            stmt.setString(2, passwordHash)
            stmt.setString(3, name)  // Store the name
            stmt.setString(4, consumerNumber)
            stmt.setString(5, operator)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getInt("id")
                } else {
                    throw IllegalStateException("Failed to create user")
                }
            }
        }
    }
}

private fun getUserByPhone(phoneNumber: String): Map<String, Any>? {
    val query = """
        SELECT id, password_hash, name, consumer_number, operator
        FROM users
        WHERE phone_number = ?
    """.trimIndent()

    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, phoneNumber)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    mapOf(
                        "id" to rs.getInt("id"),
                        "password_hash" to rs.getString("password_hash"),
                        "name" to rs.getString("name"),  // Include name in response
                        "consumer_number" to rs.getString("consumer_number"),
                        "operator" to rs.getString("operator")
                    )
                } else {
                    null
                }
            }
        }
    }
}

private fun hashPassword(password: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
    return bytes.fold("") { str, it -> str + "%02x".format(it) }
}

private fun verifyPassword(password: String, hash: String): Boolean {
    return hashPassword(password) == hash
}