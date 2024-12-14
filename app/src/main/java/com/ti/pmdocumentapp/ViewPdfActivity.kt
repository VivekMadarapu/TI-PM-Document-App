package com.ti.pmdocumentapp

import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ti.pmdocumentapp.databinding.ActivityViewPdfBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ViewPdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewPdfBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pdfFilePath = intent.getStringExtra("newPdf")
        val pdfReportFilePath = intent.getStringExtra("pdfReport")
        val checkSheetFilePath = intent.getStringExtra("checkSheet")
        val dateFolderName = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(java.util.Date())

        var downloadSuccessful: Boolean

        if (pdfFilePath != null) {
            val file = File(pdfFilePath)
            if (file.exists()) {
                downloadSuccessful = downloadPdf(file, dateFolderName)
            } else {
                binding.textViewStatus.text = "PDF file not found."
                downloadSuccessful = false
            }
        } else {
            binding.textViewStatus.text = "No PDF file provided."
            downloadSuccessful = false
        }

        if (pdfReportFilePath != null) {
            val file = File(pdfReportFilePath)
            if (file.exists()) {
                downloadSuccessful = downloadPdf(file, dateFolderName) && downloadSuccessful
            } else {
                binding.textViewStatus.text = "PDF report file not found."
                downloadSuccessful = false
            }
        } else {
            binding.textViewStatus.text = "No PDF report file provided."
            downloadSuccessful = false
        }

        if (checkSheetFilePath != null) {
            val file = File(checkSheetFilePath)
            if (file.exists()) {
                downloadSuccessful = downloadPdf(file, dateFolderName) && downloadSuccessful
            } else {
                binding.textViewStatus.text = "Check sheet file not found."
                downloadSuccessful = false
            }
        } else {
            binding.textViewStatus.text = "No check sheet file provided."
            downloadSuccessful = false
        }

        if (downloadSuccessful) {
            showCheckMark()
        }
        else {
            showErrorMark()
        }

    }

    private fun downloadPdf(pdfFile: File, dateFolderName: String): Boolean {
        val downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val subFolder = File(downloadsDirectory, "PM Document Data")
        if (!subFolder.exists()) {
            subFolder.mkdirs()
        }

        val dateSubFolder = File(subFolder, dateFolderName)
        if (!dateSubFolder.exists()) {
            dateSubFolder.mkdirs()
        }

        val destinationFile = File(dateSubFolder, pdfFile.name)

        return try {
            pdfFile.inputStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, "File downloaded to: ${destinationFile.absolutePath}", Toast.LENGTH_LONG).show()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to download file", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun showCheckMark() {
        val checkMark = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@ViewPdfActivity, android.R.drawable.checkbox_on_background))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = binding.root.layoutParams.apply {
                width = resources.displayMetrics.widthPixels/2
                height = resources.displayMetrics.heightPixels/2
            }
        }
        binding.root.addView(checkMark)
    }

    private fun showErrorMark() {
        val errorMark = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@ViewPdfActivity, android.R.drawable.ic_delete))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = binding.root.layoutParams.apply {
                width = resources.displayMetrics.widthPixels / 2
                height = resources.displayMetrics.widthPixels / 2
            }
        }
        binding.root.addView(errorMark)
    }
}
