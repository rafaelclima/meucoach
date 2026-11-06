package com.meucoach.app.reminder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

/** Routes a notification tap into the Compose navigation graph. */
object NotificationNavigation {
    const val ROUTE_TODAY = "today"
    const val ROUTE_CHECK_IN = "check-in"
    const val ROUTE_ACTIVITIES = "activities"
    const val ROUTE_DATA = "profile/data"

    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    fun request(route: String) {
        _route.value = route
    }

    fun consume() {
        _route.value = null
    }

    fun routeFor(rawUri: String?): String? {
        if (rawUri.isNullOrBlank()) return null
        val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
        if (uri.scheme != "meucoach") return null
        return when (uri.host) {
            "today" -> ROUTE_TODAY

            "check-in" -> ROUTE_CHECK_IN

            "activities" -> ROUTE_ACTIVITIES

            "data" -> ROUTE_DATA

            "activity" ->
                uri.path
                    ?.trim('/')
                    ?.takeIf { it.isNotBlank() }
                    ?.let { activityId -> "activity/$activityId" }

            else -> null
        }
    }
}
