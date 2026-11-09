package ir.sahlbit.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

private val CustomInk = Color(0xFF172033)
private val CustomBlue = Color(0xFF2563EB)
private val CustomCream = Color(0xFFFFF8E7)

private fun Int.toPersianDigits(): String = toString().map { ch ->
    if (ch in '0'..'9') ('۰'.code + ch.code - '0'.code).toChar() else ch
}.joinToString("")

fun saveSquarePhoto(context: Context, source: Bitmap): String? = runCatching {
    val side = minOf(source.width, source.height)
    val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val file = File(context.filesDir, "sahlbit_photo_${UUID.randomUUID()}.jpg")
    file.outputStream().use { output -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, output) }
    require(file.exists() && file.length() > 0)
    Uri.fromFile(file).toString()
}.getOrNull()

data class CustomContactPhone(val id: String = UUID.randomUUID().toString(), val number: String, val photoUri: String? = null)
data class CustomContact(val id: String = UUID.randomUUID().toString(), val name: String, val phones: List<CustomContactPhone>)

object CustomContactsStore {
    private const val KEY = "sahlbit_custom_contacts_v1"
    fun load(prefs: android.content.SharedPreferences): List<CustomContact> = runCatching {
        val root = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        buildList {
            for (i in 0 until root.length()) {
                val item = root.getJSONObject(i)
                val phones = item.optJSONArray("phones") ?: JSONArray()
                add(CustomContact(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    name = item.optString("name"),
                    phones = buildList {
                        for (j in 0 until phones.length()) {
                            val phone = phones.getJSONObject(j)
                            add(CustomContactPhone(phone.optString("id", UUID.randomUUID().toString()), phone.optString("number"), phone.optString("photo").takeIf(String::isNotBlank)))
                        }
                    }
                ))
            }
        }
    }.getOrDefault(emptyList())
    fun save(prefs: android.content.SharedPreferences, contacts: List<CustomContact>) {
        val root = JSONArray()
        contacts.forEach { contact ->
            root.put(JSONObject().apply {
                put("id", contact.id); put("name", contact.name)
                put("phones", JSONArray().apply {
                    contact.phones.forEach { phone -> put(JSONObject().apply {
                        put("id", phone.id); put("number", phone.number); put("photo", phone.photoUri ?: "")
                    }) }
                })
            })
        }
        prefs.edit().putString(KEY, root.toString()).apply()
    }
}

