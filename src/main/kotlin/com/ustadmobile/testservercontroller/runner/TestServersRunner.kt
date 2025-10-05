package com.ustadmobile.testservercontroller.runner

import com.ustadmobile.test.http.waitForUrl
import com.ustadmobile.testservercontroller.PROP_BASE_DIR
import com.ustadmobile.testservercontroller.PROP_ENV
import com.ustadmobile.testservercontroller.PROP_PORT_RANGE
import com.ustadmobile.testservercontroller.PROP_RUN_COMMAND
import com.ustadmobile.testservercontroller.PROP_SHUTDOWN_URL
import com.ustadmobile.testservercontroller.RunningCmd
import com.ustadmobile.testservercontroller.util.DEFAULT_FROM_PORT
import com.ustadmobile.testservercontroller.util.DEFAULT_UNTIL_PORT
import com.ustadmobile.testservercontroller.util.findFreePort
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.toURI
import io.ktor.server.config.ApplicationConfig
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

class TestServersRunner(
    config: ApplicationConfig,
    private val okHttpClient: OkHttpClient,
    private val httpClient: HttpClient,
) {

    data class StartServerResponse(
        val port: Int,
        val url: String,
    )

    val runningCmdMap = ConcurrentMap<Int, RunningCmd>()

    val runCommand = config.property(PROP_RUN_COMMAND).getString()

    val workspaceBaseDir = File(config.propertyOrNull(PROP_BASE_DIR)?.getString() ?: ".")

    val shutdownUrl = config.property(PROP_SHUTDOWN_URL).getString()

    val portRangeStr = config.propertyOrNull(PROP_PORT_RANGE)?.getString()
        ?: "$DEFAULT_FROM_PORT-$DEFAULT_UNTIL_PORT"

    val envVariables = config.propertyOrNull(PROP_ENV)?.getMap()?.mapNotNull { entry ->
        (entry.value as? String)?.let { entry.key to it }
    }?.toMap() ?: emptyMap()

    val split = portRangeStr.split("-").map { it.toInt() }.also {
        if(it.size != 2)
            throw IllegalArgumentException(
                "$$PROP_PORT_RANGE must be in the form of x-y e.g. $DEFAULT_FROM_PORT-$DEFAULT_UNTIL_PORT"
            )
    }

    val fromPort = split.first()
    val untilPort = split.last()

    fun startServer(
        controlServerUrl: String,
        waitForUrl: String?,
    ) : StartServerResponse {
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


        val serverUrl = URLBuilder(controlServerUrl).apply {
            port = serverPort
        }.build()

        if(waitForUrl != null) {
            okHttpClient.waitForUrl(
                url = serverUrl.toURI().resolve(waitForUrl).toString()
            )
        }

        return StartServerResponse(
            port = serverPort,
            url = serverUrl.toString(),
        )
    }

    suspend fun stopServer(
        port: Int
    ) {
        runningCmdMap[port] ?: throw IllegalArgumentException("Running server not found")

        val shutdownUrl = Url("http://localhost:$port$shutdownUrl")
        println("Shutdown $shutdownUrl")
        val shutdownText = httpClient.get(shutdownUrl).bodyAsText()
        println(shutdownText)
        runningCmdMap.remove(port)
    }

    suspend fun stopAll() {
        val runningServerPorts = runningCmdMap.keys
        runningServerPorts.forEach {
            try {
                stopServer(it)
            }catch(t: Throwable) {
                println("WARNING: stopAll couldn't stop server on port $it: ${t.message}")
                //t.printStackTrace()
            }
        }
    }

}