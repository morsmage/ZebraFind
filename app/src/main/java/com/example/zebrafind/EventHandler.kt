package com.example.zebrafind

import android.content.ContentValues
import android.util.Log
import com.zebra.rfid.api3.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Read/Status Notify handler
// Implement the RfidEventsLister class to receive event notifications
class EventHandler : RfidEventsListener {

    // Read Event Notification
    override fun eventReadNotify(e: RfidReadEvents) {
        // Recommended to use new method getReadTagsEx for better performance in case of large tag population
        val tags = reader.Actions.getReadTags(100)
        if (tags != null) {
            for (i in tags.indices) {
                Log.d(ContentValues.TAG, "Tag ID ${tags[i].tagID}")

                CoroutineScope(Dispatchers.Main).launch {
                    MainActivity().textView.text = "Tag ID ${tags[i].tagID}"
                }

                if (tags[i].tagID == "020000$licensePlate") {
                    Log.d(ContentValues.TAG, "External LP " + tags[i].tagID.takeLast(18))
                    Log.d(ContentValues.TAG, tags[i].peakRSSI.toString())
                }
            }
        }
    }

    // Status Event Notification
    override fun eventStatusNotify(e: RfidStatusEvents) {
        if (e.StatusEventData.statusEventType === STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
            if (e.StatusEventData.HandheldTriggerEventData.handheldEvent === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
                println("Trigger pressed")
                reader.Actions.Inventory.perform()
            }
            if (e.StatusEventData.HandheldTriggerEventData.handheldEvent === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                println("Trigger released")
                reader.Actions.Inventory.stop()
            }
        }
    }
}
