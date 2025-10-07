package com.ustadmobile.testservercontroller.runner

import com.ustadmobile.test.http.waitForUrl
import com.ustadmobile.testservercontroller.PROP_BASE_DIR
import com.ustadmobile.testservercontroller.PROP_ENV
import com.ustadmobile.testservercontroller.PROP_PORT_RANGE
import com.ustadmobile.testservercontroller.PROP_RUN_COMMAND
import com.ustadmobile.testservercontroller.PROP_SHUTDOWN_URL
import com.ustadmobile.testservercontroller.PROP_URLSUBSTITUTION
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
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import okhttp3.OkHttpClient
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TestServersRunner(
    config: ApplicationConfig,
    private val okHttpClient: OkHttpClient,
    private val httpClient: HttpClient,
    private val logger: Logger,
) {
    data class StartServerRequest(
        val controlServerUrl: String,
        val waitForUrl: String?,
        val name: String?,
    )

    data class StartServerResponse(
        val port: Int,
        val url: String,
    )

    val runningCmdMap = ConcurrentMap<Int, RunningCmd>()

    val runCommand = config.property(PROP_RUN_COMMAND).getString()

    val workspaceBaseDir = File(config.propertyOrNull(PROP_BASE_DIR)?.getString() ?: DEFAULT_BASEDIR)

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

    val urlSubstitution = config.propertyOrNull(PROP_URLSUBSTITUTION)?.getString()

    @OptIn(ExperimentalTime::class)
    fun startServer(
        request: StartServerRequest,
    ) : StartServerResponse {
        val serverPort = findFreePort(fromPort, untilPort)
        val runArgs = runCommand.split(Regex("\\s+")).filter {
            it.isNotEmpty()
        }.toMutableList()

        val scope = CoroutineScope(Dispatchers.Default + Job())

        val dateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dirName = request.name?.filter { it.isLetterOrDigit() }
            ?: ("run-${dateTime.date.year}_${dateTime.date.month.number.toString().padStart(2, '0')}_" +
                    "${dateTime.date.day.toString().padStart(2, '0')}_" +
                    "${dateTime.time.hour}_${dateTime.time.minute}_${dateTime.second}")

        val cmdWorkspaceDir = File(workspaceBaseDir, dirName).also {
            it.mkdirs()
        }

        logger.info("TestServerRunner: port=$serverPort starting command $runCommand (workingDir=${cmdWorkspaceDir.absolutePath})")

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

        logger.info("TestServerRunner: port=$serverPort process started PID=${process.pid()}")
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

        val serverUrl = if(urlSubstitution != null && urlSubstitution.isNotEmpty()) {
            Url(urlSubstitution.replace("_PORT_", serverPort.toString()))
        }else {
            URLBuilder(request.controlServerUrl).apply {
                port = serverPort
            }.build()
        }

        runningCmdMap[serverPort] = RunningCmd(
            port = serverPort,
            process = process,
            serverUrl = serverUrl,
        )

        if(request.waitForUrl != null) {
            okHttpClient.waitForUrl(
                url = serverUrl.toURI().resolve(request.waitForUrl).toString()
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
        logger.info("TestServerRunner: request to stop server on port=$port")
        val runningCmd = runningCmdMap[port] ?: throw IllegalArgumentException("Running server not found")

        val shutdownUrl = runningCmd.serverUrl.toURI().resolve(shutdownUrl)
        logger.info("TestServerRunner: stopping server on $port by ")
        val shutdownText = httpClient.get(shutdownUrl.toString()).bodyAsText()
        logger.info("TestServerRunner: stopping server on port $port : server response: $shutdownText")
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

    companion object {

        const val DEFAULT_BASEDIR = "./build/testservercontroller/base"

    }

}