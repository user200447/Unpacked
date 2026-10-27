package com.jassemsendi.unpacked

/** Which desktop OS this process runs on, asked once. */
internal object HostOs {
    private val name = System.getProperty("os.name").lowercase()
    val isMac: Boolean = name.contains("mac")
    val isWindows: Boolean = name.contains("windows")
}
