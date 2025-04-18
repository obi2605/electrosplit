package com.electrosplit

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    println("✅ println: module() is definitely running")
    log.info("✅ log: module() is definitely running")
    configureSerialization()
    configureRouting()
}