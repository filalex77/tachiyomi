package eu.kanade.tachiyomi.extension.model

import eu.kanade.tachiyomi.source.Source

sealed class Extension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Int

    data class Installed(override val name: String,
                         override val pkgName: String,
                         override val versionName: String,
                         override val versionCode: Int,
                         val sources: List<Source>,
                         val hasUpdate: Boolean = false) : Extension()

    data class Available(override val name: String,
                         override val pkgName: String,
                         override val versionName: String,
                         override val versionCode: Int,
                         val apkName: String) : Extension()
}
