package com.jassemsendi.unpacked.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedCodeReportTest {
    private val files =
        listOf(
            CountedFile("./core/model/src/commonMain/kotlin/Event.kt", code = 100),
            CountedFile("./core/data/src/commonMain/kotlin/Library.kt", code = 200),
            CountedFile("./core/data/src/jvmMain/kotlin/Open.kt", code = 10),
            CountedFile("./core/data/src/commonTest/kotlin/LibraryTest.kt", code = 50),
            CountedFile("./core/fixtures/src/commonMain/kotlin/SyntheticInstagramExport.kt", code = 60),
            CountedFile("./ui/src/iosMain/kotlin/Main.kt", code = 20),
            CountedFile("./desktopApp/src/main/kotlin/Main.kt", code = 30),
            CountedFile("./androidApp/src/main/kotlin/MainActivity.kt", code = 40),
            CountedFile("./desktopApp/src/test/kotlin/EvalHarness.kt", code = 25),
            CountedFile("./iosApp/iosApp/iOSApp.swift", code = 15),
            CountedFile("./build.gradle.kts", code = 5),
            CountedFile("./core/model/build.gradle.kts", code = 3),
            CountedFile("./build-logic/src/main/kotlin/unpacked.quality.gradle.kts", code = 7),
            CountedFile("./build-logic/src/main/kotlin/com/jassemsendi/unpacked/buildlogic/SharedCodeReport.kt", code = 9),
        )

    @Test
    fun clocCsvIsReadByFileAndItsHeaderAndSumAreLeftOut() {
        val csv =
            """
            language,filename,blank,comment,code,"github.com/AlDanial/cloc v 2.12  T=0.53 s (542.0 files/s 57046.6 lines/s)"
            Kotlin,./core/data/src/commonTest/kotlin/LibraryTest.kt,116,17,994
            Gradle,./core/model/build.gradle.kts,0,0,3
            SUM,,3399,2203,24817
            """.trimIndent()

        assertEquals(
            listOf(
                CountedFile("./core/data/src/commonTest/kotlin/LibraryTest.kt", code = 994),
                CountedFile("./core/model/build.gradle.kts", code = 3),
            ),
            SharedCodeReport.parseClocCsv(csv),
        )
    }

    @Test
    fun filesAreGroupedBySourceSetWithTheModulesThatHoldThem() {
        val rows = SharedCodeReport.rows(files)

        assertEquals(
            listOf(
                Row("commonMain", Role.Production, modules = listOf("core:data", "core:model"), files = 2, code = 300),
                Row("main", Role.Production, modules = listOf("androidApp", "desktopApp"), files = 2, code = 70),
                Row("iosMain", Role.Production, modules = listOf("ui"), files = 1, code = 20),
                Row("Swift", Role.Production, modules = listOf("iosApp"), files = 1, code = 15),
                Row("jvmMain", Role.Production, modules = listOf("core:data"), files = 1, code = 10),
                Row("commonMain", Role.Test, modules = listOf("core:fixtures"), files = 1, code = 60),
                Row("commonTest", Role.Test, modules = listOf("core:data"), files = 1, code = 50),
                Row("test", Role.Test, modules = listOf("desktopApp"), files = 1, code = 25),
                Row("Build logic", Role.Build, modules = listOf("build-logic"), files = 2, code = 16),
                Row("Gradle scripts", Role.Build, modules = listOf("core:model", "root"), files = 2, code = 8),
            ),
            rows,
        )
    }

    @Test
    fun theSharedShareIsCommonMainOverEveryProductionSourceSet() {
        val summary = SharedCodeReport.summary(SharedCodeReport.rows(files))

        // 300 of 300 + 70 + 20 + 15 + 10 = 415 production lines.
        assertEquals(300, summary.commonCode)
        assertEquals(415, summary.productionCode)
        assertEquals(72, summary.commonPercent)
    }

    @Test
    fun theTableIsMarkdownWithTotalsAndTheShareUnderIt() {
        val markdown = SharedCodeReport.render(files)

        assertTrue(markdown.startsWith("| Source set | Modules | Files | Lines of code |\n| --- | --- | ---: | ---: |\n"), markdown)
        assertTrue("| `commonMain` | core:data, core:model | 2 | 300 |" in markdown, markdown)
        assertTrue("| **Production** | | 7 | 415 |" in markdown, markdown)
        assertTrue("| **Tests** | | 3 | 135 |" in markdown, markdown)
        assertTrue("| `Gradle scripts` | core:model, root | 2 | 8 |" in markdown, markdown)
        assertTrue("| `Build logic` | build-logic | 2 | 16 |" in markdown, markdown)
        assertTrue(markdown.trimEnd().endsWith("Common Kotlin (`commonMain`) is 72% of the production code: 300 of 415 lines."), markdown)
    }

    @Test
    fun theReadmeBlockBetweenTheMarkersIsReplacedAndTheRestKept() {
        val readme =
            """
            # Unpacked

            <!-- shared-code-report:start -->
            old table
            <!-- shared-code-report:end -->

            ## License
            """.trimIndent()

        val updated = SharedCodeReport.updateReadme(readme, "new table\n")

        assertEquals(
            """
            # Unpacked

            <!-- shared-code-report:start -->
            new table
            <!-- shared-code-report:end -->

            ## License
            """.trimIndent(),
            updated,
        )
    }
}