@Composable
fun CustomContactsPage(
    contacts: List<CustomContact>,
    onSave: (List<CustomContact>) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf("") }
    var phones by remember { mutableStateOf(listOf(CustomContactPhone(number = ""))) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var photoTarget by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("") }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val index = photoTarget
        if (uri != null && index in phones.indices) {
            val bitmap = runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
            val saved = bitmap?.let { saveSquarePhoto(context, it) }
            if (saved != null) phones = phones.toMutableList().also { it[index] = it[index].copy(photoUri = saved) }
            else message = "ذخیرهٔ تصویر ناموفق بود."
        }
        photoTarget = -1
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val index = photoTarget
        if (bitmap != null && index in phones.indices) {
            val saved = saveSquarePhoto(context, bitmap)
            if (saved != null) phones = phones.toMutableList().also { it[index] = it[index].copy(photoUri = saved) }
            else message = "ذخیرهٔ عکس دوربین ناموفق بود."
        }
        photoTarget = -1
    }

    val androidContactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0).orEmpty()
                    val number = cursor.getString(1).orEmpty()
                    val photoSource = cursor.getString(2)
                    val savedPhoto = if (!photoSource.isNullOrBlank()) {
                        val bitmap = runCatching { context.contentResolver.openInputStream(Uri.parse(photoSource))?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
                        bitmap?.let { saveSquarePhoto(context, it) }
                    } else null
                    phones = listOf(CustomContactPhone(number = number, photoUri = savedPhoto))
                    if (savedPhoto != null) message = "مخاطب و تصویر آن وارد شدند؛ برای هر شماره می‌توانید تصویر جداگانه انتخاب کنید."
                }
            }
        }.onFailure { message = "خواندن مخاطب از برنامهٔ مخاطبین اندروید ناموفق بود." }
    }

    Column(Modifier.fillMaxSize().background(CustomCream).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = CustomBlue) }
            Text("مخاطبین اختصاصی", color = CustomBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text("مخاطب اختصاصی بسازید، چند شماره برای او ثبت کنید و برای هر شماره تصویر جداگانه در نظر بگیرید. اطلاعات این بخش در حافظهٔ داخلی سهل‌بیت نگهداری می‌شود.", color = CustomInk, fontSize = 14.sp, lineHeight = 24.sp)
        OutlinedButton(onClick = { androidContactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text("افزودن از برنامهٔ مخاطبین اندروید")
        }
        Text(if (editingId == null) "مخاطب جدید" else "ویرایش مخاطب", color = CustomInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام مخاطب") }, singleLine = true)
        phones.forEachIndexed { index, phone ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("شمارهٔ ${(index + 1).toPersianDigits()}", color = CustomInk, fontWeight = FontWeight.SemiBold)
                    val phoneBitmap = remember(phone.photoUri) { phone.photoUri?.let { raw -> runCatching { context.contentResolver.openInputStream(Uri.parse(raw))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } }
                    if (phoneBitmap != null) Image(phoneBitmap, contentDescription = "تصویر شماره", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    OutlinedTextField(phone.number, { value -> phones = phones.toMutableList().also { it[index] = it[index].copy(number = value) } }, modifier = Modifier.fillMaxWidth(), label = { Text("شماره تماس") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { photoTarget = index; gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Text(if (phone.photoUri == null) "گالری" else "تغییر تصویر") }
                        OutlinedButton(onClick = { photoTarget = index; camera.launch(null) }, modifier = Modifier.weight(1f)) { Text("دوربین") }
                        if (phones.size > 1) IconButton(onClick = { phones = phones.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Default.Delete, "حذف شماره", tint = Color(0xFFB3261E)) }
                    }
                }
            }
        }
        OutlinedButton(onClick = { phones = phones + CustomContactPhone(number = "") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("افزودن شمارهٔ دیگر") }
        Button(onClick = {
            val cleanName = name.trim()
            val cleanPhones = phones.map { it.copy(number = it.number.trim()) }.filter { it.number.isNotBlank() }
            if (cleanName.isBlank() || cleanPhones.isEmpty()) {
                message = "نام مخاطب و دست‌کم یک شمارهٔ تماس را وارد کنید."
            } else {
                val item = CustomContact(id = editingId ?: UUID.randomUUID().toString(), name = cleanName, phones = cleanPhones)
                val next = if (editingId == null) contacts + item else contacts.map { if (it.id == editingId) item else it }
                onSave(next)
                name = ""; phones = listOf(CustomContactPhone(number = "")); editingId = null; message = "مخاطب ذخیره شد."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("ذخیرهٔ مخاطب") }
        if (message.isNotBlank()) Text(message, color = CustomBlue, fontSize = 13.sp)
        Divider()
        Text("مخاطبان ذخیره‌شده", color = CustomInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (contacts.isEmpty()) Text("هنوز مخاطب اختصاصی ثبت نشده است.", color = CustomInk)
        contacts.forEach { contact ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(contact.name, color = CustomInk, fontWeight = FontWeight.Bold)
                    contact.phones.forEach { phone ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val phoneBitmap = remember(phone.photoUri) { phone.photoUri?.let { raw -> runCatching { context.contentResolver.openInputStream(Uri.parse(raw))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } }
                            if (phoneBitmap != null) Image(phoneBitmap, contentDescription = "تصویر ${phone.number}", modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            Text(phone.number, color = Color(0xFF64748B), fontSize = 13.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingId = contact.id; name = contact.name; phones = contact.phones.ifEmpty { listOf(CustomContactPhone(number = "")) }; message = "" }, modifier = Modifier.weight(1f)) { Text("ویرایش") }
                        OutlinedButton(onClick = { onSave(contacts.filterNot { it.id == contact.id }) }, modifier = Modifier.weight(1f)) { Text("حذف") }
                    }
                }
            }
        }
    }
}


@Composable
fun PreviewPhotoPicker(photoUri: String?, onPhotoSelected: (String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    fun persist(uri: Uri?) {
        val bitmap = uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream) } }.getOrNull() }
        onPhotoSelected(bitmap?.let { saveSquarePhoto(context, it) })
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) persist(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> onPhotoSelected(bitmap?.let { saveSquarePhoto(context, it) }) }
    val contactPhoto = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            val source = runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (!source.isNullOrBlank()) persist(Uri.parse(source))
            else android.widget.Toast.makeText(context, "برای این مخاطب تصویری ثبت نشده است.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (photoUri == null) "انتخاب تصویر مخاطب" else "تغییر تصویر مخاطب") }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text("انتخاب تصویر پیش‌نمایش") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = false; gallery.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب تصویر از گالری و برش مربعی") }
                OutlinedButton(onClick = { expanded = false; camera.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("گرفتن عکس با دوربین") }
                OutlinedButton(onClick = { expanded = false; contactPhoto.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, modifier = Modifier.fillMaxWidth()) { Text("استفاده از عکس مخاطب در برنامهٔ مخاطبین اندروید") }
                if (photoUri != null) TextButton(onClick = { expanded = false; onPhotoSelected(null) }, modifier = Modifier.fillMaxWidth()) { Text("حذف تصویر انتخاب‌شده") }
            }
        },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("بستن") } }
    )
}

