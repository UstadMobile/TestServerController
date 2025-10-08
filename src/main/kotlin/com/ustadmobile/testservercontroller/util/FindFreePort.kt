package com.ustadmobile.testservercontroller.util

import java.io.IOException
import java.net.ServerSocket
import kotlin.random.Random

const val DEFAULT_FROM_PORT = 1_025

const val DEFAULT_UNTIL_PORT = 65_534

/**
 * Find a free TCP port within a specific range
 *
 * @return a random free TCP port
 */
fun findFreePort(
    from: Int = DEFAULT_FROM_PORT,
    until: Int = DEFAULT_UNTIL_PORT,
    numAttempts: Int = 20
): Int {
    val portsTried = mutableListOf<Int>()
    for(i in 1..numAttempts) {
        val portToTry = if(from == until) from else Random.nextInt(from, until)

        try {
            return ServerSocket(portToTry).use { socket ->
                socket.localPort
            }
        } catch (e: IOException) {
            portsTried.add(portToTry)
        }
    }

    throw IllegalStateException("Could not find a free port in range $from to $until after $numAttempts " +
            "attempts (tried ${portsTried.joinToString()}")
}

