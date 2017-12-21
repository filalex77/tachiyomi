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

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is SExtension -> name == other.name
            else -> false
        }
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

}