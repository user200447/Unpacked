package com.jassemsendi.unpacked.ui.imports

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject

/**
 * Presents UIKit's document picker over whatever view controller is on top of the active window. One sheet at a
 * time: UIKit refuses to present over one already up, so a pick asked for while the sheet is showing is answered
 * as cancelled at once and the sheet keeps answering the caller that opened it.
 */
internal class UiKitDocumentPickerPresenter : DocumentPickerPresenter {
    /** UIKit holds its delegate weakly; this keeps it alive until the picker has answered, and marks a sheet as up. */
    private var delegate: PickerDelegate? = null

    override fun pickZip(onPicked: (NSURL?) -> Unit) {
        val from = if (delegate == null) topViewController() else null
        if (from == null) {
            onPicked(null)
            return
        }
        val controller =
            UIDocumentPickerViewController(forOpeningContentTypes = listOf<Any?>(UTTypeZIP), asCopy = false)
        val delegate =
            PickerDelegate { url ->
                this.delegate = null
                onPicked(url)
            }
        this.delegate = delegate
        controller.delegate = delegate
        from.presentViewController(controller, animated = true, completion = null)
    }

    private class PickerDelegate(
        private val onResult: (NSURL?) -> Unit,
    ) : NSObject(),
        UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            onResult(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onResult(null)
        }
    }
}

/** The view controller on top of the foreground window: the root, or whatever it has presented over itself. */
private fun topViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    val scene =
        scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: scenes.firstOrNull()
    var top = scene?.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
