package eu.kanade.tachiyomi.extension

import android.content.Context
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.extension.online.FDroidParser
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.catalogue.extension.ExtensionItem
import rx.Observable

/**
 * Created by Carlos on 11/30/2017.
 */
open class ExtensionManager(
        private val context: Context,
        private val fDroidParser: FDroidParser = FDroidParser()
) {
    private val extensionMap = mutableMapOf<String, SExtension>()
    private val extensionsInstalled = mutableMapOf<String, SExtension>()

    init {

    }

    fun getExtensionCache(): MutableCollection<SExtension> {
        return extensionMap.values
    }


    open fun get(sourceKey: String): SExtension? {
        return extensionMap[sourceKey]
    }

    open fun get(extension: SExtension): SExtension? {
        val key = extension.lang + extension.name
        return get(key)
    }

    fun getExtensions(): Observable<List<ExtensionItem>> {
        if (getExtensionCache().isEmpty()) {
            extensionsInstalled.clear()
            populateInstalledExtensions()
            return fDroidParser.findExtensions().flatMapIterable { it -> it }.filter { it -> registerExtension(it) }.toList().flatMapIterable { it -> it }.filter { it -> filterOutdated(it) }.doOnNext { it -> updateExtensionWithInstalled(it) }.map { it -> ExtensionItem(it) }.toList()
        }
        extensionsInstalled.clear()
        populateInstalledExtensions()
        return Observable.just(getExtensionCache().map { it -> ExtensionItem(it) }.toList())
    }

    private fun registerExtension(extension: SExtension): Boolean {
        val key = extension.lang + extension.name
        val sExtension = extensionMap[key]
        if (sExtension != null) {
            return when (compareVersionIsNewer(sExtension.version, extension.version)) {
                1 -> {
                    extensionMap.remove(key)
                    extensionMap.put(key, extension)
                    true
                }
                else -> {
                    false
                }
            }
        } else {
            extensionMap.put(key, extension)
            return true
        }
    }

    private fun filterOutdated(extension: SExtension): Boolean {
        val key = extension.lang + extension.name
        return when (compareVersionIsNewer(extensionMap[key]!!.version, extension.version)) {
            1 -> false

            -1 -> false

            else -> true

        }
    }

    private fun compareVersionIsNewer(existingVersion: String, potentialVersion: String): Int {
        val oldParts = existingVersion.split(".")
        val newParts = potentialVersion.split(".")
        val length = Math.max(oldParts.size, newParts.size)
        for (i in 0..length) {
            val oldPart = when {
                i < oldParts.size -> Integer.parseInt(oldParts[i])
                else -> 0
            }
            val newParts = when {
                i < newParts.size -> Integer.parseInt(newParts[i])
                else -> 0
            }

            if (oldPart < newParts) {
                return 1
            }
            if (oldPart > newParts) {
                return -1
            }
        }
        return 0
    }

    private fun updateExtensionWithInstalled(extension: SExtension) {
        val installedExt = extensionsInstalled[extension.name]
        if (installedExt != null) {
            extension.installed = true
            extension.upToDate = extension.version == installedExt.version
        } else {
            extension.installed = false
        }

    }


    fun populateInstalledExtensions() {
        val pkgManager = context.packageManager
        val flags = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_SIGNATURES
        val installedPkgs = pkgManager.getInstalledPackages(flags)
        val extPkgs = installedPkgs.filter { it.reqFeatures.orEmpty().any { it.name == ExtensionManager.EXTENSION_FEATURE } }

        val sources = mutableListOf<Source>()
        for (pkgInfo in extPkgs) {
            val appInfo = pkgManager.getApplicationInfo(pkgInfo.packageName,
                    PackageManager.GET_META_DATA) ?: continue

            /*val extName = pkgManager.getApplicationLabel(appInfo).toString()
                    .substringAfter("Tachiyomi: ")*/
            val version = pkgInfo.versionName
            val extName = pkgInfo.packageName.substringAfterLast(".")

            val extension = SExtension.create()
            extension.name = extName
            extension.version = version
            extensionsInstalled.put(extName, extension)
        }

    }

    private companion object {
        const val EXTENSION_FEATURE = "tachiyomi.extension"
        const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    }

}