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

            if (request.phoneNumber.isBlank() || request.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest,
                    AuthResponse(false, "Phone number and password are required"))
                return@post
            }

            if (userExists(request.phoneNumber)) {
                call.respond(HttpStatusCode.Conflict,
                    AuthResponse(false, "Phone number already registered"))
                return@post
            }

            val passwordHash = hashPassword(request.password)

            val userId = createUser(
                phoneNumber = request.phoneNumber,
                passwordHash = passwordHash,
                name = request.name ?: ""
            )

            call.respond(HttpStatusCode.Created,
                AuthResponse(
                    success = true,
                    message = "Registration successful",
                    userId = userId,
                    name = request.name
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

            val user = getUserByPhone(request.phoneNumber)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound,
                    AuthResponse(false, "User not found"))
                return@post
            }

            if (!verifyPassword(request.password, user["password_hash"] as String)) {
                call.respond(HttpStatusCode.Unauthorized,
                    AuthResponse(false, "Invalid credentials"))
                return@post
            }

            call.respond(HttpStatusCode.OK,
                AuthResponse(
                    success = true,
                    message = "Login successful",
                    userId = user["id"] as Int,
                    name = user["name"] as String
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
        post("/createGroup") {
            val request = call.receive<GroupRequest>()

            // Verify creator exists
            val creator = getUserByPhone(request.creatorPhone)
            if (creator == null) {
                call.respond(HttpStatusCode.NotFound,
                    AuthResponse(false, "Creator not found"))
                return@post
            }

            // Verify bill exists
            if (!billExists(request.consumerNumber, request.operator)) {
                call.respond(HttpStatusCode.BadRequest,
                    AuthResponse(false, "No bill found for provided consumer details"))
                return@post
            }

            // Generate unique group code and QR
            val groupCode = generateRandomCode(6)
            val groupQr = request.groupQr

            // Create group
            val groupId = createGroup(
                request.groupName,
                request.creatorPhone,
                groupCode,
                groupQr,
                request.consumerNumber,
                request.operator
            )

            // Add creator as first member
            addGroupMember(groupId, request.creatorPhone)

            // Get creator name
            val creatorName = creator["name"] as String

            // Get bill details
            val bill = fetchBillFromDB(request.consumerNumber, request.operator)!!

            call.respond(HttpStatusCode.Created, GroupResponse(
                groupId,
                request.groupName,
                groupCode,
                groupQr,
                creatorName,
                request.creatorPhone,
                bill
            ))
        }

/// Join a group
        post("/joinGroup") {
            val request = call.receive<JoinGroupRequest>()

            // Verify member exists
            val member = getUserByPhone(request.memberPhone)
            if (member == null) {
                call.respond(HttpStatusCode.NotFound,
                    AuthResponse(false, "User not found"))
                return@post
            }

            // Get group by code
            val group = getGroupByCode(request.groupCode)
            if (group == null) {
                call.respond(HttpStatusCode.NotFound,
                    AuthResponse(false, "Group not found"))
                return@post
            }

            // Check if already a member
            if (isGroupMember(group["group_id"] as Int, request.memberPhone)) {
                call.respond(HttpStatusCode.Conflict,
                    AuthResponse(false, "Already a member of this group"))
                return@post
            }

            // Add member to group
            addGroupMember(group["group_id"] as Int, request.memberPhone)

            // Get group details to return
            val groupDetails = getGroupDetails(group["group_id"] as Int)

            call.respond(HttpStatusCode.OK, groupDetails)
        }


// Get group details
        get("/groupDetails/{groupId}") {
            val groupId = call.parameters["groupId"]?.toIntOrNull()
            if (groupId == null) {
                call.respond(HttpStatusCode.BadRequest,
                    AuthResponse(false, "Invalid group ID"))
                return@get
            }

            val groupDetails = getGroupDetails(groupId)

            call.respond(HttpStatusCode.OK, groupDetails)
        }

// Update group (name or QR)
        post("/updateGroup") {
            val request = call.receive<UpdateGroupRequest>()

            // Verify group exists and requester is creator
            val group = getGroupById(request.groupId)
            if (group == null) {
                call.respond(HttpStatusCode.NotFound,
                    AuthResponse(false, "Group not found"))
                return@post
            }

            // Update group
            updateGroup(request.groupId, request.newName, request.newQr)

            call.respond(HttpStatusCode.OK,
                AuthResponse(true, "Group updated successfully"))
        }

        post("/updateGroup") {
            val request = call.receive<UpdateGroupRequest>()
            updateGroup(request.groupId, request.newName, request.newQr)
        }

// Leave group
        post("/leaveGroup") {
            val request = call.receive<LeaveGroupRequest>()

            // Verify group exists
            if (!groupExists(request.groupId)) {
                call.respond(HttpStatusCode.NotFound,
                    AuthResponse(false, "Group not found"))
                return@post
            }

            // Remove member
            removeGroupMember(request.groupId, request.memberPhone)

            call.respond(HttpStatusCode.OK,
                AuthResponse(true, "Left group successfully"))
        }

// Delete group (creator only)
        post("/deleteGroup") {
            val request = call.receive<DeleteGroupRequest>()

            // Verify group exists and requester is creator
            val group = getGroupById(request.groupId)
            if (group == null) {
                call.respond(HttpStatusCode.NotFound,
                    AuthResponse(false, "Group not found"))
                return@post
            }

            if (group["creator_phone"] != request.creatorPhone) {
                call.respond(HttpStatusCode.Forbidden,
                    AuthResponse(false, "Only creator can delete group"))
                return@post
            }

            // Delete group (cascade will remove members)
            deleteGroup(request.groupId)

            call.respond(HttpStatusCode.OK,
                AuthResponse(true, "Group deleted successfully"))
        }

// 🔁 Inside /updateGroupBill/{groupId}
        post("/updateGroupBill/{groupId}") {
            val groupId = call.parameters["groupId"]?.toIntOrNull()
            if (groupId == null) {
                call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Invalid group ID"))
                return@post
            }

            val request = call.receive<BillRequest>()

            // ✅ Fetch group BEFORE update
            val group = getGroupById(groupId)
            if (group == null) {
                call.respond(HttpStatusCode.NotFound, AuthResponse(false, "Group not found"))
                return@post
            }

            val oldConsumer = group["bill_consumer_number"] as String
            val oldOperator = group["bill_operator"] as String
            val oldBillId = group["bill_id"] as? Int

            val oldBillDate = oldBillId?.let { getBillDateById(it) }

            // ✅ Check if bill exists
            if (!billExists(request.consumerNumber, request.operator)) {
                call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Bill not found for provided details"))
                return@post
            }

            val newBillId = getLatestBillId(request.consumerNumber, request.operator)
            val newBillDate = getBillDateById(newBillId)

            // ✅ Update group bill + bill_id
            updateGroupBillWithId(groupId, request.consumerNumber, request.operator, newBillId)

            // ✅ Conditional reset
            if (newBillDate != null && (
                        request.consumerNumber != oldConsumer || request.operator != oldOperator ||
                                (oldBillDate != null && newBillDate.after(oldBillDate))
                        )) {
                resetGroupMembersForNewBill(groupId)
            }

            val updatedDetails = getGroupDetails(groupId)
            call.respond(HttpStatusCode.OK, updatedDetails)
        }

        post("/markAsPaid") {
            val request = call.receive<MarkPaidRequest>()
            val phone = request.memberPhone
            val groupId = request.groupId
            val amount = request.splitAmount
            val consumerNumber = request.consumerNumber


            try {
                createDataSource().connection.use { conn ->
                    conn.autoCommit = false
// ✅ Fetch current reading and current offset before inserting into splits
                    val (readingg, offset) = conn.prepareStatement(
                        "SELECT current_reading, offset_value FROM group_members WHERE member_phone = ? AND group_id = ?"
                    ).use { stmt ->
                        stmt.setString(1, phone)
                        stmt.setInt(2, groupId)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                rs.getDouble("current_reading") to rs.getFloat("offset_value")
                            } else throw IllegalStateException("User not found in group")
                        }
                    }

// ✅ Calculate adjusted reading
                    val adjustedReading = (readingg - offset).coerceAtLeast(0.0)

// ✅ Insert into splits with units_paid_for
                    conn.prepareStatement(
                        """
    INSERT INTO splits (phone_number, split_amount, group_id, consumer_number, datetime_paid, bill_generation_date, units_paid_for)
    VALUES (?, ?, ?, ?, NOW(), 
        (SELECT bill_date FROM bills WHERE consumer_number = ? ORDER BY bill_date DESC LIMIT 1),
        ?
    )
    """.trimIndent()
                    ).use { stmt ->
                        stmt.setString(1, phone)
                        stmt.setDouble(2, amount)
                        stmt.setInt(3, groupId)
                        stmt.setString(4, consumerNumber)
                        stmt.setString(5, consumerNumber)
                        stmt.setDouble(6, adjustedReading)
                        stmt.executeUpdate()
                    }


                    // ✅ 2. Update payment status to 'Paid'
                    conn.prepareStatement(
                        "UPDATE group_members SET payment_status = 'Paid' WHERE member_phone = ? AND group_id = ?"
                    ).use { stmt ->
                        stmt.setString(1, phone)
                        stmt.setInt(2, groupId)
                        stmt.executeUpdate()
                    }

                    // ✅ 3. Fetch current reading and current offset (for backup)
                    val (reading, existingOffset, existingOrigin) = conn.prepareStatement(
                        "SELECT current_reading, offset_value, offset_origin FROM group_members WHERE member_phone = ? AND group_id = ?"
                    ).use { stmt ->
                        stmt.setString(1, phone)
                        stmt.setInt(2, groupId)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                Triple(
                                    rs.getDouble("current_reading"),
                                    rs.getFloat("offset_value"),
                                    rs.getString("offset_origin")
                                )
                            } else null
                        }
                    } ?: throw IllegalStateException("User not found in group")

                    // ✅ 4. Update offset and back up old offset
                    conn.prepareStatement(
                        """
                UPDATE group_members
                SET 
                    previous_offset_value = offset_value,
                    previous_offset_origin = offset_origin,
                    offset_value = ?, 
                    offset_origin = 'Auto'
                WHERE member_phone = ? AND group_id = ?
                """.trimIndent()
                    ).use { stmt ->
                        stmt.setDouble(1, reading)
                        stmt.setString(2, phone)
                        stmt.setInt(3, groupId)
                        stmt.executeUpdate()
                    }

                    conn.commit()
                }

                println("✅ Split: amount=$amount, group=$groupId, phone=$phone")
                call.respond(HttpStatusCode.OK, AuthResponse(true, "Marked as paid and recorded"))
            } catch (e: Exception) {
                println("❌ Failed to mark as paid: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, AuthResponse(false, "Failed: ${e.message}"))
            }
        }




        post("/resetPaymentStatus") {
            val request = call.receive<MarkPaidRequest>()
            val phone = request.memberPhone
            val groupId = request.groupId
            val consumerNumber = request.consumerNumber

            try {
                createDataSource().connection.use { conn ->
                    conn.autoCommit = false

                    // ✅ 1. Reset payment status
                    conn.prepareStatement(
                        """
                UPDATE group_members
                SET payment_status = 'Pending'
                WHERE group_id = ? AND member_phone = ?
                """.trimIndent()
                    ).use { stmt ->
                        stmt.setInt(1, groupId)
                        stmt.setString(2, phone)
                        stmt.executeUpdate()
                    }

                    // ✅ 2. Delete from splits table
                    conn.prepareStatement(
                        """
                DELETE FROM splits
                WHERE phone_number = ?
                AND consumer_number = ?
                AND bill_generation_date = (
                    SELECT bill_date FROM bills
                    WHERE consumer_number = ?
                    ORDER BY bill_date DESC
                    LIMIT 1
                )
                """.trimIndent()
                    ).use { stmt ->
                        stmt.setString(1, phone)
                        stmt.setString(2, consumerNumber)
                        stmt.setString(3, consumerNumber)
                        stmt.executeUpdate()
                    }

                    // ✅ 3. Restore previous offset value
                    conn.prepareStatement(
                        """
                UPDATE group_members
                SET 
                    offset_value = previous_offset_value,
                    offset_origin = previous_offset_origin,
                    previous_offset_value = NULL,
                    previous_offset_origin = NULL
                WHERE group_id = ? AND member_phone = ?
                """.trimIndent()
                    ).use { stmt ->
                        stmt.setInt(1, groupId)
                        stmt.setString(2, phone)
                        stmt.executeUpdate()
                    }

                    conn.commit()
                }

                println("✅ Payment status reset and offset restored for phone=$phone, group=$groupId")
                call.respond(HttpStatusCode.OK, AuthResponse(true, "Payment status reset"))
            } catch (e: Exception) {
                println("❌ Failed to reset payment: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, AuthResponse(false, "Reset failed: ${e.message}"))
            }
        }




// Submit meter reading
        post("/submitReading") {
            val request = call.receive<SubmitReadingRequest>()
            val phone = request.phone
            val groupId = request.groupId
            val reading = request.reading.toDoubleOrNull()
            val offset = request.offset?.toFloatOrNull()

            if (reading == null) {
                call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Invalid reading"))
                return@post
            }

            try {
                createDataSource().connection.use { conn ->
                    conn.autoCommit = false

                    // 1. Update current reading
                    conn.prepareStatement(
                        """
                UPDATE group_members
                SET current_reading = ?
                WHERE member_phone = ? AND group_id = ?
                """.trimIndent()
                    ).use { stmt ->
                        stmt.setDouble(1, reading)
                        stmt.setString(2, phone)
                        stmt.setInt(3, groupId)
                        stmt.executeUpdate()
                    }

                    // 2. If offset was provided, set it manually
                    if (offset != null) {
                        conn.prepareStatement(
                            """
                    UPDATE group_members
                    SET offset_value = ?, offset_origin = 'manual'
                    WHERE member_phone = ? AND group_id = ?
                    """.trimIndent()
                        ).use { stmt ->
                            stmt.setFloat(1, offset)
                            stmt.setString(2, phone)
                            stmt.setInt(3, groupId)
                            stmt.executeUpdate()
                        }
                    }

                    conn.commit()
                }

                println("✅ Reading submitted: $reading kWh for phone=$phone in group=$groupId (offset=${offset ?: "unchanged"})")
                call.respond(HttpStatusCode.OK, AuthResponse(true, "Reading submitted"))
            } catch (e: Exception) {
                println("❌ Failed to submit reading: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, AuthResponse(false, "Failed: ${e.message}"))
            }
        }

        get("/getGroupForUser/{phone}") {
            val phone = call.parameters["phone"]
            log.info("🟡 API hit: /getGroupForUser/$phone")
            if (phone.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Phone number missing"))
                return@get
            }

            val groupId = findGroupIdByMemberPhone(phone)
            environment.log.info("🔵 Group ID found for $phone: $groupId")

            if (groupId != null) {
                val groupDetails = getGroupDetails(groupId)
                environment.log.info("🟢 Group details: $groupDetails")
                call.respond(HttpStatusCode.OK, groupDetails)
            } else {
                environment.log.info("🔴 No group found for $phone")
                call.respond(HttpStatusCode.OK, null)
            }
        }

        get("/getPaymentHistory/{phone}") {
            val phone = call.parameters["phone"]

            if (phone.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Phone number is required"))
                return@get
            }

            try {
                val query = """
    SELECT s.split_amount, s.consumer_number, s.bill_generation_date, s.datetime_paid,
           g.group_name, g.bill_operator, s.units_paid_for
    FROM splits s
    JOIN groups g ON s.group_id = g.group_id
    WHERE s.phone_number = ?
    ORDER BY s.datetime_paid DESC
""".trimIndent()


                val history = mutableListOf<PaymentHistoryEntry>()

                createDataSource().connection.use { conn ->
                    conn.prepareStatement(query).use { stmt ->
                        stmt.setString(1, phone)
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                history.add(
                                    PaymentHistoryEntry(
                                        amount = rs.getDouble("split_amount"),
                                        consumerNumber = rs.getString("consumer_number"),
                                        billGenerationDate = rs.getTimestamp("bill_generation_date").toString(),
                                        datetimePaid = rs.getTimestamp("datetime_paid").toString(),
                                        groupName = rs.getString("group_name"),
                                        operator = rs.getString("bill_operator"),
                                        unitsPaidFor = rs.getFloat("units_paid_for")

                                    )
                                )
                            }
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, history)
            } catch (e: Exception) {
                call.application.environment.log.error("❌ Failed to fetch payment history", e)
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }




        get("/ping") {
            call.application.environment.log.info("✅ /ping was hit")
            call.respondText("pong")
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
        ORDER BY bill_date DESC
        LIMIT 1
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
    name: String
): Int {
    val query = """
        INSERT INTO users 
        (phone_number, password_hash, name)
        VALUES (?, ?, ?)
        RETURNING id
    """.trimIndent()

    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, phoneNumber)
            stmt.setString(2, passwordHash)
            stmt.setString(3, name)
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


private fun generateRandomCode(length: Int): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..length)
        .map { chars.random() }
        .joinToString("")
}

private fun createGroup(
    name: String,
    creatorPhone: String,
    code: String,
    qr: String,
    consumerNumber: String,
    operator: String
): Int {
    val query = """
        INSERT INTO groups 
        (group_name, creator_phone, group_code, group_qr, bill_consumer_number, bill_operator)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING group_id
    """.trimIndent()

    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, creatorPhone)
            stmt.setString(3, code)
            stmt.setString(4, qr)
            stmt.setString(5, consumerNumber)
            stmt.setString(6, operator)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getInt("group_id")
                } else {
                    throw IllegalStateException("Failed to create group")
                }
            }
        }
    }
}

