package eu.kanade.tachiyomi.ui.catalogue.extension

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController

/**
 * Dialog to create a new category for the library.
 */
class ExtensionRestartDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
        where T : Controller, T : ExtensionRestartDialog.Listener {

    constructor(target: T) : this() {
        targetController = target
    }

    /**
     * Called when creating the dialog for this controller.
     *
     * @param savedViewState The saved state of this dialog.
     * @return a new dialog instance.
     */
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialDialog.Builder(activity!!)
                .title(R.string.ext_restart_dialog_title)
                .negativeText(android.R.string.cancel)
                .positiveText(R.string.ext_restart_dialog_positive)
                .alwaysCallInputCallback()
                .content(R.string.ext_restart_dialog_content)
                .onPositive { _, _ -> (targetController as? Listener)?.restart() }
                .build()
    }

    interface Listener {
        fun restart()
    }

}