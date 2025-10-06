package com.ustadmobile.testservercontroller

import com.ustadmobile.testservercontroller.runner.TestServersRunner
import com.ustadmobile.testservercontroller.util.clientProtocolAndHost
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val PROP_RUN_COMMAND = "testservercontroller.cmd"

const val PROP_BASE_DIR = "testservercontroller.basedir"

const val PROP_SHUTDOWN_URL = "testservercontroller.shutdown.url"

const val PROP_ENV = "testservercontroller.env"

const val PROP_PORT_RANGE = "testservercontroller.portRange"

const val PROP_URLSUBSTITUTION = "testservercontroller.urlsubstitution"

fun Routing.TestServerControllerRoute(
    testServersRunner: TestServersRunner
) {
    route("testcontroller") {

        get("start") {
            val startServerResponse = testServersRunner.startServer(
                controlServerUrl = call.request.headers.clientProtocolAndHost(),
                waitForUrl = call.request.queryParameters["waitForUrl"],
            )

            call.response.header("cache-control", "no-cache, no-store")

            call.respondText(
                contentType = ContentType.Application.Json,
                text = """
                    { 
                        "port": ${startServerResponse.port},
                         "url": "${startServerResponse.url}"
                    }
                """.trimIndent()
            )
        }

        get("stop") {
            try {
                val port = call.request.queryParameters["port"]?.toInt() ?: throw IllegalArgumentException("no port")
                testServersRunner.stopServer(port = port)
                call.response.header("cache-control", "no-cache, no-store")
                call.respondText("OK: stopped server runner on port $port")
            }catch(t: Throwable) {
                t.printStackTrace()
                throw t
            }
        }
    }

}