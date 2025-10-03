package com.ustadmobile.testservercontroller

import com.ustadmobile.testservercontroller.util.findFreePort
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import java.io.File

fun Routing.TestServerControllerRoute() {
    val runCommand = environment.config.property("testservercontroller.cmd").getString()
    val workspaceBaseDir = File(environment.config.propertyOrNull("testservercontroller.basedir")?.getString() ?: ".")
    val runningCmdMap = ConcurrentMap<Int, RunningCmd>()
    val shutdownUrl = environment.config.property("testservercontroller.shutdown.url").getString()

    get("start") {
        val port = findFreePort()
        val runArgs = runCommand.split(Regex("\\s+")).filter {
            it.isNotEmpty()
        }.toMutableList()

        val scope = CoroutineScope(Dispatchers.Default + Job())

        val cmdWorkspaceDir = File(workspaceBaseDir, "run-$port").also {
            it.mkdirs()
        }

        val process = ProcessBuilder(runArgs)
            .directory(cmdWorkspaceDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE).also { pb ->
                pb.environment().apply {
                    put("TESTSERVER_WORKSPACE", cmdWorkspaceDir.absolutePath)
                    put("TESTSERVER_PORT", port.toString())
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

        runningCmdMap[port] = RunningCmd(
            port = port,
            process = process
        )

        call.response.header("cache-control", "no-cache, no-store")
        call.respondText("Started on port $port")
    }

    get("stop") {
        try {
            val port = call.request.queryParameters["port"]?.toInt() ?: throw IllegalArgumentException("No port")
            runningCmdMap[port] ?: throw IllegalArgumentException("Running server not found")

            val httpClient: HttpClient by inject()
            val shutdownUrl = Url("http://localhost:$port$shutdownUrl")
            println("Shutdown $shutdownUrl")
            val shutdownText = httpClient.get(shutdownUrl).bodyAsText()

            call.response.header("cache-control", "no-cache, no-store")
            call.respondText("Stopped on port: $port using $shutdownUrl response: $shutdownText")
        }catch(t: Throwable) {
            t.printStackTrace()
        }

    }


}