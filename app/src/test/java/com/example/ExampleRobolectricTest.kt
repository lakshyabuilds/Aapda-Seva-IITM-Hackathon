package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExampleRobolectricTest {

    init {
        ShadowLog.stream = System.out
    }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Aapda Seva", appName)
  }

  @Test
  fun `launch MainActivity`() {
      try {
          ActivityScenario.launch(MainActivity::class.java).use { scenario ->
              scenario.onActivity { activity ->
                  println("MainActivity launched successfully.")
              }
          }
      } catch (e: Exception) {
          e.printStackTrace()
          throw e
      }
  }
}
