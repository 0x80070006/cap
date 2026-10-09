package org.capnav.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpException(val code: Int, message: String) : IOException(message)

object HttpClients {
    const val USER_AGENT = "Cap/0.1 (+https://github.com/0x80070006/cap)"
    private const val MAX_BODY_BYTES = 8L shl 20

    /**
     * Shared hardening: HTTPS only (enforced by networkSecurityConfig too), TLS 1.3 preferred with
     * TLS 1.2 + AEAD suites as floor (ADR-006), no cookies, no cache, generic User-Agent, optional
     * SPKI pins for self-hosted servers.
     */
    fun base(pins: Map<String, List<String>> = emptyMap()): OkHttpClient.Builder {
        val pinner = CertificatePinner.Builder().apply {
            pins.forEach { (host, list) -> list.forEach { add(host, it) } }
        }.build()
        return OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
            .certificatePinner(pinner)
            .followSslRedirects(true)
            .followRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                require(req.url.isHttps) { "cleartext request refused" }
                chain.proceed(req.newBuilder().header("User-Agent", USER_AGENT).build())
            })
    }

    /** Interceptor that accounts every exchange in the privacy log, by purpose. */
    fun logging(log: NetworkLog, purposeOf: (HttpUrl) -> Purpose) = Interceptor { chain ->
        val req = chain.request()
        val res = chain.proceed(req)
        val sent = req.url.toString().length.toLong() + (req.body?.contentLength()?.coerceAtLeast(0) ?: 0)
        log.record(purposeOf(req.url), req.url.host, sent, res.body?.contentLength()?.coerceAtLeast(0) ?: 0)
        res
    }

    suspend fun OkHttpClient.getString(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val call = newCall(Request.Builder().url(url).get().build())
        call.await().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code, "HTTP ${res.code}")
            val body = res.body ?: throw IOException("empty body")
            val len = body.contentLength()
            if (len > MAX_BODY_BYTES) throw IOException("response too large")
            val source = body.source()
            source.request(MAX_BODY_BYTES + 1)
            if (source.buffer.size > MAX_BODY_BYTES) throw IOException("response too large")
            source.buffer.readUtf8()
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) = cont.resume(response)
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}
