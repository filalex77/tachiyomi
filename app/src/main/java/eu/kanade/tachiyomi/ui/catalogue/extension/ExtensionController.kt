package eu.kanade.tachiyomi.ui.catalogue.extension

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import eu.kanade.tachiyomi.ui.catalogue.SourceDividerItemDecoration
import eu.kanade.tachiyomi.ui.main.MainActivity
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
        NucleusController<ExtensionPresenter>(),
        FlexibleAdapter.OnItemClickListener, ExtensionDownloadDialog.Listener, ExtensionUninstallDialog.Listener, ExtensionRestartDialog.Listener {

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
        ext_recycler.addItemDecoration(SourceDividerItemDecoration(view.context))
        ext_progress.visible()
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
        snack = null
    }

    /**
     * Called from the presenter when the network request is received.
     *
     * @param page the current page.
     * @param mangas the list of manga of the page.
     */
    fun onAdd(extension: List<ExtensionItem>) {
        val adapter = adapter ?: return
        hideProgressBar()
        adapter.onLoadMoreComplete(extension)
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
                presenter.updateExtensions()
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

    /**
     * Called when a extension is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(position: Int): Boolean {
        val item = adapter?.getItem(position) as? ExtensionItem ?: return false
        if (!item.extension.upToDate) {
            val dialog = ExtensionDownloadDialog(this, item.extension)
            dialog.showDialog(router)
        }
        if (item.extension.installed) {
            val dialog = ExtensionUninstallDialog(this, item!!.extension)
            dialog.showDialog(router)
        }
        return false
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

    override fun uninstallExtension(ext: SExtension) {
        val uninstallObserver = Observable.fromCallable {
            var intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE)
            intent.data = Uri.parse("package:" + ext.packageName)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            startActivity(intent)
        }
        processObserverWithRestart(uninstallObserver, 1L)
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