private fun getGroupByCode(code: String): Map<String, Any>? {
    val query = "SELECT * FROM groups WHERE group_code = ?"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, code)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    mapOf(
                        "group_id" to rs.getInt("group_id"),
                        "group_name" to rs.getString("group_name"),
                        "creator_phone" to rs.getString("creator_phone"),
                        "group_code" to rs.getString("group_code"),
                        "group_qr" to rs.getString("group_qr"),
                        "bill_consumer_number" to rs.getString("bill_consumer_number"),
                        "bill_operator" to rs.getString("bill_operator")
                    )
                } else {
                    null
                }
            }
        }
    }
}

private fun getGroupById(groupId: Int): Map<String, Any>? {
    val query = "SELECT * FROM groups WHERE group_id = ?"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    mapOf(
                        "group_id" to rs.getInt("group_id"),
                        "group_name" to rs.getString("group_name"),
                        "creator_phone" to rs.getString("creator_phone"),
                        "group_code" to rs.getString("group_code"),
                        "group_qr" to rs.getString("group_qr"),
                        "bill_consumer_number" to rs.getString("bill_consumer_number"),
                        "bill_operator" to rs.getString("bill_operator"),
                        "bill_id" to rs.getInt("bill_id") // ✅ now fetched too
                    )
                } else {
                    null
                }
            }
        }
    }
}


