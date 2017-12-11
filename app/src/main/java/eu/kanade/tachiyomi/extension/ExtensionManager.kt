package eu.kanade.tachiyomi.extension

import android.content.Context
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.extension.online.ExtensionParser
import eu.kanade.tachiyomi.extension.online.FDroidParser
import rx.Observable

/**Manages Extensions installed as well as extensions from Repos
 * Created on 11/30/2017.
 */
open class ExtensionManager(
        private val context: Context,
        private val fDroidParser: ExtensionParser = FDroidParser()
) {
    private val extensionMap = mutableMapOf<String, SExtension>()
    private val extensionsInstalled = mutableMapOf<String, SExtension>()

    private fun getExtensionCache(): List<SExtension> {
        return extensionMap.values.toList()
    }


    open fun get(sourceKey: String): SExtension? {
        return extensionMap[sourceKey]
    }

    open fun get(extension: SExtension): SExtension? {
        val key = extension.lang + extension.name
        return get(key)
    }

    fun getExtensions(): Observable<List<SExtension>> {
        if (getExtensionCache().isEmpty()) {
            extensionsInstalled.clear()
            populateInstalledExtensions()

            //If more sources are added combine find extensions calls.  Then go into the flatMap
            val foundExtensions = fDroidParser.findExtensions()

            return foundExtensions.flatMapIterable { it -> it }.filter { it -> registerExtension(it) }.toList().flatMapIterable { it -> it }.filter { it -> filterOutdated(it) }.doOnNext { it -> updateExtensionWithInstalled(it) }.toList()
        }
        extensionsInstalled.clear()
        populateInstalledExtensions()
        return Observable.just(getExtensionCache())
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
            extension.packageName = installedExt.packageName
        } else {
            extension.installed = false
        }

    }


    private fun populateInstalledExtensions() {
        val pkgManager = context.packageManager
        val flags = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_SIGNATURES
        val installedPkgs = pkgManager.getInstalledPackages(flags)
        val extPkgs = installedPkgs.filter { it.reqFeatures.orEmpty().any { it.name == ExtensionManager.EXTENSION_FEATURE } }

        for (pkgInfo in extPkgs) {
            pkgManager.getApplicationInfo(pkgInfo.packageName,
                    PackageManager.GET_META_DATA) ?: continue

            val extension = SExtension.create()
            extension.name = pkgInfo.packageName.substringAfterLast(".")
            extension.version = pkgInfo.versionName
            extension.packageName = pkgInfo.packageName
            extensionsInstalled.put(extension.name, extension)
        }

    }

    private companion object {
        const val EXTENSION_FEATURE = "tachiyomi.extension"
    }

}