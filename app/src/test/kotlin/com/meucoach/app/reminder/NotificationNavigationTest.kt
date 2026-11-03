package com.meucoach.app.reminder

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class NotificationNavigationTest {
    @Test
    fun `maps check-in and today links`() {
        assertThat(NotificationNavigation.routeFor("meucoach://check-in")).isEqualTo("check-in")
        assertThat(NotificationNavigation.routeFor("meucoach://today")).isEqualTo("today")
    }

    @Test
    fun `maps import results`() {
        assertThat(NotificationNavigation.routeFor("meucoach://activities")).isEqualTo("activities")
        assertThat(NotificationNavigation.routeFor("meucoach://activity/abc-123"))
            .isEqualTo("activity/abc-123")
        assertThat(NotificationNavigation.routeFor("meucoach://activity/")).isNull()
    }

    @Test
    fun `ignores other links`() {
        assertThat(NotificationNavigation.routeFor("meucoach://profile")).isNull()
        assertThat(NotificationNavigation.routeFor("https://example.com/today")).isNull()
        assertThat(NotificationNavigation.routeFor("not a uri")).isNull()
        assertThat(NotificationNavigation.routeFor(null)).isNull()
    }

    @Test
    fun `publishes and consumes a route`() {
        NotificationNavigation.request("check-in")
        assertThat(NotificationNavigation.route.value).isEqualTo("check-in")
        NotificationNavigation.consume()
        assertThat(NotificationNavigation.route.value).isNull()
    }
}
