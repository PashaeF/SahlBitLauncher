package ir.sahlbit.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private fun String.toPersianDigitsText(): String = map { ch ->
    when (ch) {
        in '0'..'9' -> ('۰'.code + ch.code - '0'.code).toChar()
        in '٠'..'٩' -> ('۰'.code + ch.code - '٠'.code).toChar()
        else -> ch
    }
}.joinToString("")

private fun String.toEnglishDigitsText(): String = map { ch ->
    when (ch) {
        in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
        in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
        else -> ch
    }
}.joinToString("")

fun saveSquarePhotoFromUri(context: Context, uri: Uri, prefix: String = "sahlbit_photo_"): String? = runCatching {
    val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("تصویر قابل خواندن نیست")
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.preRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.preRotate(270f); matrix.postScale(-1f, 1f) }
    }
    val oriented = if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    val side = minOf(oriented.width, oriented.height)
    val square = Bitmap.createBitmap(oriented, (oriented.width - side) / 2, (oriented.height - side) / 2, side, side)
    val file = File(context.filesDir, "${prefix}${UUID.randomUUID()}.jpg")
    file.outputStream().use { output -> square.compress(Bitmap.CompressFormat.JPEG, 92, output) }
    require(file.exists() && file.length() > 0)
    Uri.fromFile(file).toString()
}.getOrNull()

fun saveSquarePhoto(context: Context, source: Bitmap): String? = runCatching {
    val side = minOf(source.width, source.height)
    val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val file = File(context.filesDir, "sahlbit_photo_${UUID.randomUUID()}.jpg")
    file.outputStream().use { output -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, output) }
    require(file.exists() && file.length() > 0)
    Uri.fromFile(file).toString()
}.getOrNull()

