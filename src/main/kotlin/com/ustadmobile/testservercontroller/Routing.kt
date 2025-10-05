package com.ustadmobile.testservercontroller

import com.ustadmobile.testservercontroller.runner.TestServersRunner
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    testServersRunner: TestServersRunner,
) {
    install(AutoHeadResponse)

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        TestServerControllerRoute(testServersRunner = testServersRunner)
    }
}
