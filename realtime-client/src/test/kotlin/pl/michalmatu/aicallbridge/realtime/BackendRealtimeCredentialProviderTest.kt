package pl.michalmatu.aicallbridge.realtime

import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendRealtimeCredentialProviderTest {
    @Test
    fun authenticatedHttpsBackendResponseBecomesTypedShortLivedSecret() {
        var seenRequest: Request? = null
        val client = fakeClient { request ->
            seenRequest = request
            response(
                request,
                200,
                """{"value":"ek_backend_test","expires_at":2000,"session":{"type":"realtime"}}""",
            )
        }
        val provider = BackendRealtimeCredentialProvider(
            requestFactory = {
                Request.Builder()
                    .url("https://agent.example.test/realtime/client-secret")
                    .header("Authorization", "Bearer app-session-token")
                    .post("{}".toRequestBody(JSON))
                    .build()
            },
            callFactory = client,
            nowEpochSeconds = { 1_000L },
        )

        val result = runSuspend { provider.fetchClientSecret() }

        assertTrue(result.isSuccess)
        val secret = result.getOrThrow()
        assertEquals("ek_backend_test", secret.value)
        assertEquals(2_000L, secret.expiresAtEpochSeconds)
        assertEquals("POST", seenRequest!!.method)
        assertEquals("Bearer app-session-token", seenRequest!!.header("Authorization"))
        assertFalse(secret.toString().contains("ek_backend_test"))
    }

    @Test
    fun cleartextOrNonPostBackendRequestIsRejectedBeforeNetwork() {
        var calls = 0
        val client = fakeClient { request ->
            calls++
            response(request, 500, "never")
        }

        val cleartext = BackendRealtimeCredentialProvider(
            requestFactory = {
                Request.Builder()
                    .url("http://agent.example.test/realtime/client-secret")
                    .post("{}".toRequestBody(JSON))
                    .build()
            },
            callFactory = client,
            nowEpochSeconds = { 1_000L },
        )
        val get = BackendRealtimeCredentialProvider(
            requestFactory = {
                Request.Builder()
                    .url("https://agent.example.test/realtime/client-secret")
                    .get()
                    .build()
            },
            callFactory = client,
            nowEpochSeconds = { 1_000L },
        )

        assertTrue(runSuspend { cleartext.fetchClientSecret() }.isFailure)
        assertTrue(runSuspend { get.fetchClientSecret() }.isFailure)
        assertEquals(0, calls)
    }

    @Test
    fun directOpenAiMintEndpointIsRejectedOnDevice() {
        var calls = 0
        val provider = BackendRealtimeCredentialProvider(
            requestFactory = {
                Request.Builder()
                    .url("https://api.openai.com/v1/realtime/client_secrets")
                    .post("{}".toRequestBody(JSON))
                    .build()
            },
            callFactory = fakeClient { request ->
                calls++
                response(request, 200, """{"value":"ek_bad","expires_at":2000}""")
            },
            nowEpochSeconds = { 1_000L },
        )

        val result = runSuspend { provider.fetchClientSecret() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("developer backend"))
        assertEquals(0, calls)
    }

    @Test
    fun nonSuccessAndMalformedResponsesFailWithoutEchoingResponseBody() {
        val serverSecret = "ek_must_not_leak"
        val nonSuccess = BackendRealtimeCredentialProvider(
            requestFactory = { backendPost() },
            callFactory = fakeClient { request ->
                response(request, 401, "{\"error\":\"$serverSecret\"}")
            },
            nowEpochSeconds = { 1_000L },
        )
        val malformed = BackendRealtimeCredentialProvider(
            requestFactory = { backendPost() },
            callFactory = fakeClient { request -> response(request, 200, "not-json-$serverSecret") },
            nowEpochSeconds = { 1_000L },
        )

        val httpFailure = runSuspend { nonSuccess.fetchClientSecret() }
        val parseFailure = runSuspend { malformed.fetchClientSecret() }

        assertTrue(httpFailure.isFailure)
        assertTrue(parseFailure.isFailure)
        assertFalse(httpFailure.exceptionOrNull().toString().contains(serverSecret))
        assertFalse(parseFailure.exceptionOrNull().toString().contains(serverSecret))
    }

    @Test
    fun expiredOrStructurallyInvalidSecretIsRejected() {
        val expired = BackendRealtimeCredentialProvider(
            requestFactory = { backendPost() },
            callFactory = fakeClient { request ->
                response(request, 200, """{"value":"ek_old","expires_at":1000}""")
            },
            nowEpochSeconds = { 1_000L },
        )
        val missingValue = BackendRealtimeCredentialProvider(
            requestFactory = { backendPost() },
            callFactory = fakeClient { request ->
                response(request, 200, """{"expires_at":2000}""")
            },
            nowEpochSeconds = { 1_000L },
        )

        assertTrue(runSuspend { expired.fetchClientSecret() }.isFailure)
        assertTrue(runSuspend { missingValue.fetchClientSecret() }.isFailure)
    }

    private fun backendPost(): Request =
        Request.Builder()
            .url("https://agent.example.test/realtime/client-secret")
            .post("{}".toRequestBody(JSON))
            .build()

    private fun fakeClient(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain -> handler(chain.request()) }
            .build()

    private fun response(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERROR")
            .body(body.toResponseBody(JSON))
            .build()

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return outcome?.getOrThrow() ?: throw IOException("suspend block did not complete synchronously")
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