@Composable
fun DialerPage(onBack: () -> Unit, onCall: (String) -> Unit, initialNumber: String = "") {
    var number by remember(initialNumber) { mutableStateOf(initialNumber) }
    val keys = listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "*", "۰", "#", "+")
    Column(Modifier.fillMaxSize().background(CustomCream).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = CustomBlue) }
            Text("شماره‌گیر اختصاصی", color = CustomBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(number, { number = it }, modifier = Modifier.fillMaxWidth(), label = { Text("شماره تماس") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            keys.chunked(3).forEach { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        OutlinedButton(onClick = { number += key }, modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(18.dp)) { Text(key, fontSize = 25.sp, color = CustomInk) }
                    }
                    if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { if (number.isNotEmpty()) number = number.dropLast(1) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Close, null); Text("پاک کردن") }
            Button(onClick = { if (number.isNotBlank()) onCall(number.map { ch -> when (ch) { in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar(); else -> ch } }.joinToString("")) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Call, null); Text("تماس") }
        }
    }
}

@Composable
fun RecentCallsCustomizationPage(prefs: android.content.SharedPreferences, onBack: () -> Unit) {
    var avatarSize by remember { mutableIntStateOf(prefs.getInt("recent_avatar_size", 64).coerceIn(40, 112)) }
    var avatarRadius by remember { mutableIntStateOf(prefs.getInt("recent_avatar_radius", 20).coerceIn(0, 50)) }
    var nameSize by remember { mutableIntStateOf(prefs.getInt("recent_name_size", 15).coerceIn(11, 24)) }
    var showNames by remember { mutableStateOf(prefs.getBoolean("recent_show_names", false)) }
    var showSavedCount by remember { mutableStateOf(prefs.getBoolean("recent_show_saved_count", false)) }
    Column(Modifier.fillMaxSize().background(CustomCream).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = CustomBlue) }
            Text("سفارشی‌سازی صفحه تماس‌های اخیر", color = CustomBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text("نمایش نام و تصویر مخاطب و تعداد مخاطبان ذخیره‌شده را مطابق نیاز تنظیم کنید.", color = CustomInk, lineHeight = 24.sp)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("نمایش نام مخاطب", Modifier.weight(1f), color = CustomInk); Switch(showNames, { showNames = it; prefs.edit().putBoolean("recent_show_names", it).apply() }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("نمایش تعداد مخاطبان ذخیره‌شده", Modifier.weight(1f), color = CustomInk); Switch(showSavedCount, { showSavedCount = it; prefs.edit().putBoolean("recent_show_saved_count", it).apply() }) }
        Text("اندازهٔ تصویر مخاطب: ${avatarSize.toPersianDigits()} dp", color = CustomInk)
        Slider(avatarSize.toFloat(), { avatarSize = it.toInt(); prefs.edit().putInt("recent_avatar_size", avatarSize).apply() }, valueRange = 40f..112f, modifier = Modifier.fillMaxWidth(.8f))
        Text("گردی گوشهٔ تصویر: ${avatarRadius.toPersianDigits()} dp", color = CustomInk)
        Slider(avatarRadius.toFloat(), { avatarRadius = it.toInt(); prefs.edit().putInt("recent_avatar_radius", avatarRadius).apply() }, valueRange = 0f..50f, modifier = Modifier.fillMaxWidth(.8f))
        Text("اندازهٔ نام مخاطب: ${nameSize.toPersianDigits()} sp", color = CustomInk)
        Slider(nameSize.toFloat(), { nameSize = it.toInt(); prefs.edit().putInt("recent_name_size", nameSize).apply() }, valueRange = 11f..24f, modifier = Modifier.fillMaxWidth(.8f))
    }
}
