package com.jlucraft.console.data.push

import java.util.Calendar

object PushNotificationPolicy {
    // Force-enabled events that always notify regardless of settings
    private val FORCE_ENABLED = setOf("AuthChallenge", "instance_crash")
    private val ALERT_EVENTS = PushNotificationFormatter.ALERT_EVENTS

    // Cached preferences — set externally by settings loader
    @Volatile var enabledEventTypes: Set<String> = emptySet()
    @Volatile var dndEnabled: Boolean = false
    @Volatile var dndStartHour: Int = 22
    @Volatile var dndEndHour: Int = 7

    fun shouldNotify(eventType: String): Boolean {
        // Force-enabled events always notify
        if (eventType in FORCE_ENABLED) return true

        // Check DND window (uses cached values, NO blocking IO)
        if (dndEnabled) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val inDndWindow = if (dndStartHour <= dndEndHour) {
                currentHour >= dndStartHour && currentHour < dndEndHour
            } else {
                currentHour >= dndStartHour || currentHour < dndEndHour
            }
            if (inDndWindow) {
                // During DND, only alert events and AuthChallenge bypass
                val bypassEvents = ALERT_EVENTS + setOf("AuthChallenge")
                return eventType in bypassEvents
            }
        }

        // Check if event type is in notification events
        if (eventType !in PushNotificationFormatter.NOTIFICATION_EVENTS) return false

        // If no enabled types configured, notify all
        if (enabledEventTypes.isEmpty()) return true

        return eventType in enabledEventTypes
    }

    fun isInDndWindow(): Boolean {
        if (!dndEnabled) return false
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (dndStartHour <= dndEndHour) {
            currentHour >= dndStartHour && currentHour < dndEndHour
        } else {
            currentHour >= dndStartHour || currentHour < dndEndHour
        }
    }
}
