package com.ustadmobile.testservercontroller

import com.ustadmobile.test.http.waitForUrl
import com.ustadmobile.testservercontroller.util.DEFAULT_FROM_PORT
import com.ustadmobile.testservercontroller.util.DEFAULT_UNTIL_PORT
import com.ustadmobile.testservercontroller.util.clientProtocolAndHost
import com.ustadmobile.testservercontroller.util.findFreePort
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.toURI
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.ktor.ext.inject
import java.io.File

const val PROP_RUN_COMMAND = "testservercontroller.cmd"

const val PROP_BASE_DIR = "testservercontroller.basedir"

const val PROP_SHUTDOWN_URL = "testservercontroller.shutdown.url"

const val PROP_ENV = "testservercontroller.env"

const val PROP_PORT_RANGE = "testservercontroller.portRange"

fun Routing.TestServerControllerRoute() {
    route("testcontroller") {
        val okHttpClient: OkHttpClient by inject()
        val httpClient: HttpClient by inject()

        val runCommand = environment.config.property(PROP_RUN_COMMAND).getString()
        val workspaceBaseDir = File(environment.config.propertyOrNull(PROP_BASE_DIR)?.getString() ?: ".")
        val runningCmdMap = ConcurrentMap<Int, RunningCmd>()
        val shutdownUrl = environment.config.property(PROP_SHUTDOWN_URL).getString()
        val envVariables = environment.config.propertyOrNull(PROP_ENV)?.getMap()?.mapNotNull { entry ->
            (entry.value as? String)?.let { entry.key to it }
        }?.toMap() ?: emptyMap()

        val portRangeStr = environment.config.propertyOrNull(PROP_PORT_RANGE)?.getString()
            ?: "$DEFAULT_FROM_PORT-$DEFAULT_UNTIL_PORT"
        val split = portRangeStr.split("-").map { it.toInt() }
        if(split.size != 2) {
            throw IllegalArgumentException("$$PROP_PORT_RANGE must be in the form of x-y e.g. $DEFAULT_FROM_PORT-$DEFAULT_UNTIL_PORT")
        }

        val fromPort = split.first()
        val untilPort = split.last()

        get("start") {
            val serverPort = findFreePort(fromPort, untilPort)
            val runArgs = runCommand.split(Regex("\\s+")).filter {
                it.isNotEmpty()
            }.toMutableList()

            val scope = CoroutineScope(Dispatchers.Default + Job())

            val cmdWorkspaceDir = File(workspaceBaseDir, "run-$serverPort").also {
                it.mkdirs()
            }

            val process = ProcessBuilder(runArgs)
                .directory(cmdWorkspaceDir)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE).also { pb ->
                    pb.environment().apply {
                        put("TESTSERVER_WORKSPACE", cmdWorkspaceDir.absolutePath)
                        put("TESTSERVER_PORT", serverPort.toString())
                        putAll(envVariables)
                    }
                }
                .start()

            scope.launch {
                process.inputStream.bufferedReader().use { inStream ->
                    inStream.forEachLine { println(it) }
                }
            }

            scope.launch {
                process.errorStream.bufferedReader().use { inStream ->
                    inStream.forEachLine { println(it) }
                }
            }

            runningCmdMap[serverPort] = RunningCmd(
                port = serverPort,
                process = process
            )

            val waitForUrl = call.request.queryParameters["waitForUrl"]

            val controlServerUrl = call.request.headers.clientProtocolAndHost()
            val serverUrl = URLBuilder(controlServerUrl).apply {
                port = serverPort
            }.build()

            if(waitForUrl != null) {
                okHttpClient.waitForUrl(
                    url = serverUrl.toURI().resolve(waitForUrl).toString()
                )
            }

            call.response.header("cache-control", "no-cache, no-store")

            call.respondText(
                contentType = ContentType.Application.Json,
                text = """
                    { 
                        "port": $serverPort,
                         "url": "$serverUrl"
                    }
                """.trimIndent()
            )
        }

        get("stop") {
            try {
                val port = call.request.queryParameters["port"]?.toInt() ?: throw IllegalArgumentException("No port")
                runningCmdMap[port] ?: throw IllegalArgumentException("Running server not found")

                val shutdownUrl = Url("http://localhost:$port$shutdownUrl")
                println("Shutdown $shutdownUrl")
                val shutdownText = httpClient.get(shutdownUrl).bodyAsText()

                call.response.header("cache-control", "no-cache, no-store")
                call.respondText("Stopped server on port: $port using $shutdownUrl response: $shutdownText")
            }catch(t: Throwable) {
                t.printStackTrace()
            }

        }
    }

}