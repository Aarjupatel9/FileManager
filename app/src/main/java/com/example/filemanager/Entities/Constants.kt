package com.example.filemanager.Entities

object Constants {
    var notificationId = "google_chat_message_alert";
    var notificationName = "Google Chat Message Alert";

    var notificationIdForNotification = 123;

    object SORT_CONSTANTS {
        var SORT_BY_NAME_ASC = 1
        var SORT_BY_NAME_DESC = 2
        var SORT_BY_SIZE_ASC = 3
        var SORT_BY_SIZE_DESC = 4
        var SORT_BY_DATE_ASC = 5
        var SORT_BY_DATE_DESC = 6
    }
        var SORT_TYPE = arrayOf("sortByNameAsc","sortByNameDesc","sortBySizeAsc","sortBySizeDesc","sortByDateAsc","sortByDateDesc");
}