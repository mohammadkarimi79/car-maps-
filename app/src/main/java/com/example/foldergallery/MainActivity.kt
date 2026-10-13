package com.example.foldergallery

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private data class Folder(val id: String, val parentId: String?, val name: String)
private data class Picture(val id: String, val folderId: String?, val filename: String)

class MainActivity : AppCompatActivity() {
    private val folders = mutableListOf<Folder>()
    private val pictures = mutableListOf<Picture>()
    private var currentFolderId: String? = null
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importPicture(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadIndex()
        findViewById<Button>(R.id.addFolderButton).setOnClickListener { askFolderName() }
        findViewById<Button>(R.id.addImagesButton).setOnClickListener { picker.launch("image/*") }
        render()
    }

    private fun indexFile() = File(filesDir, "index.json")

    private fun loadIndex() {
        try {
            val f = indexFile()
            if (!f.exists()) return
            val obj = JSONObject(f.readText())
            val fa = obj.optJSONArray("folders") ?: JSONArray()
            for (i in 0 until fa.length()) {
                val x = fa.getJSONObject(i)
                folders.add(Folder(x.getString("id"), x.optString("parentId").takeUnless { it == "null" || it.isEmpty() }, x.getString("name")))
            }
            val pa = obj.optJSONArray("pictures") ?: JSONArray()
            for (i in 0 until pa.length()) {
                val x = pa.getJSONObject(i)
                val file = x.getString("filename")
                if (File(filesDir, "images/$file").exists()) pictures.add(Picture(x.getString("id"), x.optString("folderId").takeUnless { it == "null" || it.isEmpty() }, file))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "فهرست ذخیره‌شده خوانده نشد؛ اطلاعات تازه ذخیره می‌شود", Toast.LENGTH_LONG).show()
            folders.clear(); pictures.clear()
        }
    }

    private fun saveIndex() {
        val root = JSONObject()
        val fa = JSONArray()
        folders.forEach { f -> fa.put(JSONObject().put("id", f.id).put("parentId", f.parentId).put("name", f.name)) }
        val pa = JSONArray()
        pictures.forEach { p -> pa.put(JSONObject().put("id", p.id).put("folderId", p.folderId).put("filename", p.filename)) }
        root.put("folders", fa).put("pictures", pa)
        indexFile().writeText(root.toString())
    }

    private fun askFolderName() {
        val input = EditText(this).apply { hint = "مثلاً سفر شمال"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("ساخت پوشه").setMessage("نام پوشه را وارد کنید").setView(input)
            .setNegativeButton("لغو", null).setPositiveButton("ساخت") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) { toast("نام پوشه نمی‌تواند خالی باشد"); return@setPositiveButton }
                if (folders.any { it.parentId == currentFolderId && it.name.equals(name, true) }) { toast("پوشه‌ای با این نام در همین مسیر وجود دارد"); return@setPositiveButton }
                folders.add(Folder(UUID.randomUUID().toString(), currentFolderId, name))
                saveIndex(); render()
            }.show()
    }

    private fun importPicture(uri: Uri) {
        try {
            val dir = File(filesDir, "images").apply { mkdirs() }
            val filename = "${UUID.randomUUID()}.${extensionFor(contentResolver, uri)}"
            val target = File(dir, filename)
            contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                ?: throw IllegalStateException("تصویر قابل خواندن نیست")
            pictures.add(Picture(UUID.randomUUID().toString(), currentFolderId, filename))
            saveIndex(); render(); toast("عکس به این پوشه اضافه شد")
        } catch (_: Exception) { toast("افزودن عکس انجام نشد؛ دوباره تلاش کنید") }
    }

    private fun extensionFor(resolver: ContentResolver, uri: Uri): String {
        val mime = resolver.getType(uri) ?: "image/jpeg"
        return when (mime.lowercase()) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; "image/heic" -> "heic"; else -> "jpg" }
    }

    private fun render() {
        val foldersBox = findViewById<LinearLayout>(R.id.foldersContainer)
        val imagesBox = findViewById<GridLayout>(R.id.imagesContainer)
        foldersBox.removeAllViews(); imagesBox.removeAllViews()
        val folderItems = folders.filter { it.parentId == currentFolderId }
        val pictureItems = pictures.filter { it.folderId == currentFolderId }
        val path = if (currentFolderId == null) "پوشه‌های من" else "پوشه‌های من  /  ${folderName(currentFolderId)}"
        findViewById<TextView>(R.id.pathText).text = path
        findViewById<TextView>(R.id.emptyText).visibility = if (folderItems.isEmpty() && pictureItems.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.foldersHeader).visibility = if (folderItems.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.imagesHeader).visibility = if (pictureItems.isEmpty()) View.GONE else View.VISIBLE
        folderItems.forEach { folder ->
            val row = TextView(this).apply {
                text = "📁  ${folder.name}"
                textSize = 17f; setTextColor(Color.rgb(41,70,62)); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15)); setBackgroundColor(Color.WHITE)
                setOnClickListener { currentFolderId = folder.id; render() }
            }
            val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
            foldersBox.addView(row, lp)
        }
        val screenWidth = resources.displayMetrics.widthPixels
        val tile = ((screenWidth - dp(56)) / 3).coerceAtLeast(dp(80))
        pictureItems.forEach { picture ->
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(225,225,220))
                val file = File(filesDir, "images/${picture.filename}")
                setImageURI(Uri.fromFile(file))
                contentDescription = "نمایش عکس"
                setOnClickListener { startActivity(Intent(this@MainActivity, ImageViewerActivity::class.java).putExtra("imagePath", file.absolutePath)) }
            }
            val lp = GridLayout.LayoutParams().apply { width = tile; height = tile; setMargins(dp(3), dp(3), dp(3), dp(3)) }
            imagesBox.addView(image, lp)
        }
    }

    private fun folderName(id: String?) = folders.firstOrNull { it.id == id }?.name ?: "پوشه"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    @Deprecated("Use system back dispatcher")
    override fun onBackPressed() {
        if (currentFolderId != null) {
            currentFolderId = folders.firstOrNull { it.id == currentFolderId }?.parentId
            render()
        } else super.onBackPressed()
    }
}
