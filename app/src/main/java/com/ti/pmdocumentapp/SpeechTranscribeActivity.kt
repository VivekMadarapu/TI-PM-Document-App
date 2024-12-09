package com.ti.pmdocumentapp

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.ti.pmdocumentapp.databinding.ActivitySpeechTranscribeBinding
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SpeechTranscribeActivity : AppCompatActivity() {

    private var fullTranscription: String = ""
    private var currentSection: Int = 1
    private var currentSubsection: Int? = null
    private var continuousSpeechRecognition = false

    private lateinit var binding: ActivitySpeechTranscribeBinding
    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 101
    private val REQUEST_IMAGE_CAPTURE = 102
    private lateinit var speechRecognizer: SpeechRecognizer
    private val transcribedItems = mutableListOf<View>()
    private val imageViewToBitmapMap = mutableMapOf<ImageView, Bitmap>()
    private val TAG = "SpeechTranscribeActivity"

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { addImageToLayout(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechTranscribeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))

        binding.buttonStartTranscription.setOnClickListener {
            if (checkPermission(Manifest.permission.RECORD_AUDIO)) {
                startSpeechRecognition()
            } else {
                checkAndRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }

        binding.buttonLoopTranscription.setOnClickListener {
            if (continuousSpeechRecognition) {
                binding.buttonLoopTranscription.backgroundTintList = ContextCompat.getColorStateList(this, R.color.purple_500)
                continuousSpeechRecognition = false
            } else {
                binding.buttonLoopTranscription.backgroundTintList = ContextCompat.getColorStateList(this, R.color.teal_200)
                continuousSpeechRecognition = true
            }
        }

        binding.buttonTakePicture.setOnClickListener {
            if (checkPermission(Manifest.permission.CAMERA)) {
                dispatchTakePictureIntent()
            } else {
                checkAndRequestPermissions(arrayOf(Manifest.permission.CAMERA))
            }
        }

        binding.buttonUndoTranscription.setOnClickListener {
            undoLastItem()
        }

        binding.buttonSavePdf.setOnClickListener {
            savePdfDocument()
        }

        binding.buttonSaveDocx.setOnClickListener {
            saveWordDocument()
        }

        binding.buttonSaveTxt.setOnClickListener {
            saveTxtFile()
        }

        initializeSpeechRecognizer()
    }

    inner class SpeechRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            binding.textViewTranscriptionStatus.text = "Listening..."
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            binding.textViewTranscriptionStatus.text = "Processing..."
        }

        override fun onError(error: Int) {
            val errorMessage = getErrorText(error)
            Log.e(TAG, "Recognition Error: $errorMessage")
            binding.textViewTranscriptionStatus.text = "Error occurred: $errorMessage. Please try again."
            if (continuousSpeechRecognition) {
                startSpeechRecognition()
            } else {
                binding.buttonStartTranscription.isEnabled = true
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcribedText = matches?.get(0)?.let { processTranscribedText(it) } ?: ""
            if (transcribedText.split(" ")[0] != "action:") {
                addTextToLayout(transcribedText)
                binding.textViewTranscriptionStatus.text = "Transcription Recorded"
            }
            else {
                binding.textViewTranscriptionStatus.text = "Action Executed: ${transcribedText.split(":")[1].trim()}"
            }
            binding.textViewCurrentText.text = ""
            if (continuousSpeechRecognition) {
                startSpeechRecognition()
            } else {
                binding.buttonStartTranscription.isEnabled = true
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialMatch = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = partialMatch?.get(0) ?: ""
            binding.textViewCurrentText.text = partialText
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(SpeechRecognitionListener())
    }

    private fun startSpeechRecognition() {
        binding.buttonStartTranscription.isEnabled = false // Disable the button while recording
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    private fun processTranscribedText(text: String): String {
        return when {
            text.contains("create subsection", ignoreCase = true) -> {
                currentSubsection = 1
                currentSection--
                "action: create subsection"
            }
            text.contains("complete subsection", ignoreCase = true) -> {
                currentSubsection = null
                currentSection++
                "action: complete subsection"
            }
            text.contains("undo", ignoreCase = true) -> {
                undoLastItem()
                "action: undo"
            }
            text.contains("capture image", ignoreCase = true) -> {
                if (checkPermission(Manifest.permission.CAMERA)) {
                    dispatchTakePictureIntent()
                } else {
                    checkAndRequestPermissions(arrayOf(Manifest.permission.CAMERA))
                }
                "action: capture image"
            }
            text.contains("save pdf", ignoreCase = true) -> {
                savePdfDocument()
                "action: save pdf"
            }
            text.contains("save word", ignoreCase = true) -> {
                saveWordDocument()
                "action: save word"
            }
            text.contains("save text", ignoreCase = true) -> {
                saveTxtFile()
                "action: save text"
            }
            currentSubsection != null -> {
                val subsectionNumber = currentSubsection!!
                currentSubsection = currentSubsection!! + 1
                "    $currentSection.$subsectionNumber $text"
            }
            else -> {
                val sectionText = "$currentSection.0 $text"
                currentSection++
                sectionText
            }
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermissions(permissions: Array<String>) {
        val permissionsToRequest = permissions.filter { !checkPermission(it) }.toTypedArray()
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_RECORD_AUDIO)
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE_SECURE)
        try {
            takePictureLauncher.launch(takePictureIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addTextToLayout(text: String) {
        val editText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 18f
            setPadding(0, 16, 0, 16)
            this.setText(text)
        }
        binding.linearLayoutCapturedPhotos.addView(editText)
        transcribedItems.add(editText)
        fullTranscription += "\n$text"
    }

    private fun addImageToLayout(image: Bitmap) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            setImageBitmap(image)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        binding.linearLayoutCapturedPhotos.addView(imageView)
        transcribedItems.add(imageView)
        imageViewToBitmapMap[imageView] = image
    }

    private fun undoLastItem() {
        if (transcribedItems.isNotEmpty()) {
            val lastView = transcribedItems.removeAt(transcribedItems.size - 1)
            binding.linearLayoutCapturedPhotos.removeView(lastView)
            if (lastView is EditText) {
                if (currentSubsection != null) {
                    if (currentSubsection!! > 1) {
                        currentSubsection = currentSubsection!! - 1
                    } else {
                        currentSubsection = null
                        currentSection--
                    }
                }
                else {
                    currentSection--
                }
                fullTranscription = fullTranscription.substringBeforeLast('\n')
            }
        }
    }

    private fun savePdfDocument() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDirectory.exists()) {
            documentsDirectory.mkdirs()
        }
        val pdfPath = File(documentsDirectory, "transcription_$currentDateTime.pdf")
        val pdfWriter = PdfWriter(FileOutputStream(pdfPath))
        val pdfDocument = com.itextpdf.kernel.pdf.PdfDocument(pdfWriter)
        val document = Document(pdfDocument)

        try {
            val header = """
                Module:
                Specification Name: 
                Description: 
            """.trimIndent()
            document.add(Paragraph(header).setBold())
            document.add(Paragraph("\n"))
            for (i in 0 until transcribedItems.size) {
                when (val view = transcribedItems[i]) {
                    is TextView -> {
                        val paragraph = Paragraph(view.text.toString())
                        if (view.text.startsWith("    ")) {
                            paragraph.setMarginLeft(20f)
                        }
                        document.add(paragraph)
                    }
                    is ImageView -> {
                        val bitmap = imageViewToBitmapMap[view]
                        bitmap?.let {
                            val imageFile = File.createTempFile("image_${System.currentTimeMillis()}", ".png", cacheDir)
                            FileOutputStream(imageFile).use { outputStream ->
                                it.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            }

                            val imageData = ImageDataFactory.create(imageFile.absolutePath)
                            val image = Image(imageData)
                            document.add(image)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating PDF: ${e.message}", e)
            Toast.makeText(this, "Error creating PDF", Toast.LENGTH_SHORT).show()
        } finally {
            document.close()
        }

        Toast.makeText(this, "PDF saved to ${pdfPath.absolutePath}", Toast.LENGTH_SHORT).show()
    }

    private fun saveWordDocument() {
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val docxFile = File(documentsDirectory, "transcription_${System.currentTimeMillis()}.docx")

        val document = XWPFDocument()
        try {
            val header = """
                Module:
                Specification Name: 
                Description: 
            """.trimIndent()
            val headerParagraph = document.createParagraph()
            val headerRun = headerParagraph.createRun()
            headerRun.setText(header)
            for (i in 0 until transcribedItems.size) {
                when (val view = transcribedItems[i]) {
                    is TextView -> {
                        val paragraph: XWPFParagraph = document.createParagraph()
                        val run: XWPFRun = paragraph.createRun()
                        run.setText(view.text.toString())
                    }
                    is ImageView -> {
                        val bitmap = imageViewToBitmapMap[view]
                        bitmap?.let {
                            val imageFile = File(cacheDir, "image_${System.currentTimeMillis()}.png")
                            FileOutputStream(imageFile).use { fos ->
                                it.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            }

                            val imageInputStream = FileInputStream(imageFile)
                            val pictureType = XWPFDocument.PICTURE_TYPE_JPEG
                            val pictureParagraph = document.createParagraph()
                            val run: XWPFRun = pictureParagraph.createRun()
                            run.addPicture(
                                imageInputStream,
                                pictureType,
                                imageFile.name,
                                Units.toEMU(400.0),
                                Units.toEMU(400.0)
                            )
                        }
                    }
                }
            }

            FileOutputStream(docxFile).use { outputStream ->
                document.write(outputStream)
            }
            Toast.makeText(this, "DOCX file saved to ${docxFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save DOCX file", Toast.LENGTH_SHORT).show()
        } finally {
            try {
                document.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun saveTxtFile() {
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val txtFile = File(documentsDirectory, "transcription_${System.currentTimeMillis()}.txt")

        try {
            FileWriter(txtFile).use { writer ->
                val header = """
                    Module:
                    Specification Name: 
                    Description: 
                """.trimIndent()
                writer.write(header + "\n\n")
                for (i in 0 until transcribedItems.size) {
                    when (val view = transcribedItems[i]) {
                        is TextView -> {
                            writer.write(view.text.toString() + "\n")
                        }
                    }
                }
            }
            Toast.makeText(this, "TXT file saved to ${txtFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save TXT file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                PERMISSIONS_REQUEST_RECORD_AUDIO -> startSpeechRecognition()
                REQUEST_IMAGE_CAPTURE -> dispatchTakePictureIntent()
            }
        } else {
            val feature = if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) "Audio" else "Camera"
            Toast.makeText(this, "$feature permission is required to use this feature", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
    }
}
