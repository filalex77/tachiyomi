package eu.kanade.tachiyomi.ui.catalogue.extension

import android.os.Bundle
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [ExtensionController].
 */
open class ExtensionPresenter(
        private val extensionManager: ExtensionManager = Injekt.get()
) : BasePresenter<ExtensionController>() {

    private var repoSubscription: Subscription? = null


    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        updateExtensions()
    }


    fun updateExtensions() {
        repoSubscription?.unsubscribe()
        val getExt = extensionManager.getExtensions().flatMapIterable { it -> it }
        val extItems = getExt.groupBy { it -> it.lang }.flatMap { it -> it.toList() }.map { it -> getExtensionItems(it) }
        repoSubscription = extItems.toList().observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io()).subscribeLatestCache(
                { view, ext ->
                    Timber.d("set Extension")
                    val flatten = ext.flatten().sortedWith(compareBy({ it.extension.lang }, { !it.extension.upToDate }, { !it.extension.installed }, { it.extension.name }))
                    view?.setExtensions(flatten)
                },
                { _, error ->
                    Timber.e(error)
                    view?.onError(error)
                })

    }

    private fun getExtensionItems(ext: MutableList<SExtension>): List<ExtensionItem> {
        val langItem = LangExtItem(ext[0].lang)
        var extItems: MutableList<ExtensionItem> = arrayListOf()
        ext.forEach { ex ->
            extItems.add(ExtensionItem(ex, langItem))
        }
        return extItems
    }


}