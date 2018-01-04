package eu.kanade.tachiyomi.ui.extension

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.preference.*
import android.support.v7.preference.internal.AbstractMultiSelectListPreference
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.DividerItemDecoration.VERTICAL
import android.support.v7.widget.LinearLayoutManager
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jakewharton.rxbinding.view.clicks
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.EmptyPreferenceDataStore
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.setting.initThenAdd
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.widget.preference.LoginPreference
import eu.kanade.tachiyomi.widget.preference.SourceLoginDialog
import eu.kanade.tachiyomi.widget.preference.SwitchPreferenceCategory
import kotlinx.android.synthetic.main.extension_detail_controller.*

@SuppressLint("RestrictedApi")
class ExtensionDetailsController(bundle: Bundle? = null) :
        NucleusController<ExtensionDetailsPresenter>(bundle),
        PreferenceManager.OnDisplayPreferenceDialogListener,
        DialogPreference.TargetFragment,
        SourceLoginDialog.Listener {

    private var lastOpenPreferencePosition: Int? = null

    private var preferenceScreen: PreferenceScreen? = null

    constructor(pkgName: String) : this(Bundle().apply {
        putString(PKGNAME_KEY, pkgName)
    })

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.extension_detail_controller, container, false)
    }

    override fun createPresenter(): ExtensionDetailsPresenter {
        return ExtensionDetailsPresenter(args.getString(PKGNAME_KEY))
    }

    override fun getTitle(): String? {
        return "Extension info" // TODO resource
    }

    @SuppressLint("PrivateResource")
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        val extension = presenter.extension

        // TODO resource
        extension_title.text = extension.name
        extension_version.text = "Version: ${extension.versionName}"
        extension_lang.text = "Language: ${extension.getLocalizedLang(view.context)}"
        extension_pkg.text = extension.pkgName
        try {
            val icon = view.context.packageManager.getApplicationIcon(extension.pkgName)
            extension_icon.setImageDrawable(icon)
        } catch (e: PackageManager.NameNotFoundException) {
            // If the package was uninstalled, don't show any icon
        }
        extension_uninstall_button.clicks().subscribeUntilDestroy {
            presenter.uninstallExtension()
        }

        val themedContext by lazy { getPreferenceThemeContext() }
        val manager = PreferenceManager(themedContext)
        manager.preferenceDataStore = EmptyPreferenceDataStore()
        manager.onDisplayPreferenceDialogListener = this
        val screen = manager.createPreferenceScreen(themedContext)
        preferenceScreen = screen

        val hasEnabledOption = extension.sources.size > 1

        for (source in extension.sources) {
            if (hasEnabledOption || source is ConfigurableSource || source is LoginSource) {
                addPreferencesForSource(screen, source, hasEnabledOption)
            }
        }

        manager.setPreferences(screen)

        extension_prefs_recycler.layoutManager = LinearLayoutManager(view.context)
        extension_prefs_recycler.adapter = PreferenceGroupAdapter(screen)
        extension_prefs_recycler.addItemDecoration(DividerItemDecoration(view.context, VERTICAL))
    }

    override fun onDestroyView(view: View) {
        preferenceScreen = null
        super.onDestroyView(view)
    }

    fun onExtensionUninstalled() {
        router.popCurrentController()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        lastOpenPreferencePosition?.let { outState.putInt(LASTOPENPREFERENCE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lastOpenPreferencePosition = savedInstanceState.get(LASTOPENPREFERENCE_KEY) as? Int
    }

    private fun addPreferencesForSource(screen: PreferenceScreen, source: Source, hasEnabledOption: Boolean) {
        val context = screen.context

        val dataStore = SharedPreferencesDataStore(if (source is HttpSource) {
            source.preferences
        } else {
            context.getSharedPreferences("source_${source.id}", Context.MODE_PRIVATE)
        })

        if (hasEnabledOption) {
            screen.initThenAdd(SwitchPreferenceCategory(context)) {
                title = source.toString()
                key = "enabled"
                preferenceDataStore = dataStore
            }
        }
        if (source is LoginSource) {
            screen.initThenAdd(LoginPreference(context)) {
                title = "Login" // TODO resource
                key = "user"
                preferenceDataStore = dataStore
                onClick {
                    val dialog = SourceLoginDialog(source)
                    dialog.targetController = this@ExtensionDetailsController
                    dialog.showDialog(router)
                }
            }
        }
        if (source is ConfigurableSource) {
            val newScreen = screen.preferenceManager.createPreferenceScreen(context)
            source.setupPreferenceScreen(newScreen)

            for (i in 0 until newScreen.preferenceCount) {
                val pref = newScreen.getPreference(i)
                pref.preferenceDataStore = dataStore
                pref.order = Int.MAX_VALUE // reset to default order
                screen.addPreference(pref)
            }
        }
    }

    private fun getPreferenceThemeContext(): Context {
        val tv = TypedValue()
        activity!!.theme.resolveAttribute(R.attr.preferenceTheme, tv, true)
        return ContextThemeWrapper(activity, tv.resourceId)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (!isAttached) return

        val screen = preference.parent!!

        lastOpenPreferencePosition = (0 until screen.preferenceCount).indexOfFirst {
            screen.getPreference(it) === preference
        }

        val f = when (preference) {
            is EditTextPreference -> EditTextPreferenceDialogController
                    .newInstance(preference.getKey())
            is ListPreference -> ListPreferenceDialogController
                    .newInstance(preference.getKey())
            is AbstractMultiSelectListPreference -> MultiSelectListPreferenceDialogController
                    .newInstance(preference.getKey())
            else -> throw IllegalArgumentException("Tried to display dialog for unknown " +
                    "preference type. Did you forget to override onDisplayPreferenceDialog()?")
        }
        f.targetController = this
        f.showDialog(router)
    }

    override fun findPreference(key: CharSequence?): Preference {
        return preferenceScreen!!.getPreference(lastOpenPreferencePosition!!)
    }

    override fun loginDialogClosed(source: LoginSource) {
        val lastOpen = lastOpenPreferencePosition ?: return
        (preferenceScreen?.getPreference(lastOpen) as? LoginPreference)?.notifyChanged()
    }

    private companion object {
        const val PKGNAME_KEY = "pkg_name"
        const val LASTOPENPREFERENCE_KEY = "last_open_preference"
    }

}