private fun addGroupMember(groupId: Int, memberPhone: String) {
    val query = """
        INSERT INTO group_members 
        (group_id, member_phone)
        VALUES (?, ?)
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.setString(2, memberPhone)
            stmt.executeUpdate()
        }
    }
}

private fun isGroupMember(groupId: Int, memberPhone: String): Boolean {
    val query = """
        SELECT 1 FROM group_members 
        WHERE group_id = ? AND member_phone = ?
    """.trimIndent()

    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.setString(2, memberPhone)
            stmt.executeQuery().use { rs ->
                rs.next()
            }
        }
    }
}

private fun groupExists(groupId: Int): Boolean {
    val query = "SELECT 1 FROM groups WHERE group_id = ?"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.executeQuery().use { rs ->
                rs.next()
            }
        }
    }
}

private fun getGroupDetails(groupId: Int): GroupDetailsResponse {
    val group = getGroupById(groupId)!!

    val bill = fetchBillFromDB(
        group["bill_consumer_number"] as String,
        group["bill_operator"] as String
    )!!

    val creator = getUserByPhone(group["creator_phone"] as String)!!
    val creatorName = creator["name"] as String

    val membersQuery = """
        SELECT u.name, gm.member_phone, gm.current_reading, gm.payment_status,
               gm.offset_value, gm.offset_origin, gm.previous_offset_value
        FROM group_members gm
        JOIN users u ON gm.member_phone = u.phone_number
        WHERE gm.group_id = ?
    """.trimIndent()

    val members = mutableListOf<MemberInfo>()
    val pieChartData = mutableMapOf<String, Float>()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(membersQuery).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    val phone = rs.getString("member_phone")
                    val reading = rs.getFloat("current_reading")
                    val paymentStatus = rs.getString("payment_status")
                    val previousOffsetValue = rs.getFloat("previous_offset_value")

                    val amountToPay = if (reading > 0) {
                        bill.totalAmount.toFloat() / countGroupMembers(groupId)
                    } else 0f

                    val offsetValue = rs.getFloat("offset_value")
                    val offsetOrigin = rs.getString("offset_origin")

                    members.add(MemberInfo(
                        name = name,
                        phone = phone,
                        reading = if (reading > 0) reading else null,
                        amountToPay = amountToPay,
                        paymentStatus = paymentStatus,
                        offsetValue = if (offsetValue > 0) offsetValue else null,
                        offsetOrigin = offsetOrigin ?: "",
                        previousOffsetValue = if (previousOffsetValue > 0) previousOffsetValue else null
                    ))

                    pieChartData[name] = amountToPay
                }
            }
        }
    }

    return GroupDetailsResponse(
        groupId = groupId,
        groupName = group["group_name"] as String,
        groupCode = group["group_code"] as String,
        groupQr = group["group_qr"] as String,
        creatorName = creatorName,
        creatorPhone = group["creator_phone"] as String,
        billDetails = bill,
        consumerNumber = group["bill_consumer_number"] as String,
        operator = group["bill_operator"] as String,
        members = members,
        pieChartData = pieChartData
    )
}


private fun countGroupMembers(groupId: Int): Int {
    val query = "SELECT COUNT(*) FROM group_members WHERE group_id = ?"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getInt(1)
                } else {
                    0
                }
            }
        }
    }
}

private fun updateGroup(groupId: Int, newName: String?, newQr: String?) {
    val query = buildString {
        append("UPDATE groups SET ")
        val updates = mutableListOf<String>()
        if (newName != null) updates.add("group_name = ?")
        if (newQr != null) updates.add("group_qr = ?")
        append(updates.joinToString(", "))
        append(" WHERE group_id = ?")
    }

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            var paramIndex = 1
            if (newName != null) stmt.setString(paramIndex++, newName)
            if (newQr != null) stmt.setString(paramIndex++, newQr)
            stmt.setInt(paramIndex, groupId)
            stmt.executeUpdate()
        }
    }
}

private fun removeGroupMember(groupId: Int, memberPhone: String) {
    val query = """
        DELETE FROM group_members 
        WHERE group_id = ? AND member_phone = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.setString(2, memberPhone)
            stmt.executeUpdate()
        }
    }
}

