package com.dmitrylab.homenode.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING
}

@Serializable
data class TrafficResponse(val up: Long, val down: Long)

object NodeServerController {
    private val mutex = Mutex()
    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun start(port: Int = 8080, host: String = "0.0.0.0") = mutex.withLock {
        if (engine != null || _status.value == ServerStatus.RUNNING || _status.value == ServerStatus.STARTING) {
            return@withLock
        }

        _status.value = ServerStatus.STARTING
        _lastError.value = null

        try {
            val createdEngine = withContext(Dispatchers.IO) {
                embeddedServer(Netty, port = port, host = host) {
                    install(ContentNegotiation) {
                        json()
                    }
                    routing {
                        get("/") {
                            call.respondText("HomeNode API is running!")
                        }
                        get("/api/tt/stats") {
                            call.respond(TrafficResponse(up = 1024 * 1024, down = 5 * 1024 * 1024))
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