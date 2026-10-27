package com.jassemsendi.unpacked.ui.welcome

import com.jassemsendi.unpacked.model.Source

/** How to ask each supported Source for an Export, step by step, in Unpacked's own words and with no logos. */
fun exportInstructions(source: Source): List<String> =
    when (source) {
        Source.Instagram -> {
            listOf(
                "In the Instagram app or on the website, open Settings, then Accounts Center.",
                "Choose \"Your information and permissions\", then \"Download your information\".",
                "Choose \"Download or transfer information\", select your Instagram account, " +
                    "and ask for all available information.",
                "Choose \"Download to device\". Set the format to JSON, the date range to all time, " +
                    "and media quality to low: Unpacked never opens media, so a small file is enough.",
                "Submit the request. You get an email when the zip is ready, which can take a few days. " +
                    "A large Export arrives as several parts; each part is imported on its own.",
                "Download the zip. There is no need to unzip it.",
            )
        }
    }
