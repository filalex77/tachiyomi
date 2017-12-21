package eu.kanade.tachiyomi.extension.model


interface SExtension {

    var url: String

    var name: String

    var lang: String

    var packageName: String

    var version: String

    var upToDate: Boolean

    var installed: Boolean

    var source: Long


    companion object {
        fun create(): SExtension {
            return SExtensionImpl()
        }
    }
}