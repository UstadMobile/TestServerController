package com.ustadmobile.testservercontroller

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.koin.dsl.module

val serverKoinModule = module {

    single<OkHttpClient> {

        OkHttpClient.Builder()
            .dispatcher(
                Dispatcher().also {
                    it.maxRequests = 30
                    it.maxRequestsPerHost = 10
                }
            )
            .build()
    }

    single<HttpClient> {
        HttpClient(OkHttp) {
            engine {
                preconfigured = get()
            }
        }
    }
}