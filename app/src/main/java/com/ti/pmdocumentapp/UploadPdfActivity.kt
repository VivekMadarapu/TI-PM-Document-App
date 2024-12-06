// UploadPdfActivity.kt
package com.ti.pmdocumentapp

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ti.pmdocumentapp.databinding.ActivityUploadPdfBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
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

            runOnUiThread {
                binding.progressBar.visibility = View.VISIBLE
                binding.buttonUploadPdf.isEnabled = false
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .build()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", file.name,
                    file.asRequestBody("application/pdf".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("http://192.168.2.102:5000/process")
                .post(requestBody)
                .build()

            thread {
                try {
                    val response = client.newCall(request).execute()
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonUploadPdf.isEnabled = true
                    }
                    if (response.isSuccessful) {
                        val responseBody = response.body?.byteStream()

                        if (responseBody != null) {
                            val zipInputStream = ZipInputStream(responseBody)
                            var zipEntry = zipInputStream.nextEntry
                            val receivedFiles = mutableListOf<File>()

                            while (zipEntry != null) {
                                val receivedFile = File(cacheDir, zipEntry.name)
                                FileOutputStream(receivedFile).use { outputStream ->
                                    val buffer = ByteArray(1024)
                                    var bytesRead: Int
                                    while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                                        outputStream.write(buffer, 0, bytesRead)
                                    }
                                }
                                receivedFiles.add(receivedFile)
                                zipEntry = zipInputStream.nextEntry
                            }
                            zipInputStream.closeEntry()
                            zipInputStream.close()

                            if (receivedFiles.size == 3) {
                                val intent = Intent(this, ViewPdfActivity::class.java).apply {
                                    putExtra("pdfReport", receivedFiles[0].absolutePath)
                                    putExtra("newPdf", receivedFiles[1].absolutePath)
                                    putExtra("checkSheet", receivedFiles[2].absolutePath)
                                }

                                runOnUiThread {
                                    startActivity(intent)
                                }
                            } else {
                                Log.e("UploadPdfActivity", "Received data does not contain three PDF files.")
                            }
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

                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonUploadPdf.isEnabled = true
                    }

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
