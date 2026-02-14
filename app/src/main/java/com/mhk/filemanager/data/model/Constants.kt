package com.mhk.filemanager.data.model

object Constants {
    const val notificationId = "google_chat_message_alert"
    const val notificationName = "Google Chat Message Alert"

    const val notificationIdForNotification = 123

    object SORT_CONSTANTS {
        const val SORT_BY_NAME_ASC = 1
        const val SORT_BY_NAME_DESC = 2
        const val SORT_BY_SIZE_ASC = 3
        const val SORT_BY_SIZE_DESC = 4
        const val SORT_BY_DATE_ASC = 5
        const val SORT_BY_DATE_DESC = 6
    }

    val SORT_TYPE = arrayOf(
        "sortByNameAsc",
        "sortByNameDesc",
        "sortBySizeAsc",
        "sortBySizeDesc",
        "sortByDateAsc",
        "sortByDateDesc"
    )
}