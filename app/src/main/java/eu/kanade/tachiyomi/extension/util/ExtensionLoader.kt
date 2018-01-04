package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import timber.log.Timber

internal object ExtensionLoader {

    const val EXTENSION_FEATURE = "tachiyomi.extension"
    const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    const val LIB_VERSION_MIN = 1
    const val LIB_VERSION_MAX = 1

    fun loadExtensions(context: Context): List<Extension.Installed> {
        val pkgManager = context.packageManager
        val installedPkgs = pkgManager.getInstalledPackages(PackageManager.GET_CONFIGURATIONS)
        val extPkgs = installedPkgs.filter { isPackageAnExtension(it) }

        if (extPkgs.isEmpty()) return emptyList()

        // Load each extension concurrently and wait for completion
        return runBlocking {
            val deferred = extPkgs.map {
                async { loadExtension(context, it.packageName, it) }
            }
            deferred.mapNotNull { it.await() }
        }
    }

    fun loadExtensionFromPkgName(context: Context, pkgName: String): Extension.Installed? {
        val pkgInfo = context.packageManager.getPackageInfo(pkgName, PackageManager.GET_CONFIGURATIONS)
        if (!isPackageAnExtension(pkgInfo)) {
            return null
        }
        return loadExtension(context, pkgName, pkgInfo)
    }

    private fun loadExtension(context: Context, pkgName: String, optPkgInfo: PackageInfo? = null): Extension.Installed? {
        val pkgManager = context.packageManager

        val pkgInfo = optPkgInfo ?: pkgManager.getPackageInfo(pkgName, PackageManager.GET_CONFIGURATIONS)
        val appInfo = pkgManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)

        val extName = pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = pkgInfo.versionCode

        // Validate lib version
        val majorLibVersion = versionName.substringBefore('.').toInt()
        if (majorLibVersion < LIB_VERSION_MIN || majorLibVersion > LIB_VERSION_MAX) {
            Timber.w("Lib version is %d, while only versions %d to %d are allowed",
                    majorLibVersion, LIB_VERSION_MIN, LIB_VERSION_MAX)
            return null
        }

        val classLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)

        val sources = appInfo.metaData.getString(METADATA_SOURCE_CLASS)
                .split(";")
                .map {
                    val sourceClass = it.trim()
                    if (sourceClass.startsWith("."))
                        pkgInfo.packageName + sourceClass
                    else
                        sourceClass
                }
                .flatMap {
                    try {
                        val obj = Class.forName(it, false, classLoader).newInstance()
                        when (obj) {
                            is Source -> listOf(obj)
                            is SourceFactory -> obj.createSources()
                            else -> throw Exception("Unknown source class type! ${obj.javaClass}")
                        }
                    } catch (e: Throwable) {
                        Timber.e(e, "Extension load error: $extName.")
                        return null
                    }
                }
        val langs = sources.filterIsInstance<CatalogueSource>()
                .map { it.lang }
                .toSet()

        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        return Extension.Installed(extName, pkgName, versionName, versionCode, sources, lang)
    }

    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

}