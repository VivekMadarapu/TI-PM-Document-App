package com.ti.pmdocumentapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.ti.pmdocumentapp.databinding.ActivityViewPdfBinding
import java.io.File

class ViewPdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewPdfBinding
    private var uploadedPdfUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonViewPdf.setOnClickListener {
            val pdfFile = getPdfFileFromStorage() ?: run {
                binding.textViewStatus.text = "No PDF file found in storage."
                return@setOnClickListener
            }
            viewPdf(pdfFile)
        }

        binding.buttonViewUploadedPdf.setOnClickListener {
            uploadedPdfUri?.let {
                viewUploadedPdf(it)
            } ?: run {
                binding.textViewStatus.text = "No uploaded PDF available to view."
            }
        }

        // Example: Receiving PDF URI from another activity (e.g., after uploading a PDF)
        val pdfUri = intent.getParcelableExtra<Uri>("uploadedPdfUri")
        if (pdfUri != null) {
            uploadedPdfUri = pdfUri
            binding.textViewStatus.text = "Uploaded PDF received."
        }
    }

    private fun getPdfFileFromStorage(): File? {
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return documentsDirectory.listFiles()?.firstOrNull { it.extension == "pdf" }
    }

    private fun viewPdf(pdfFile: File) {
        val pdfUri: Uri = FileProvider.getUriForFile(
            this,
            "com.ti.pmdocumentapp.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            binding.textViewStatus.text = "No application found to open PDF."
        }
    }

    private fun viewUploadedPdf(pdfUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            binding.textViewStatus.text = "No application found to open uploaded PDF."
        }
    }
}
