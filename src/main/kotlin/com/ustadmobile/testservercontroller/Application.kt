package com.ustadmobile.testservercontroller

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    install(Koin) {
        modules(serverKoinModule)
    }

    configureHTTP()
    configureSerialization()
    configureMonitoring()
    configureRouting()
}
