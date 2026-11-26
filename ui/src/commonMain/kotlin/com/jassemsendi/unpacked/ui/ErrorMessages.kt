package com.jassemsendi.unpacked.ui

import com.jassemsendi.unpacked.model.DataError

/**
 * What the user reads when the library could not do what a screen asked. The screens hold plain
 * strings until the app has string resources; this is the one place a [DataError] becomes words.
 */
fun DataError.toMessage(): String =
    when (this) {
        DataError.Local.DISK_FULL -> "The device is out of storage."
        DataError.Local.NOT_FOUND -> "That is no longer in your library."
        DataError.Local.UNKNOWN -> "Your library could not be read or written."
    }
