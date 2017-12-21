package eu.kanade.tachiyomi.extension

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.extension.online.ExtensionParser
import eu.kanade.tachiyomi.extension.online.FDroidParser
import eu.kanade.tachiyomi.source.Source
import rx.Observable
import timber.log.Timber

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

    open fun clearCache() {
        extensionMap.clear()
    }

    open fun get(extension: SExtension): SExtension? {
        val key = extension.lang + extension.name
        return get(key)
    }

    fun getExtensions(): Observable<List<SExtension>> {
        if (getExtensionCache().isEmpty()) {
            extensionsInstalled.clear()
            populateInstalledExtensions()

            //If more sources are added combine find extensions calls.  Then go into the flatMapIterable
            val foundExtensions = fDroidParser.findExtensions()
            val preMergedObs = foundExtensions.flatMapIterable { it -> it }.filter { it -> registerExtension(it) }.toList().flatMapIterable { it -> it }.filter { it -> filterOutdated(it) }.doOnNext { it -> updateExtensionWithInstalled(it) }.toList()
            //add any extension that is not found on server that was side loaded
            return preMergedObs.doOnNext { it -> addInstalledExtension(it) }
        }
        extensionsInstalled.clear()
        populateInstalledExtensions()
        return Observable.just(getExtensionCache())
    }

    private fun addInstalledExtension(it: MutableList<SExtension>) {
        val values = extensionsInstalled.values
        it.forEach {
            Timber.d("extensions %s %s", it.name, it.version)
        }
        for (sExtension in values) {
            Timber.d("extension compare , %s %s", sExtension.name, it.contains(sExtension))
            if (!it.contains(sExtension)) {
                Timber.d("extension not on server , %s %s", sExtension.name, sExtension.version)
                sExtension.upToDate = true
                it.add(sExtension)
            }
        }

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
            extension.version = installedExt.version + " -> " + extension.version
            extension.packageName = installedExt.packageName
            extension.source = installedExt.source
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
            val appInfo = pkgManager.getApplicationInfo(pkgInfo.packageName,
                    PackageManager.GET_META_DATA) ?: continue

            val extension = SExtension.create()
            extension.name = pkgInfo.packageName.substringAfterLast(".")
            extension.version = pkgInfo.versionName
            extension.packageName = pkgInfo.packageName
            extension.lang = pkgInfo.packageName.substringAfter("eu.kanade.tachiyomi.extension.").substringBefore(".")
            extension.installed = true

            val trim = appInfo.metaData.getString(ExtensionManager.METADATA_SOURCE_CLASS)
                    .split(";").first().trim()

            val sourceClass = when {
                trim.startsWith(".") -> pkgInfo.packageName + trim
                else -> trim
            }

            val classLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
            val obj = Class.forName(sourceClass, false, classLoader).newInstance()
            val source = when (obj) {
                is Source -> obj
                else -> throw Exception("Unknown source class type!")
            }
            extension.source = source.id

            extensionsInstalled.put(extension.name, extension)
        }

    }

    private companion object {
        const val EXTENSION_FEATURE = "tachiyomi.extension"
        const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"

    }
}
