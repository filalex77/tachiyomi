package eu.kanade.tachiyomi.extension

import android.content.Context
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.extension.api.FDroidApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.launchNow
import kotlinx.coroutines.experimental.async
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get


class ExtensionManager(
        private val context: Context,
        private val preferences: PreferencesHelper = Injekt.get()
) {

    private val api = FDroidApi()

    private val installer by lazy { ExtensionInstaller(context) }

    private val installationListener = InstallationListener()

    private val installedExtensionsRelay = BehaviorRelay.create<List<Extension.Installed>>()
    var installedExtensions = emptyList<Extension.Installed>()
        private set(value) {
            field = value
            installedExtensionsRelay.call(value)
        }

    private val availableExtensionsRelay = BehaviorRelay.create<List<Extension.Available>>()
    var availableExtensions = emptyList<Extension.Available>()
        private set(value) {
            field = value
            availableExtensionsRelay.call(value)
            setUpdateFieldOfInstalledExtensions(value)
        }

    private val untrustedExtensionsRelay = BehaviorRelay.create<List<Extension.Untrusted>>()
    var untrustedExtensions = emptyList<Extension.Untrusted>()
        private set(value) {
            field = value
            untrustedExtensionsRelay.call(value)
        }

    private lateinit var sourceManager: SourceManager

    fun init(sourceManager: SourceManager) {
        this.sourceManager = sourceManager
        initExtensions()
        ExtensionInstallReceiver(installationListener).register(context)
    }

    fun getInstalledExtensionsObservable(): Observable<List<Extension.Installed>> {
        return installedExtensionsRelay
    }

    fun getAvailableExtensionsObservable(): Observable<List<Extension.Available>> {
        if (!availableExtensionsRelay.hasValue()) {
            findAvailableExtensions()
        }
        return availableExtensionsRelay
    }

    fun getUntrustedExtensionsObservable(): Observable<List<Extension.Untrusted>> {
        return untrustedExtensionsRelay
    }

    private fun setUpdateFieldOfInstalledExtensions(availableExtensions: List<Extension.Available>) {
        val mutInstalledExtensions = installedExtensions.toMutableList()
        var changed = false

        for ((index, installedExt) in mutInstalledExtensions.withIndex()) {
            val pkgName = installedExt.pkgName
            val availableExt = availableExtensions.find { it.pkgName == pkgName } ?: continue

            val hasUpdate = availableExt.versionCode > installedExt.versionCode
            if (installedExt.hasUpdate != hasUpdate) {
                mutInstalledExtensions[index] = installedExt.copy(hasUpdate = hasUpdate)
                changed = true
            }
        }
        if (changed) {
            installedExtensions = mutInstalledExtensions
        }
    }

    private fun initExtensions() {
        val extensions = ExtensionLoader.loadExtensions(context)

        installedExtensions = extensions
                .filterIsInstance<LoadResult.Success>()
                .map { it.extension }
        installedExtensions.flatMap { it.sources }.forEach { sourceManager.registerSource(it) }

        untrustedExtensions = extensions
                .filterIsInstance<LoadResult.Untrusted>()
                .map { it.extension }
    }

    fun findAvailableExtensions() {
        api.findExtensions()
                .onErrorReturn { emptyList() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { availableExtensions = it }
    }

    fun installExtension(extension: Extension.Available): Observable<InstallStep> {
        return installer.downloadAndInstall(api.getApkUrl(extension), extension)
    }

    fun updateExtension(extension: Extension.Installed): Observable<InstallStep>  {
        val availableExt = availableExtensions.find { it.pkgName == extension.pkgName }
                ?: return Observable.empty()
        return installExtension(availableExt)
    }

    fun uninstallExtension(pkgName: String) {
        installer.uninstallApk(pkgName)
    }

    fun trustSignature(signature: String) {
        val untrustedSignatures = untrustedExtensions.map { it.signatureHash }.toSet()
        if (signature !in untrustedSignatures) return

        ExtensionLoader.trustedSignatures += signature
        val preference = preferences.trustedSignatures()
        preference.set(preference.getOrDefault() + signature)

        val nowTrustedExtensions = untrustedExtensions.filter { it.signatureHash == signature }
        untrustedExtensions -= nowTrustedExtensions

        val ctx = context
        launchNow {
            nowTrustedExtensions
                    .map { extension ->
                        async { ExtensionLoader.loadExtensionFromPkgName(ctx, extension.pkgName) }
                    }
                    .map { it.await() }
                    .forEach { result ->
                        if (result is LoadResult.Success) {
                            registerNewExtension(result.extension)
                        }
                    }
        }
    }

    private fun registerNewExtension(extension: Extension.Installed) {
        installedExtensions += extension
        extension.sources.forEach { sourceManager.registerSource(it) }
    }

    private fun registerUpdatedExtension(extension: Extension.Installed) {
        val mutInstalledExtensions = installedExtensions.toMutableList()
        val oldExtension = mutInstalledExtensions.find { it.pkgName == extension.pkgName }
        if (oldExtension != null) {
            mutInstalledExtensions -= oldExtension
            extension.sources.forEach { sourceManager.unregisterSource(it) }
        }
        mutInstalledExtensions += extension
        installedExtensions = mutInstalledExtensions
        extension.sources.forEach { sourceManager.registerSource(it) }
    }

    private fun unregisterExtension(pkgName: String) {
        val installedExtension = installedExtensions.find { it.pkgName == pkgName }
        if (installedExtension != null) {
            installedExtensions -= installedExtension
            installedExtension.sources.forEach { sourceManager.unregisterSource(it) }
        }
        val untrustedExtension = untrustedExtensions.find { it.pkgName == pkgName }
        if (untrustedExtension != null) {
            untrustedExtensions -= untrustedExtension
        }
    }

    private inner class InstallationListener : ExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: Extension.Installed) {
            registerNewExtension(extension.withUpdateCheck())
            installer.onApkInstalled(extension.pkgName)
        }

        override fun onExtensionUpdated(extension: Extension.Installed) {
            registerUpdatedExtension(extension.withUpdateCheck())
            installer.onApkInstalled(extension.pkgName)
        }

        override fun onExtensionUntrusted(extension: Extension.Untrusted) {
            untrustedExtensions += extension
            installer.onApkInstalled(extension.pkgName)
        }

        override fun onExtensionUninstalled(pkgName: String) {
            unregisterExtension(pkgName)
        }
    }

    private fun Extension.Installed.withUpdateCheck(): Extension.Installed {
        val availableExt = availableExtensions.find { it.pkgName == pkgName }
        if (availableExt != null && availableExt.versionCode > versionCode) {
            return copy(hasUpdate = true)
        }
        return this
    }

}