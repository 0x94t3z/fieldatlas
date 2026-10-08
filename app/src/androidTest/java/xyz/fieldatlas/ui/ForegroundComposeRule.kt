package xyz.fieldatlas.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Work around MIUI's fixture background-launch restriction without changing permissions. */
fun foregroundComposeRule(): ComposeContentTestRule {
    val delegate = createComposeRule()
    return object : ComposeContentTestRule by delegate {
        override fun apply(base: Statement, description: Description): Statement {
            val test = delegate.apply(base, description)
            return object : Statement() {
                override fun evaluate() {
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val target = instrumentation.targetContext.packageName
                    // Only the separate debug sandbox is brought forward. Never launch a real
                    // user's MainActivity in a release-host fixture or alter its saved state.
                    if (target.endsWith(".debug")) {
                        // This must be DIFFERENT from ActivityScenario's ComponentActivity:
                        // pre-launching that same class creates a stale ActivityScenario handle.
                        val command = "am start -W -n $target/xyz.fieldatlas.MainActivity"
                        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                            .bufferedReader().use { it.readText() }
                    }
                    test.evaluate()
                }
            }
        }
    }
}
