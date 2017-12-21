package eu.kanade.tachiyomi.ui.catalogue.extension

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.ui.base.controller.DialogController

/**
 * Dialog to create a new category for the library.
 */
class ExtensionDownloadDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
        where T : Controller, T : ExtensionDownloadDialog.Listener {

    private var extension: SExtension? = null

    constructor(target: T, extension: SExtension) : this() {
        targetController = target
        this.extension = extension
    }

    /**
     * Called when creating the dialog for this controller.
     *
     * @param savedViewState The saved state of this dialog.
     * @return a new dialog instance.
     */
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialDialog.Builder(activity!!)
                .title(R.string.ext_download_dialog)
                .negativeText(android.R.string.cancel)
                .positiveText(R.string.ext_download_dialog_positive)
                .alwaysCallInputCallback()
                .content(extension!!.name + " " + extension!!.version)
                .onPositive { _, _ -> (targetController as? Listener)?.downloadExtension(extension!!) }
                .build()
    }

    interface Listener {
        fun downloadExtension(ext: SExtension)
    }

}