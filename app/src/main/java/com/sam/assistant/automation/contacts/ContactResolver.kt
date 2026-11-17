package com.sam.assistant.automation.contacts

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import com.sam.assistant.core.permissions.PermissionManager

data class ContactEntry(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val phoneType: String = "Mobile"
)

class ContactResolver(private val context: Context) {

    companion object {
        private const val TAG = "ContactResolver"

        fun maskPhoneNumber(number: String): String {
            val cleaned = number.trim()
            if (cleaned.length <= 4) return "****"
            val visibleStart = cleaned.take(4)
            val visibleEnd = cleaned.takeLast(2)
            val maskedMiddle = "*".repeat((cleaned.length - 6).coerceAtLeast(3))
            return "$visibleStart$maskedMiddle$visibleEnd"
        }
    }

    /**
     * Searches contacts provider by name, returning all matching candidates.
     */
    fun findContacts(nameQuery: String): List<ContactEntry> {
        val query = nameQuery.trim()
        if (query.isBlank() || !PermissionManager.hasContactsPermission(context)) {
            return emptyList()
        }

        val results = mutableListOf<ContactEntry>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null) {
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: ""
                    val name = cursor.getString(nameIdx) ?: ""
                    val number = cursor.getString(numIdx) ?: ""
                    val typeInt = cursor.getInt(typeIdx)
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        typeInt,
                        "Mobile"
                    ).toString()

                    results.add(ContactEntry(id, name, number, typeLabel))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contacts query failed", e)
        } finally {
            cursor?.close()
        }

        Log.d(TAG, "Contact search for \"$query\" found ${results.size} candidates (Private details masked in logs)")
        return results
    }

    /**
     * Resolves single unique contact or null if ambiguous/missing.
     */
    fun findSingleContact(nameQuery: String): ContactEntry? {
        val matches = findContacts(nameQuery)
        return if (matches.size == 1) matches.first() else null
    }
}
