package eu.kanade.tachiyomi.extension.api

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class FDroidApi {

    private val network: NetworkHelper by injectLazy()

    private val client = network.client

    private val baseUrl = "https://fdroid.j2ghz.com/repo"

    fun findExtensions(): Observable<List<Extension.Available>> {
        val call = GET("$baseUrl/index.xml")

        return client.newCall(call).asObservableSuccess()
                .map(::parseResponse)
    }

    private fun parseResponse(response: Response): List<Extension.Available> {
        val document = response.asJsoup()

        return document.select("application[id^=eu.kanade.tachiyomi.extension").map { element ->
            val name = element.select("name").text().substringAfter("Tachiyomi: ")
            val pkgName = element.select("id").text()
            val recentPackage = element.select("package").first()
            val versionName = recentPackage.select("version").text()
            val versionCode = recentPackage.select("versioncode").text().toInt()
            val apkName = recentPackage.select("apkname").text()

            Extension.Available(name, pkgName, versionName, versionCode, apkName)
        }
    }

    fun downloadExtension(extension: Extension.Available): Observable<Response> {
        val call = GET("$baseUrl/${extension.apkName}")

        return client.newCall(call).asObservableSuccess()
    }
}
