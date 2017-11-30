package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.extension.online.FDroidParser
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * Created by Carlos on 11/30/2017.
 */
open class ExtensionManager(
        private val fDroidParser: FDroidParser = FDroidParser()
) {
    private val extensionMap = mutableMapOf<String, SExtension>()


    init {
        createExtensions()
    }

    fun getExtensions() = extensionMap.values

    open fun get(sourceKey: String): SExtension? {
        return extensionMap[sourceKey]
    }


    private fun createExtensions() {
        fDroidParser.findExtensions()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapIterable { it -> it }.forEach {
            registerExtension(it)
        }
    }

    private fun registerExtension(extension: SExtension, overwrite: Boolean = false) {
        val key = extension.lang + extension.name + extension.version
        if (overwrite || !extensionMap.containsKey(key)) {
            extensionMap.put(key, extension)
        }
    }

}