package eu.kanade.tachiyomi.ui.catalogue.extension

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.UpdaterService
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsExtensionDetailController
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.snack
import eu.kanade.tachiyomi.util.visible
import kotlinx.android.synthetic.main.extension_controller.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit


/**
 * Controller to manage the catalogues available in the app.
 */
open class ExtensionController :
        NucleusController<ExtensionPresenter>(), ExtensionAdapter.OnButtonClickListener, ExtensionDownloadDialog.Listener, ExtensionRestartDialog.Listener {

    /**
     * Adapter containing the list of manga from the catalogue.
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return applicationContext?.getString(R.string.pref_category_extensions)
    }

    override fun createPresenter(): ExtensionPresenter {
        return ExtensionPresenter()
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.extension_controller, container, false)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Initialize adapter, scroll listener and recycler views
        adapter = ExtensionAdapter(this)
        // Create recycler and set adapter.
        ext_recycler.layoutManager = LinearLayoutManager(view.context)
        ext_recycler.adapter = adapter
        ext_recycler.addItemDecoration(ExtensionDividerItemDecoration(view.context))
        ext_progress.visible()
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
        snack = null
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    fun onError(error: Throwable) {
        Timber.e(error)
        val adapter = adapter ?: return
        adapter.onLoadMoreComplete(null)
        hideProgressBar()

        val message = view!!.context.getString(R.string.error_with_repository)

        snack?.dismiss()
        snack = ext_recycler.snack(message, Snackbar.LENGTH_INDEFINITE) {
            setAction(R.string.action_retry) {
                showProgressBar()
                presenter.updateExtensions(true)
            }
        }
    }


    /**
     * Shows the progress bar.
     */
    private fun showProgressBar() {
        ext_progress?.visible()
        snack?.dismiss()
        snack = null
    }


    /**
     * Hides active progress bars.
     */
    private fun hideProgressBar() {
        ext_progress?.gone()
    }

    override fun onButtonClick(position: Int) {
        val item = adapter!!.getItem(position) as? ExtensionItem
        if (item!!.extension.upToDate) {
            //launch details page
            router.pushController(SettingsExtensionDetailController(item!!.extension).withFadeTransaction())

        }else{
            val dialog = ExtensionDownloadDialog(this, item.extension)
            dialog.showDialog(router)
        }

    }



        /**
     * Called to update adapter containing sources.
     */
    fun setExtensions(extensions: List<IFlexible<*>>) {
        hideProgressBar()
        adapter?.updateDataSet(extensions)

    }

    override fun downloadExtension(ext: SExtension) {
        val downloadExtObservable = Observable.fromCallable { UpdaterService.downloadExtension(applicationContext!!, ext.url, ext.name + " " + ext.version) }
        processObserverWithRestart(downloadExtObservable, 5L)
    }

    override fun restart() {
        var launchIntent = Intent(this.applicationContext!!, MainActivity::class.java)
        var intent = PendingIntent.getActivity(applicationContext, 0, launchIntent, 0)
        var manager = this.applicationContext!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, intent)
        Process.killProcess(Process.myPid())
    }

    private fun getDelayObserver(delayTime: Long): Observable<Unit> {
        return Observable.empty<Unit>().delay(delayTime, TimeUnit.SECONDS)
    }

    private fun processObserverWithRestart(observer: Observable<Unit>, delayTime: Long) {
        observer.concatWith(getDelayObserver(delayTime)).observeOn(AndroidSchedulers.mainThread()).doOnCompleted {
            ExtensionRestartDialog(this).showDialog(router)
        }.doOnError { it -> Timber.e(it) }.subscribe()
    }

}

