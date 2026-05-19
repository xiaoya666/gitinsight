package com.power.gitinsight.domain.ai

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * team : gitInsight.
 * Class Name: HttpJsonClient
 * Description: Thin JSON-over-HTTPS helper shared by every AiProvider. Uses the JDK 11+ HttpClient so we
 *              don't pull in OkHttp / Ktor just for a few completion calls. Non-2xx responses surface as
 *              IOException with the status code + first 500 chars of the body — enough for providers to
 *              wrap in AiResult.Error without leaking sensitive headers.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal object HttpJsonClient {

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    /**
     * POST [body] as JSON to [url] with optional [headers] (auth, x-api-key, etc).
     * Returns the response body string on 2xx; throws IOException otherwise.
     */
    fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 30
    ): String {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body))

        val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        val status = response.statusCode()
        if (status / 100 != 2) {
            val excerpt = response.body().take(MAX_ERROR_EXCERPT)
            throw IOException("HTTP $status: $excerpt")
        }
        return response.body()
    }

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val MAX_ERROR_EXCERPT = 500
}