private fun deleteGroup(groupId: Int) {
    val query = "DELETE FROM groups WHERE group_id = ?"
    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.executeUpdate()
        }
    }
}

private fun updateMemberReading(groupId: Int, memberPhone: String, reading: Float) {
    val query = """
        UPDATE group_members 
        SET current_reading = ?
        WHERE group_id = ? AND member_phone = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setFloat(1, reading)
            stmt.setInt(2, groupId)
            stmt.setString(3, memberPhone)
            stmt.executeUpdate()
        }
    }
}

private fun getUserByPhone(phoneNumber: String): Map<String, Any>? {
    val query = """
        SELECT id, password_hash, name
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
                        "name" to rs.getString("name")
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

private fun findGroupIdByMemberPhone(phone: String): Int? {
    val query = "SELECT group_id FROM group_members WHERE member_phone = ? LIMIT 1"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, phone)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getInt("group_id") else null
            }
        }
    }
}

private fun updateGroupBill(groupId: Int, consumerNumber: String, operator: String) {
    val query = """
        UPDATE groups 
        SET bill_consumer_number = ?, bill_operator = ?
        WHERE group_id = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, consumerNumber)
            stmt.setString(2, operator)
            stmt.setInt(3, groupId)
            stmt.executeUpdate()
        }
    }
}

private fun updateMemberPaymentStatus(groupId: Int, memberPhone: String) {
    val query = """
        UPDATE group_members
        SET payment_status = 'Paid'
        WHERE group_id = ? AND member_phone = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.setString(2, memberPhone)
            stmt.executeUpdate()
        }
    }
}

