package dev.monosoul.cookiemover

import dev.monosoul.cookiemover.ExtensionIOHandler.JSON
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.hildan.chrome.devtools.domains.page.events.PageEvent
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import org.hildan.chrome.devtools.sessions.asPageSession
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

class ChromeRunner(
    private val execPath: File,
    private val url: URI,
    private val defaultDataDirPath: File,
    private val appDataDirPath: File,
    private val clock: Clock,
) {
    private val cdpPort by lazy {
        ServerSocket(0).use { it.localPort }
    }
    private var process: Process? = null
    private val cdpClient by lazy {
        requireNotNull(process) { "Chrome has not been started yet" }
        ChromeDPClient("http://localhost:$cdpPort", httpClient = HttpClient(Java) {
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(100)
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 30.seconds.inWholeMilliseconds
                connectTimeoutMillis = 30.seconds.inWholeMilliseconds
                requestTimeoutMillis = 30.seconds.inWholeMilliseconds
            }
            install(ContentNegotiation) { json(JSON) }
            install(WebSockets)
        })
    }
    private val backgroundScope = CoroutineScope(Dispatchers.Default + Job())
    private val targetUrl = AtomicReference(url)

    private fun start() {
        if (!appDataDirPath.exists()) {
            FileUtils.copyDirectory(defaultDataDirPath, appDataDirPath) {
                !it.name.contains("Socket")
            }
        }

        process = ProcessBuilder(
            execPath.absolutePath,
            "--user-data-dir=${appDataDirPath.absolutePath}",
            "--disable-features=InfiniteSessionRestore",
            "--hide-crash-restore-bubble",
            "--kiosk",
            "--remote-debugging-port=$cdpPort",
            "$url"
        ).start()
    }

    private fun stop() {
        process?.destroy()
    }

    private fun cleanUp() {
        val sessions = File(appDataDirPath, "Default/Sessions")
        if (sessions.exists()) {
            sessions.deleteRecursively()
            sessions.mkdirs()
        }
    }

    private fun waitForAuthentication() = backgroundScope.launch {
        val session = cdpClient.webSocket()
        val target = session.target.getTargets().targetInfos.single {
            it.type == "page" && URI(it.url).hostContains(url.host)
        }

        val pageSession = session.attachToTarget(target.targetId).asPageSession()
        val page = pageSession.page

        val authenticated: AtomicBoolean = AtomicBoolean(false)
        val lastEvent: AtomicReference<EventAndTimestamp?> = AtomicReference(null)
        val windowIsOpen = AtomicBoolean(true)

        session.target.setDiscoverTargets(true)
        session.target.targetDestroyedEvents().onEach {
            // this will make sure closing the window will also close the process itself
            if (it.targetId == target.targetId) {
                windowIsOpen.set(false)
                session.close()
                stop()
            }
        }.launchIn(backgroundScope)

        page.enable()
        page.events().onEach {
            // listen to page events
            when (it) {
                is PageEvent.FrameNavigated -> {
                    // when navigate to octa, then probably authentication happened
                    if (URI(it.frame.url).hostContains("okta.com")) {
                        authenticated.set(true)
                    }
                }

                else -> Unit
            }
            // save the last event received
            lastEvent.set(EventAndTimestamp(it, clock.instant()))
        }.launchIn(backgroundScope)

        backgroundScope.launch {
            try {
                while (windowIsOpen.get()) {
                    /**
                     * While the Chrome window is open, check if the last page event received is final
                     * (i.e. the page was rendered and navigation is done), if authentication happened and if there
                     * were no events for the past 600 ms.
                     * If that condition is satisfied, then probably authentication is complete and it's safe to close
                     * the Chrome instance.
                     * Also saves the last URL the page was at before closing as targetUrl, so that the client browser
                     * can be redirected there after importing cookies.
                     */
                    delay(200)
                    val lastEventValue: EventAndTimestamp = lastEvent.get() ?: continue
                    val currentTime = clock.instant()
                    val timeSinceTheLastEvent = Duration.between(lastEventValue.timestamp, currentTime).toMillis()
                    val navHistory = page.getNavigationHistory()
                    val currentUrl = URI(navHistory.entries[navHistory.currentIndex].url)
                    targetUrl.set(currentUrl)
                    if (
                        authenticated.get()
                        && timeSinceTheLastEvent > 600
                        && lastEventValue.isFinal()
                        && currentUrl.hostContains(url.host)
                    ) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (windowIsOpen.get()) {
                    /**
                     * if caught an exception while the window is still open, then something went wrong
                     * otherwise it's fine
                     */
                    throw e
                }
            }
            session.close()
            stop()
        }
    }

    fun runChrome(): URI {
        start()

        waitForAuthentication()
        process!!.waitFor()
        backgroundScope.cancel()

        cleanUp()
        return targetUrl.get()
    }
}

private data class EventAndTimestamp(val event: PageEvent, val timestamp: Instant)

@OptIn(ExperimentalChromeApi::class)
private fun EventAndTimestamp?.isFinal() = when (this?.event) {
    is PageEvent.DocumentOpened -> true
    is PageEvent.FrameStoppedLoading -> true
    else -> false
}

private fun URI.hostContains(host: String): Boolean = (this.host ?: "").contains(host)