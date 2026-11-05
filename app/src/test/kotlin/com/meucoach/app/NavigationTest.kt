package com.meucoach.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `returning to today does not reopen the last tab after a plain navigation`() {
        compose.setContent { TestApp() }

        // Bottom bar: Today -> Trends -> Today (works and stores the Trends state).
        compose.onNodeWithTag("tab-trends").performClick()
        compose.onNodeWithTag("screen-trends").assertIsDisplayed()
        compose.onNodeWithTag("tab-today").performClick()
        compose.onNodeWithTag("screen-today").assertIsDisplayed()

        // "See trends" card: a plain navigate, without the tab options.
        compose.onNodeWithTag("card-trends").performClick()
        compose.onNodeWithTag("screen-trends").assertIsDisplayed()

        // Regression: this used to restore the saved Trends stack under the Today id.
        compose.onNodeWithTag("tab-today").performClick()
        compose.onNodeWithTag("screen-today").assertIsDisplayed()
    }

    @Composable
    private fun TestApp() {
        val nav = rememberNavController()
        Column {
            NavHost(
                navController = nav,
                startDestination = "today",
                modifier = Modifier.weight(1f),
            ) {
                composable("today") {
                    Column {
                        Text("Today", modifier = Modifier.testTag("screen-today"))
                        Text(
                            text = "See trends",
                            modifier = Modifier
                                .testTag("card-trends")
                                .clickable { nav.navigate("trends") { launchSingleTop = true } },
                        )
                    }
                }
                composable("trends") {
                    Text("Trends", modifier = Modifier.testTag("screen-trends"))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Today tab",
                    modifier = Modifier
                        .testTag("tab-today")
                        .clickable { nav.navigateToTab("today") },
                )
                Text(
                    text = "Trends tab",
                    modifier = Modifier
                        .testTag("tab-trends")
                        .clickable { nav.navigateToTab("trends") },
                )
            }
        }
    }
}
