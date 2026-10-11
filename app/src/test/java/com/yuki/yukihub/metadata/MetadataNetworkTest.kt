package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.net.ApiService
import com.yuki.yukihub.net.HttpClient
import com.yuki.yukihub.net.MetadataRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MetadataNetworkTest {
    @Test fun ordinaryClientErrorIsNotRetried() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(400).build())
            val api = HttpClient.retrofit(server.url("/").toString(), HttpClient.defaultBuilder().build()).create(ApiService::class.java)
            val error = runCatching { HttpClient.executeWithRetry({ api.get(server.url("/bad").toString()) }, 3, 1) }.exceptionOrNull()
            assertTrue(error is HttpClient.HttpFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun retryAfterBlocksCallsUntilTheDeadline() {
        val host = "rate-limit-test.invalid"
        HttpClient.recordRateLimit(host, "120")
        val error = runCatching { HttpClient.checkRateLimit(host) }.exceptionOrNull()
        assertTrue(error is HttpClient.HttpFailure)
        assertEquals(429, (error as HttpClient.HttpFailure).status)
    }

    @Test fun disconnectCancelsBlockedResponseBody() =
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse.Builder().body("slow body").bodyDelay(30, TimeUnit.SECONDS).build())
                val task =
                    async(Dispatchers.IO) {
                        MetadataRequests.run(CoverSourceMode.NEXTMOE) {
                            HttpClient.defaultBuilder().build().newCall(Request.Builder().url(server.url("/slow")).build())
                                .execute().use { it.body.string() }
                        }
                    }
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                MetadataRequests.cancel(CoverSourceMode.NEXTMOE)
                withTimeout(2000) { task.join() }
                assertTrue(task.isCancelled)
            }
        }
}
