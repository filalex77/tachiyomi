package eu.kanade.tachiyomi.ui.catalogue.extension

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [ExtensionController].
 */
open class ExtensionPresenter(
        private val extensionManager: ExtensionManager = Injekt.get(),
        private val db: DatabaseHelper = Injekt.get(),
        private val prefs: PreferencesHelper = Injekt.get()
) : BasePresenter<ExtensionController>() {

    /**
     * Query from the view.
     */
    var query = ""
        private set
    /**
     * Subscription to initialize manga details.
     */
    private var repoSubscription: Subscription? = null

    private val extensionsSubject = PublishSubject.create<List<SExtension>>()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        if (savedState != null) {
            query = savedState.getString(ExtensionPresenter::query.name, "")
        }
        subscribeToRepo()
    }

    override fun onSave(state: Bundle) {
        state.putString(ExtensionPresenter::query.name, query)
        super.onSave(state)
    }

    /**
     * Subscribes to repo
     * */

    private fun subscribeToRepo() {
        repoSubscription?.let { remove(it) }
        repoSubscription = extensionManager.getExtensions().observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io()).subscribeReplay(
                { view, ext ->
                    view?.onAdd(ext)
                },
                { _, error ->
                    Timber.e(error)
                    view?.onError(error)
                }
        )
    }

    fun requestAgain() {
        subscribeToRepo()
    }


}