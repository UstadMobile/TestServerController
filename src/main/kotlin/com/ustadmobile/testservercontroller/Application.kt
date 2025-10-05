package com.ustadmobile.testservercontroller

import com.ustadmobile.testservercontroller.runner.TestServersRunner
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    val okHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().also {
                it.maxRequests = 30
                it.maxRequestsPerHost = 10
            }
        )
        .build()

    val httpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
    }

    val testServersRunner = TestServersRunner(
        config = environment.config,
        okHttpClient = okHttpClient,
        httpClient = httpClient,
    )

    //Use ApplicationStopped event to stop any pending running servers.
    //As per https://ktor.io/docs/server-events.html#handle-events-application
    monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server is stopped")
        // Release resources and unsubscribe from events
        runBlocking {
            testServersRunner.stopAll()
        }

        monitor.unsubscribe(ApplicationStopped) {}
    }

    configureHTTP()
    configureSerialization()
    configureMonitoring()
    configureRouting(
        testServersRunner = testServersRunner,
    )
}