private fun insertSplitRecord(
    phoneNumber: String,
    splitAmount: Double,
    groupId: Int,
    consumerNumber: String
) {
    val query = """
        INSERT INTO splits (phone_number, split_amount, group_id, consumer_number, bill_generation_date)
        VALUES (?, ?, ?, ?, (
            SELECT bill_date FROM bills
            WHERE consumer_number = ?
            ORDER BY bill_date DESC
            LIMIT 1
        ))
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, phoneNumber)
            stmt.setDouble(2, splitAmount)
            stmt.setInt(3, groupId)
            stmt.setString(4, consumerNumber)
            stmt.setString(5, consumerNumber) // for subquery
            stmt.executeUpdate()
        }
    }
}

private fun resetMemberPaymentStatus(groupId: Int, memberPhone: String) {
    val query = """
        UPDATE group_members
        SET payment_status = 'Pending',
            offset_value = NULL,
            offset_origin = NULL
        WHERE group_id = ? AND member_phone = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.setString(2, memberPhone)
            stmt.executeUpdate()
        }
    }
}


private fun deleteSplitRecord(phoneNumber: String, consumerNumber: String) {
    val query = """
        DELETE FROM splits
        WHERE phone_number = ? AND consumer_number = ?
        AND bill_generation_date = (
            SELECT bill_date FROM bills
            WHERE consumer_number = ?
            ORDER BY bill_date DESC
            LIMIT 1
        )
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, phoneNumber)
            stmt.setString(2, consumerNumber)
            stmt.setString(3, consumerNumber)
            stmt.executeUpdate()
        }
    }
}

private fun updateMemberOffset(groupId: Int, memberPhone: String, offsetValue: Float, origin: String) {
    val query = """
        UPDATE group_members
        SET offset_value = ?, offset_origin = ?
        WHERE group_id = ? AND member_phone = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setFloat(1, offsetValue)
            stmt.setString(2, origin)
            stmt.setInt(3, groupId)
            stmt.setString(4, memberPhone)
            stmt.executeUpdate()
        }
    }
}

