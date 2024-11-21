// UploadPdfActivity.kt
package com.ti.pmdocumentapp

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.ti.pmdocumentapp.databinding.ActivityUploadPdfBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class UploadPdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUploadPdfBinding
    private var selectedFileUri: Uri? = null
    private val pickPDFFile = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSelectPdf.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/pdf"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, pickPDFFile)
        }

        binding.buttonUploadPdf.setOnClickListener {
            selectedFileUri?.let {
                uploadPdf(it)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickPDFFile && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedFileUri = uri
                val fileName = getFileName(uri)
                binding.textviewSelectedFile.text = fileName
                binding.buttonUploadPdf.isEnabled = true
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor.use {
                if (it != null && it.moveToFirst()) {
                    result = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    private fun uploadPdf(fileUri: Uri) {
        try {
            val file = File(cacheDir, getFileName(fileUri) ?: "uploaded_file.pdf")
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", file.name,
                    file.asRequestBody("application/pdf".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("http://10.0.2.2:5000/upload")
                .post(requestBody)
                .build()

            thread {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: "No response body"

                        val intent = Intent(this, UploadResponseActivity::class.java).apply {
                            putExtra("response_message", "File uploaded successfully")
                            putExtra("response_details", responseBody)
                        }

                        runOnUiThread {
                            startActivity(intent)
                        }
                    } else {
                        Log.e("UploadPdfActivity", "File upload failed: ${response.message}")

                        val intent = Intent(this, UploadResponseActivity::class.java).apply {
                            putExtra("response_message", "File upload failed")
                            putExtra("response_details", response.message)
                        }

                        runOnUiThread {
                            startActivity(intent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UploadPdfActivity", "Error uploading file", e)

                    val intent = Intent(this, UploadResponseActivity::class.java).apply {
                        putExtra("response_message", "Error uploading file")
                        putExtra("response_details", e.message)
                    }

                    runOnUiThread {
                        startActivity(intent)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("UploadPdfActivity", "Error preparing file for upload", e)
        }
    }
}