data class CustomContactPhone(
    val id: String = UUID.randomUUID().toString(),
    val number: String,
    val photoUri: String? = null,
    val contactUri: String? = null,
    val label: String = ""
)
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
                            add(CustomContactPhone(
                                id = phone.optString("id", UUID.randomUUID().toString()),
                                number = phone.optString("number"),
                                photoUri = phone.optString("photo").takeIf(String::isNotBlank),
                                contactUri = phone.optString("contactUri").takeIf(String::isNotBlank),
                                label = phone.optString("label")
                            ))
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
                    contact.phones.forEachIndexed { phoneIndex, phone -> put(JSONObject().apply {
                        put("id", phone.id); put("number", phone.number); put("photo", phone.photoUri ?: ""); put("contactUri", phone.contactUri ?: ""); put("label", phone.label)
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
    var photoRefreshRevision by remember { mutableIntStateOf(0) }
    val hostActivity = context as? ComponentActivity
    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) photoRefreshRevision++
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }

    fun cropOptions() = CropImageOptions().apply {
        fixAspectRatio = true
        aspectRatioX = 1
        aspectRatioY = 1
        guidelines = CropImageView.Guidelines.ON
        cropShape = CropImageView.CropShape.RECTANGLE
    }
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val index = photoTarget
        if (result.isSuccessful && index in phones.indices) {
            val saved = result.uriContent?.let { saveSquarePhotoFromUri(context, it) }
            if (saved != null) phones = phones.toMutableList().also {
                it[index] = it[index].copy(photoUri = saved, contactUri = null)
            } else message = "ذخیرهٔ تصویر برش‌خورده ناموفق بود."
        } else if (result.error != null) {
            message = "برش تصویر انجام نشد؛ لطفاً دوباره تلاش کنید."
        }
        photoTarget = -1
    }
    fun launchCrop(uri: Uri) { cropLauncher.launch(CropImageContractOptions(uri, cropOptions())) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && photoTarget in phones.indices) launchCrop(uri) else photoTarget = -1
    }
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraOutputUri
        if (success && uri != null && photoTarget in phones.indices) launchCrop(uri)
        else {
            photoTarget = -1
            message = "گرفتن عکس با دوربین انجام نشد."
        }
        cameraOutputUri = null
    }
    fun launchCameraCapture() {
        runCatching {
            val file = File(context.cacheDir, "sahlbit_camera_crop_${UUID.randomUUID()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            camera.launch(cameraOutputUri!!)
        }.onFailure {
            cameraOutputUri = null
            photoTarget = -1
            message = "باز کردن دوربین ممکن نشد."
        }
    }

    val androidContactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0).orEmpty()
                    val contactId = cursor.getLong(1)
                    val importedPhones = runCatching {
                        context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId.toString()), null
                        )?.use { phoneCursor ->
                            buildList {
                                while (phoneCursor.moveToNext()) {
                                    val rowId = phoneCursor.getLong(0)
                                    val number = phoneCursor.getString(1).orEmpty().trim()
                                    val photo = phoneCursor.getString(2)
                                    if (number.isNotBlank()) {
                                        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI.buildUpon().appendPath(rowId.toString()).build().toString()
                                        add(CustomContactPhone(number = number, photoUri = photo, contactUri = phoneUri))
                                    }
                                }
                            }.distinctBy { it.number.filter(Char::isDigit) }
                        }
                    }.getOrNull().orEmpty()
                    if (importedPhones.isNotEmpty()) {
                        phones = importedPhones
                        message = "مخاطب و همهٔ شماره‌های ثبت‌شده وارد شدند. تصاویر مخاطبین اندروید به‌صورت زنده خوانده می‌شوند و در حافظهٔ سهل‌بیت کپی نمی‌شوند."
                    } else {
                        val selectedPhone = context.contentResolver.query(
                            uri,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
                            null, null, null
                        )?.use { selectedCursor ->
                            if (selectedCursor.moveToFirst()) {
                                val number = selectedCursor.getString(0).orEmpty().trim()
                                val photo = selectedCursor.getString(1)
                                if (number.isNotBlank()) CustomContactPhone(number = number, photoUri = photo, contactUri = uri.toString()) else null
                            } else null
                        }
                        if (selectedPhone != null) {
                            phones = listOf(selectedPhone)
                            message = "این مخاطب با یک شماره وارد شد. برای واردکردن همهٔ شماره‌ها، اجازهٔ دسترسی به مخاطبان را فعال کنید."
                        } else message = "برای این مخاطب شمارهٔ تلفنی ثبت نشده است."
                    }
                }
            }
        }.onFailure { message = "خواندن مخاطب از برنامهٔ مخاطبین اندروید ناموفق بود." }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) androidContactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        else {
            message = "بدون این اجازه، فقط شماره‌ای که در انتخابگر سیستم برمی‌گزینید وارد می‌شود."
            androidContactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
    }
    val phonePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val index = photoTarget
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null && index in phones.indices) {
            val photo = runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (!photo.isNullOrBlank()) {
                phones = phones.toMutableList().also { it[index] = it[index].copy(photoUri = photo, contactUri = uri.toString()) }
                message = "تصویر مخاطب اندروید برای این شماره انتخاب شد."
            } else message = "برای مخاطب انتخاب‌شده تصویری ثبت نشده است."
        }
        photoTarget = -1
    }
    val phonePhotoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) phonePhotoPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        else message = "برای انتخاب تصویر از برنامه مخاطبین اندروید، دسترسی مخاطبان لازم است."
    }

    Column(Modifier.fillMaxSize().background(CustomCream).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = CustomBlue) }
            Text("مخاطبین اختصاصی", color = CustomBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text("مخاطب اختصاصی بسازید، چند شماره برای او ثبت کنید و برای هر شماره تصویر جداگانه در نظر بگیرید. اطلاعات این بخش در حافظهٔ داخلی سهل‌بیت نگهداری می‌شود.", color = CustomInk, fontSize = 14.sp, lineHeight = 24.sp)
        OutlinedButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                androidContactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            } else {
                android.widget.Toast.makeText(context, "در اندروید ۵ اجازهٔ مخاطبان هنگام نصب درخواست می‌شود. در صورت ندادن اجازه، فقط شمارهٔ انتخاب‌شده وارد خواهد شد.", android.widget.Toast.LENGTH_LONG).show()
                androidContactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text("افزودن از برنامهٔ مخاطبین اندروید")
        }
        Text(if (editingId == null) "مخاطب جدید" else "ویرایش مخاطب", color = CustomInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام مخاطب") }, singleLine = true, trailingIcon = { if (name.isNotEmpty()) IconButton(onClick = { name = "" }) { Icon(Icons.Default.Close, "پاک‌کردن نام", tint = CustomBlue) } })
        phones.forEachIndexed { index, phone ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("شمارهٔ ${(index + 1).toPersianDigits()}", color = CustomInk, fontWeight = FontWeight.SemiBold)
                    val phoneBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, phone.photoUri, photoRefreshRevision) { value = phone.photoUri?.let { raw -> withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(raw))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } } }
                    val phoneBitmapValue = phoneBitmap
                    if (phoneBitmapValue != null) Image(phoneBitmapValue, contentDescription = "تصویر شماره", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                    OutlinedTextField(value = phone.label, onValueChange = { value -> phones = phones.toMutableList().also { it[index] = it[index].copy(label = value.take(24)) } }, modifier = Modifier.fillMaxWidth(), label = { Text("عنوان شمارهٔ ${(index + 1).toPersianDigits()}") }, placeholder = { Text("مثلاً همراه، منزل یا محل کار") }, singleLine = true, trailingIcon = { if (phone.label.isNotEmpty()) IconButton(onClick = { phones = phones.toMutableList().also { it[index] = it[index].copy(label = "") } }) { Icon(Icons.Default.Close, "پاک‌کردن عنوان شماره", tint = CustomBlue) } })
                    OutlinedTextField(value = phone.number.toPersianDigitsText(), onValueChange = { value -> phones = phones.toMutableList().also { it[index] = it[index].copy(number = value.toPersianDigitsText()) } }, modifier = Modifier.fillMaxWidth(), label = { Text("شماره تماس") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { photoTarget = index; gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("گالری", fontSize = 12.sp) }
                        OutlinedButton(onClick = { photoTarget = index; launchCameraCapture() }, modifier = Modifier.weight(1f)) { Text("دوربین", fontSize = 12.sp) }
                        OutlinedButton(onClick = {
                            photoTarget = index
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) phonePhotoPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) phonePhotoPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            else phonePhotoPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        }, modifier = Modifier.weight(1f)) { Text("مخاطبین", fontSize = 12.sp) }
                        IconButton(onClick = {
                            phones = if (phones.size > 1) phones.filterIndexed { i, _ -> i != index }
                            else phones.toMutableList().also { it[index] = it[index].copy(number = "", label = "", photoUri = null, contactUri = null) }
                        }) { Icon(Icons.Default.Delete, "پاک‌کردن اطلاعات شماره", tint = Color(0xFFB3261E)) }
                    }
                }
            }
        }
        OutlinedButton(onClick = { phones = phones + CustomContactPhone(number = "") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("افزودن شمارهٔ دیگر") }
        Button(onClick = {
            val cleanName = name.trim()
            val cleanPhones = phones.map { it.copy(number = it.number.trim(), label = it.label.trim()) }.filter { it.number.isNotBlank() }
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
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(contact.name, color = CustomInk, fontWeight = FontWeight.Bold)
                    contact.phones.forEachIndexed { phoneIndex, phone ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val phoneBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, phone.photoUri, photoRefreshRevision) { value = phone.photoUri?.let { raw -> withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(raw))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } } }
                            val phoneBitmapValue = phoneBitmap
                            if (phoneBitmapValue != null) Image(phoneBitmapValue, contentDescription = "تصویر ${phone.number}", modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(phone.label.ifBlank { "شمارهٔ ${(phoneIndex + 1).toPersianDigits()}" }, color = CustomInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(phone.number.toPersianDigitsText(), color = Color(0xFF64748B), fontSize = 13.sp)
                            }
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
    fun cropOptions() = CropImageOptions().apply {
        fixAspectRatio = true
        aspectRatioX = 1
        aspectRatioY = 1
        guidelines = CropImageView.Guidelines.ON
        cropShape = CropImageView.CropShape.RECTANGLE
    }
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful && result.uriContent != null) {
            onPhotoSelected(saveSquarePhotoFromUri(context, result.uriContent!!))
        } else if (result.error != null) {
            android.widget.Toast.makeText(context, "برش تصویر انجام نشد؛ لطفاً دوباره تلاش کنید.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    fun launchCrop(uri: Uri) { cropLauncher.launch(CropImageContractOptions(uri, cropOptions())) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) launchCrop(uri) }
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraOutputUri
        if (success && uri != null) launchCrop(uri)
        cameraOutputUri = null
    }
    fun launchCameraCapture() {
        runCatching {
            val file = File(context.cacheDir, "sahlbit_preview_camera_${UUID.randomUUID()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            camera.launch(cameraOutputUri!!)
        }.onFailure {
            cameraOutputUri = null
            android.widget.Toast.makeText(context, "باز کردن دوربین ممکن نشد.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val contactPhoto = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            val source = runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (!source.isNullOrBlank()) onPhotoSelected(source)
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
                OutlinedButton(onClick = { expanded = false; launchCameraCapture() }, modifier = Modifier.fillMaxWidth()) { Text("گرفتن عکس با دوربین") }
                OutlinedButton(onClick = { expanded = false; contactPhoto.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, modifier = Modifier.fillMaxWidth()) { Text("استفاده از عکس مخاطب در برنامهٔ مخاطبین اندروید") }
                if (photoUri != null) TextButton(onClick = { expanded = false; onPhotoSelected(null) }, modifier = Modifier.fillMaxWidth()) { Text("حذف تصویر انتخاب‌شده") }
            }
        },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("بستن") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerPage(onBack: () -> Unit, onCall: (String) -> Unit, initialNumber: String = "", showHeader: Boolean = true) {
    var number by remember(initialNumber) { mutableStateOf(initialNumber.toPersianDigitsText()) }
    val keys = listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "*", "۰", "#")
    val backspaceInteraction = remember { MutableInteractionSource() }
    val backspacePressed by backspaceInteraction.collectIsPressedAsState()
    val latestBackspace by rememberUpdatedState { if (number.isNotEmpty()) number = number.dropLast(1) }
    LaunchedEffect(backspacePressed) {
        if (backspacePressed) {
            latestBackspace()
            delay(420)
            while (backspacePressed) { latestBackspace(); delay(160) }
        }
    }
    Column(Modifier.fillMaxSize().background(if (showHeader) CustomCream else Color.Transparent).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showHeader) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = CustomBlue) }
            Text("شماره‌گیر اختصاصی", color = CustomBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = number,
            onValueChange = { number = it.toPersianDigitsText() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
            shape = RoundedCornerShape(20.dp),
            textStyle = TextStyle(fontSize = 26.sp, textAlign = TextAlign.Center),
            label = { Text("شماره تماس") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(Modifier.weight(1f))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth().weight(1.35f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            val keyAction: Modifier = if (key == "۰") {
                                Modifier.combinedClickable(onClick = { number += "۰" }, onLongClick = { number += "+" })
                            } else Modifier.clickable { number += key }
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color.White)
                                    .then(keyAction).padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(key, fontSize = 28.sp, color = CustomInk, fontWeight = FontWeight.SemiBold)
                                    if (key == "۰") Text("+", fontSize = 12.sp, color = CustomBlue, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .clickable(interactionSource = backspaceInteraction, indication = null) {},
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⌫", color = CustomBlue, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("پاک کردن", color = CustomInk, fontSize = 16.sp)
                }
            }
            Button(onClick = { if (number.isNotBlank()) onCall(number.toEnglishDigitsText()) }, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f))
                    Text("تماس", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun RecentCallsCustomizationPage(prefs: android.content.SharedPreferences, onBack: () -> Unit) {
    var showPhotos by remember { mutableStateOf(prefs.getBoolean("recent_show_photos", true)) }
    var photoSource by remember { mutableStateOf(prefs.getString("recent_photo_source", "custom") ?: "custom") }
    var avatarSize by remember { mutableIntStateOf(prefs.getInt("recent_avatar_size", 64).coerceIn(40, 112)) }
    var avatarRadius by remember { mutableIntStateOf(prefs.getInt("recent_avatar_radius", 20).coerceIn(0, 50)) }
    var showNames by remember { mutableStateOf(prefs.getBoolean("recent_show_names", true)) }
    var nameSource by remember { mutableStateOf(prefs.getString("recent_name_source", "custom") ?: "custom") }
    var showAndroidUnknown by remember { mutableStateOf(prefs.getBoolean("recent_show_android_unknown", false)) }
    var showNumbers by remember { mutableStateOf(prefs.getBoolean("recent_show_numbers", true)) }
    var nameSize by remember { mutableIntStateOf(prefs.getInt("recent_name_size", 15).coerceIn(11, 24)) }
    var showStatusIcon by remember { mutableStateOf(prefs.getBoolean("recent_show_status_icon", true)) }
    var showStatusText by remember { mutableStateOf(prefs.getBoolean("recent_show_status_text", false)) }
    var statusIconSize by remember { mutableIntStateOf(prefs.getInt("recent_status_icon_size", 24).coerceIn(16, 48)) }
    var buttonMode by remember { mutableStateOf(prefs.getString("recent_call_button_mode", "both") ?: "both") }
    var callIconSize by remember { mutableIntStateOf(prefs.getInt("recent_call_icon_size", 26).coerceIn(16, 48)) }
    var callTextSize by remember { mutableIntStateOf(prefs.getInt("recent_call_text_size", 14).coerceIn(10, 24)) }
    var cardSpacing by remember { mutableIntStateOf(prefs.getInt("recent_card_spacing", 12).coerceIn(4, 30)) }
    var showSavedCount by remember { mutableStateOf(prefs.getBoolean("recent_show_saved_count", false)) }
    fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    @Composable fun RadioChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onSelect).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Text(label, color = CustomInk, modifier = Modifier.weight(1f), lineHeight = 22.sp)
        }
    }
    @Composable fun SwitchChoice(label: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = CustomInk, fontWeight = FontWeight.SemiBold)
                if (description != null) Text(description, color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 19.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
    Column(Modifier.fillMaxSize().background(CustomCream).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = CustomBlue) }
            Text("سفارشی‌سازی صفحه تماس‌های اخیر", color = CustomBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text("اجزای هر کادر تماس، منبع نام و تصویر، وضعیت تماس و ظاهر دکمه تماس را تنظیم کنید. تغییرها بلافاصله ذخیره می‌شوند.", color = CustomInk, lineHeight = 24.sp)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تصویر مخاطب", color = CustomBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                SwitchChoice("نمایش تصویر مخاطب", showPhotos, { showPhotos = it; saveBoolean("recent_show_photos", it) }, "اگر خاموش باشد، ستون تصویر حذف می‌شود و فضای آن به بخش اطلاعات تماس می‌رسد.")
                if (showPhotos) {
                    Text("منبع تصویر و اولویت نمایش", color = CustomInk, fontWeight = FontWeight.SemiBold)
                    RadioChoice("فقط تصویر مخاطبین اختصاصی سهل‌بیت (پیش‌فرض)", photoSource == "custom") { photoSource = "custom"; saveString("recent_photo_source", photoSource) }
                    RadioChoice("فقط تصویر مخاطبین اندروید", photoSource == "android") { photoSource = "android"; saveString("recent_photo_source", photoSource) }
                    RadioChoice("هر دو؛ ابتدا تصویر مخاطب اختصاصی سهل‌بیت", photoSource == "both_custom_first") { photoSource = "both_custom_first"; saveString("recent_photo_source", photoSource) }
                    RadioChoice("هر دو؛ ابتدا تصویر مخاطبین اندروید", photoSource == "both_android_first") { photoSource = "both_android_first"; saveString("recent_photo_source", photoSource) }
                    Text("اندازه تصویر مخاطب: ${avatarSize.toPersianDigits()} dp", color = CustomInk, fontWeight = FontWeight.SemiBold)
                    Slider(avatarSize.toFloat(), { avatarSize = it.toInt().coerceIn(40, 112); saveInt("recent_avatar_size", avatarSize) }, valueRange = 40f..112f)
                    Text("گردی گوشه تصویر: ${avatarRadius.toPersianDigits()} dp", color = CustomInk, fontWeight = FontWeight.SemiBold)
                    Slider(avatarRadius.toFloat(), { avatarRadius = it.toInt().coerceIn(0, 50); saveInt("recent_avatar_radius", avatarRadius) }, valueRange = 0f..50f)
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نام و شماره تماس", color = CustomBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                SwitchChoice("نمایش نام مخاطب", showNames, { showNames = it; saveBoolean("recent_show_names", it) })
                if (showNames) {
                    Text("منبع نام مخاطب و اولویت نمایش", color = CustomInk, fontWeight = FontWeight.SemiBold)
                    RadioChoice("نام مخاطبین اختصاصی سهل‌بیت (پیش‌فرض)", nameSource == "custom") { nameSource = "custom"; saveString("recent_name_source", nameSource) }
                    RadioChoice("نام مخاطبین اندروید", nameSource == "android") { nameSource = "android"; saveString("recent_name_source", nameSource) }
                    RadioChoice("هر دو؛ ابتدا نام مخاطب اختصاصی سهل‌بیت", nameSource == "custom_first") { nameSource = "custom_first"; saveString("recent_name_source", nameSource) }
                    RadioChoice("هر دو؛ ابتدا نام مخاطبین اندروید", nameSource == "android_first") { nameSource = "android_first"; saveString("recent_name_source", nameSource) }
                    SwitchChoice("نمایش نام و تصویر مخاطبان اندروید که در مخاطبین اختصاصی سهل‌بیت ثبت نشده‌اند", showAndroidUnknown, { showAndroidUnknown = it; saveBoolean("recent_show_android_unknown", it) }, "اگر خاموش باشد، برای این شماره‌ها فقط شماره تماس نمایش داده می‌شود.")
                    Text("اندازه نام مخاطب: ${nameSize.toPersianDigits()} sp", color = CustomInk, fontWeight = FontWeight.SemiBold)
                    Slider(nameSize.toFloat(), { nameSize = it.toInt().coerceIn(11, 24); saveInt("recent_name_size", nameSize) }, valueRange = 11f..24f)
                }
                SwitchChoice("نمایش شماره تماس", showNumbers, { showNumbers = it; saveBoolean("recent_show_numbers", it) })
                SwitchChoice("نمایش تعداد مخاطبان ذخیره‌شده", showSavedCount, { showSavedCount = it; saveBoolean("recent_show_saved_count", it) })
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("وضعیت تماس", color = CustomBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                SwitchChoice("نمایش علامت وضعیت تماس", showStatusIcon, { showStatusIcon = it; saveBoolean("recent_show_status_icon", it) })
                SwitchChoice("نمایش نوشتار فارسی وضعیت تماس", showStatusText, { showStatusText = it; saveBoolean("recent_show_status_text", it) })
                Text("اندازه علامت وضعیت تماس: ${statusIconSize.toPersianDigits()} dp", color = CustomInk, fontWeight = FontWeight.SemiBold)
                Slider(statusIconSize.toFloat(), { statusIconSize = it.toInt().coerceIn(16, 48); saveInt("recent_status_icon_size", statusIconSize) }, valueRange = 16f..48f)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("دکمه تماس و فاصله کادرها", color = CustomBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("نحوه نمایش دکمه تماس", color = CustomInk, fontWeight = FontWeight.SemiBold)
                RadioChoice("فقط آیکون تماس", buttonMode == "icon") { buttonMode = "icon"; saveString("recent_call_button_mode", buttonMode) }
                RadioChoice("فقط نوشته تماس", buttonMode == "text") { buttonMode = "text"; saveString("recent_call_button_mode", buttonMode) }
                RadioChoice("آیکون و نوشته تماس", buttonMode == "both") { buttonMode = "both"; saveString("recent_call_button_mode", buttonMode) }
                Text("اندازه آیکون تماس: ${callIconSize.toPersianDigits()} dp", color = CustomInk, fontWeight = FontWeight.SemiBold)
                Slider(callIconSize.toFloat(), { callIconSize = it.toInt().coerceIn(16, 48); saveInt("recent_call_icon_size", callIconSize) }, valueRange = 16f..48f)
                Text("اندازه نوشته تماس: ${callTextSize.toPersianDigits()} sp", color = CustomInk, fontWeight = FontWeight.SemiBold)
                Slider(callTextSize.toFloat(), { callTextSize = it.toInt().coerceIn(10, 24); saveInt("recent_call_text_size", callTextSize) }, valueRange = 10f..24f)
                Text("فاصله بین کادرهای تماس: ${cardSpacing.toPersianDigits()} dp", color = CustomInk, fontWeight = FontWeight.SemiBold)
                Slider(cardSpacing.toFloat(), { cardSpacing = it.toInt().coerceIn(4, 30); saveInt("recent_card_spacing", cardSpacing) }, valueRange = 4f..30f)
            }
        }
    }
}
