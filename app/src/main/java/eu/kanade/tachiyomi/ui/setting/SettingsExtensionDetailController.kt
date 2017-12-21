package eu.kanade.tachiyomi.ui.setting

import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.support.v7.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.SourceWithPreferences
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.catalogue.extension.ExtensionRestartDialog
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.widget.preference.SourceLoginDialog
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class SettingsExtensionDetailController(bundle: Bundle? = null) : SettingsController(), ExtensionRestartDialog.Listener {

    private val sourceManager: SourceManager by injectLazy()

    private lateinit var ext: SExtension

    constructor(extension: SExtension) : this() {
        this.ext = extension
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "source_" + ext.source
        super.onCreatePreferences(savedInstanceState, rootKey)
    }


    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.ext_details
        preferenceCategory {
            title = ext.name.capitalize() + " " + ext.version
        }
        preference {
            title = "Uninstall"
            onClick {
                val ctrl = UninstallDialog(ext!!)
                ctrl.targetController = this@SettingsExtensionDetailController
                ctrl.showDialog(router)
            }
        }
        var source = sourceManager.get(ext!!.source)
        if (source is SourceWithPreferences) {
            source.sharedPreference = preferenceManager.sharedPreferences
            if (source is LoginSource) {
                preference {
                    title = "Login"
                    onClick {
                        val dialog = SourceLoginDialog(source)
                        dialog.targetController = this@SettingsExtensionDetailController
                        dialog.showDialog(router)
                    }
                }
            }
            val all = source.sharedPreference.all
            preferenceCategory {
                for (c in all) {
                    when (c.value) {
                        is String -> {
                            editTextPreference {
                                key = c.key
                                title = c.key
                                defaultValue = c.value as String
                            }
                        }
                        else -> {
                            Timber.e("Preference is not a string")
                        }
                    }
                }
            }

        }
    }


    class UninstallDialog(bundle: Bundle? = null) : DialogController() {

        private var extension: SExtension? = null

        constructor(extension: SExtension) : this() {
            this.extension = extension
        }

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!

            return MaterialDialog.Builder(activity!!)
                    .title(R.string.ext_uninstall_dialog)
                    .negativeText(android.R.string.cancel)
                    .positiveText(R.string.ext_uninstall_dialog_positive)
                    .alwaysCallInputCallback()
                    .content(extension!!.name + " " + extension!!.version)
                    .onPositive { _, _ -> (targetController as? SettingsExtensionDetailController)?.uninstallExtension(extension!!) }
                    .build()
        }

    }

    fun uninstallExtension(ext: SExtension) {
        val uninstallObserver = Observable.fromCallable {
            var intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE)
            intent.data = Uri.parse("package:" + ext.packageName)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            startActivity(intent)
        }
        processObserverWithRestart(uninstallObserver)
    }

    override fun restart() {
        var launchIntent = Intent(this.applicationContext!!, MainActivity::class.java)
        var intent = PendingIntent.getActivity(applicationContext, 0, launchIntent, 0)
        var manager = this.applicationContext!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, intent)
        Process.killProcess(Process.myPid())
    }

    private fun processObserverWithRestart(observer: Observable<Unit>) {
        observer.concatWith(Observable.empty<Unit>().delay(2L, TimeUnit.SECONDS)).observeOn(AndroidSchedulers.mainThread()).doOnCompleted {
            ExtensionRestartDialog(this@SettingsExtensionDetailController).showDialog(router)
        }.doOnError { it -> Timber.e(it) }.subscribe()
    }
}