package eu.kanade.tachiyomi.ui.setting

import android.graphics.drawable.Drawable
import android.support.v7.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.updater.UpdateDownloaderService
import eu.kanade.tachiyomi.extension.ExtensionManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.*

class SettingsExtensionController : SettingsController(){

    private val extensions by lazy { Injekt.get<ExtensionManager>().getExtensions() }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_extensions

        // Get the list of active language codes.
        val activeLangsCodes = preferences.enabledLanguages().getOrDefault()

        // Get a map of sources grouped by language.
        val extensionsByLang = extensions.groupByTo(TreeMap(), { it.lang })

        // Order first by active languages, then inactive ones
        val orderedLangs = extensionsByLang.keys.filter { it in activeLangsCodes } +
                extensionsByLang.keys.filterNot { it in activeLangsCodes }

        orderedLangs.forEach { lang ->
            val sources = extensionsByLang[lang].orEmpty().sortedBy { it.name }

            sources.forEach {
                preference {
                    //val id = it.id.toString()
                    title = it.name
                    //key = getSourceKey(source.id)
                    onClick {
                        val appContext = applicationContext
                        if (appContext != null) {
                            // Start download
                            UpdateDownloaderService.downloadUpdate(appContext, it.url)
                        }
                    }
                }

            }

        }
    }

    override fun setDivider(divider: Drawable?) {
        super.setDivider(null)
    }

}