package ir.sahlbit.launcher

import android.content.Context
import android.provider.CallLog

data class RecentCallRecord(
    val id: Long,
    val number: String,
    val name: String,
    val type: Int,
    val date: Long,
    val durationSeconds: Long
)

fun readRecentCallRecords(context: Context, limit: Int = 30): List<RecentCallRecord> = runCatching {
    val projection = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION
    )
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        null,
        null,
        "${CallLog.Calls.DATE} DESC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
        val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        buildList {
            while (cursor.moveToNext() && size < limit) {
                add(RecentCallRecord(
                    id = cursor.getLong(idIndex),
                    number = cursor.getString(numberIndex).orEmpty(),
                    name = cursor.getString(nameIndex).orEmpty(),
                    type = cursor.getInt(typeIndex),
                    date = cursor.getLong(dateIndex),
                    durationSeconds = cursor.getLong(durationIndex)
                ))
            }
        }
    } ?: emptyList()
}.getOrDefault(emptyList())
