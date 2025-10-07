package com.ustadmobile.testservercontroller

import io.ktor.http.Url

data class RunningCmd(
    val port: Int,
    val process: Process,
    val serverUrl: Url,
)

