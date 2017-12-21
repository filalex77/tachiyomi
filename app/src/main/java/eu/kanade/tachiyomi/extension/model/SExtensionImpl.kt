package eu.kanade.tachiyomi.extension.model


class SExtensionImpl : SExtension {

    override lateinit var url: String

    override lateinit var name: String

    override lateinit var lang: String

    override lateinit var version: String

    override lateinit var packageName: String

    override var source: Long = 0L

    override var upToDate: Boolean = false

    override var installed: Boolean = false

    override fun compare(o1: SExtension?, o2: SExtension?): Int {
       return o1!!.name.compareTo(o2!!.name)
    }

}