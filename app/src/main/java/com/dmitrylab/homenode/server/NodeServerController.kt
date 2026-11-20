package com.dmitrylab.homenode.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val DEFAULT_LINUX_NODE_URL = "http://192.168.43.218:8090"

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING
}

object NodeServerController {
    private val mutex = Mutex()
    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val client = HttpClient(CIO)

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastLinuxResponse = MutableStateFlow<String?>(null)
    val lastLinuxResponse: StateFlow<String?> = _lastLinuxResponse.asStateFlow()

    suspend fun pingLinuxHousehold(linuxBaseUrl: String = DEFAULT_LINUX_NODE_URL): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val url = "${linuxBaseUrl.trimEnd('/')}/api/v1/projects/household/ping"
            val response: HttpResponse = client.get(url)
            val body = response.bodyAsText()
            _lastLinuxResponse.value = body
            response.status.value to body
        }

    suspend fun start(
        port: Int = 8080,
        host: String = "0.0.0.0",
        linuxBaseUrl: String = DEFAULT_LINUX_NODE_URL
    ) = mutex.withLock {
        if (engine != null || _status.value == ServerStatus.RUNNING || _status.value == ServerStatus.STARTING) {
            return@withLock
        }

        _status.value = ServerStatus.STARTING
        _lastError.value = null

        try {
            val createdEngine = withContext(Dispatchers.IO) {
                embeddedServer(Netty, port = port, host = host) {
                    routing {
                        get("/") {
                            call.respondText("HomeNode API is running!")
                        }
                        get("/api/tt/stats") {
                            call.respondText(
                                text = """{"up":1048576,"down":5242880}""",
                                contentType = ContentType.Application.Json
                            )
                        }
                        get("/api/linux/household/ping") {
                            try {
                                val (code, body) = pingLinuxHousehold(linuxBaseUrl)
                                _lastError.value = null
                                call.respondText(
                                    text = body,
                                    contentType = ContentType.Application.Json,
                                    status = HttpStatusCode.fromValue(code)
                                )
                            } catch (e: Exception) {
                                val errMsg = (e.message ?: e::class.simpleName ?: "Linux request failed")
                                    .replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                _lastError.value = errMsg
                                call.respondText(
                                    text = """{"ok":false,"data":null,"error":{"code":"LINUX_UNREACHABLE","message":"$errMsg"}}""",
                                    contentType = ContentType.Application.Json,
                                    status = HttpStatusCode.BadGateway
                                )
                            }
                        }
                    }
                }.apply {
                    start(wait = false)
                }
            }
            engine = createdEngine
            _status.value = ServerStatus.RUNNING
        } catch (e: Exception) {
            engine = null
            _lastError.value = e.message ?: e::class.simpleName
            _status.value = ServerStatus.STOPPED
        }
    }

    suspend fun stop() = mutex.withLock {
        val currentEngine = engine ?: run {
            _status.value = ServerStatus.STOPPED
            return@withLock
        }

        _status.value = ServerStatus.STOPPING
        try {
            withContext(Dispatchers.IO) {
                currentEngine.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
            }
        } catch (e: Exception) {
            _lastError.value = e.message ?: e::class.simpleName
        } finally {
            engine = null
            _status.value = ServerStatus.STOPPED
        }
    }
}