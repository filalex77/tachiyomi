package eu.kanade.tachiyomi.extension.online

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy

class FDroidParser {
    /**
     * Network service.
     */
    protected val network: NetworkHelper by injectLazy()

    /**
     * Preferences helper.
     */
    protected val preferences: PreferencesHelper by injectLazy()

    /**
     * Base url of the extensions repo
     */
    val baseUrl: String = "https://fdroid.j2ghz.com/repo/"

    /**
     * Headers used for requests.
     */
    val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient
        get() = network.client

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    open protected fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
    }

    fun findExtensions(): Observable<List<SExtension>> {
        return client.newCall(GET(baseUrl, headers)).asObservableSuccess()
                .map { response ->
                    extensionsParse(response)
                }
    }

    private fun extensionsParse(response: Response): List<SExtension> {
        val body = response.body()!!.string()
        val document = response.asJsoup(body)
        return document.select(extensionSelector()).map { extensionFromElement(it) }
    }

    private fun extensionSelector() = "a[href]:matches(tachiyomi-[A-Za-z]{2}\\..*)"

    private fun extensionFromElement(element: Element): SExtension {
        val urlElement = element.allElements.first()

        val extension = SExtension.create()
        extension.url = (baseUrl + "/" + urlElement.attr("href"))
        var name = urlElement.text()
        name = name.removePrefix("tachiyomi-")
        extension.lang = name.substringBefore(".")
        name = name.removePrefix(extension.lang + ".")
        extension.name = name.substringBefore("-")
        name = name.removeSuffix("-debug.apk")
        extension.version = name.substringAfter("-v")

        return extension
    }
}