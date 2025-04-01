package com.electrosplit
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.postgresql.ds.PGSimpleDataSource
import javax.sql.DataSource

fun Application.configureRouting() {
    routing {
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

fun fetchBillFromDB(consumerNumber: String, operator: String): BillResponse? {
    val dataSource = createDataSource()
    val connection = dataSource.connection
    val query = "SELECT total_units, total_amount FROM bills WHERE consumer_number = ? AND operator = ?"
    val statement = connection.prepareStatement(query)
    statement.setString(1, consumerNumber)
    statement.setString(2, operator)
    val resultSet = statement.executeQuery()
    return if (resultSet.next()) {
        BillResponse(resultSet.getInt("total_units"), resultSet.getDouble("total_amount"))
    } else {
        null
    }
}

fun createDataSource(): DataSource {
    val dataSource = PGSimpleDataSource()
    dataSource.user = "electrosplit"
    dataSource.password = "electrosplit123"
    dataSource.databaseName = "electrosplit_db"
    dataSource.serverNames = arrayOf("localhost")
    dataSource.portNumbers = intArrayOf(5432)  // Use 5433 if you changed the port
    return dataSource
}