private fun setCurrentReadingAsOffset(groupId: Int, memberPhone: String) {
    val query = """
        UPDATE group_members
        SET offset_value = current_reading, offset_origin = 'Auto'
        WHERE group_id = ? AND member_phone = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.setString(2, memberPhone)
            stmt.executeUpdate()
        }
    }
}

fun resetGroupMembersForNewBill(groupId: Int) {
    val query = """
        UPDATE group_members
        SET current_reading = 0,
            payment_status = 'Pending'
        WHERE group_id = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, groupId)
            stmt.executeUpdate()
        }
    }
}

private fun getLatestBillDate(consumerNumber: String): java.sql.Timestamp? {
    val query = "SELECT bill_date FROM bills WHERE consumer_number = ? ORDER BY bill_date DESC LIMIT 1"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, consumerNumber)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getTimestamp("bill_date") else null
            }
        }
    }
}

private fun getLatestBillId(consumerNumber: String, operator: String): Int {
    val query = "SELECT id FROM bills WHERE consumer_number = ? AND operator = ? ORDER BY bill_date DESC LIMIT 1"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, consumerNumber)
            stmt.setString(2, operator)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getInt("id") else throw IllegalStateException("No bill found")
            }
        }
    }
}

private fun getBillDateById(billId: Int): java.sql.Timestamp? {
    val query = "SELECT bill_date FROM bills WHERE id = ?"
    return createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setInt(1, billId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getTimestamp("bill_date") else null
            }
        }
    }
}

private fun updateGroupBillWithId(groupId: Int, consumerNumber: String, operator: String, billId: Int) {
    val query = """
        UPDATE groups 
        SET bill_consumer_number = ?, bill_operator = ?, bill_id = ?
        WHERE group_id = ?
    """.trimIndent()

    createDataSource().connection.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.setString(1, consumerNumber)
            stmt.setString(2, operator)
            stmt.setInt(3, billId)
            stmt.setInt(4, groupId)
            stmt.executeUpdate()
        }
    }
}

