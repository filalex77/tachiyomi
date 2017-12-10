package eu.kanade.tachiyomi.extension.online

import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class FDroidParser: ExtensionParser() {

    /**
     * Base url of the extensions repo
     */
    override val baseUrl = "https://fdroid.j2ghz.com/repo/"


    override fun findExtensions(): Observable<List<SExtension>> {
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