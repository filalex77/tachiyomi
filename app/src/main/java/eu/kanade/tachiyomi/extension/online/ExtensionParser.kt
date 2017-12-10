package eu.kanade.tachiyomi.extension.online

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Headers
import okhttp3.OkHttpClient
import rx.Observable
import uy.kohesive.injekt.injectLazy

/** Base Extension repo parser
 *  12/10/2017.
 */
abstract class ExtensionParser {
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
    abstract val baseUrl: String

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

    abstract fun findExtensions(): Observable<List<SExtension>>
}