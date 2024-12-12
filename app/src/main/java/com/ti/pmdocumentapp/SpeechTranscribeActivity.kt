package com.ti.pmdocumentapp

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
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
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.gson.Gson
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.io.source.ByteArrayOutputStream
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.ti.pmdocumentapp.databinding.ActivitySpeechTranscribeBinding
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.ByteArrayInputStream
import java.io.File
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
    private val imageViewToFileMap = mutableMapOf<ImageView, Uri>()
    private var photoUri: Uri = Uri.EMPTY
    private var videoUri: Uri = Uri.EMPTY
    private var videoName: String = ""
    private val TAG = "SpeechTranscribeActivity"

    data class TranscriptionItem(
        val type: String,
        val content: Array<String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TranscriptionItem

            if (type != other.type) return false
            if (!content.contentEquals(other.content)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + content.contentHashCode()
            return result
        }
    }

    data class TranscriptionState(
        val items: List<TranscriptionItem>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechTranscribeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadTranscriptionState()

        checkAndRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))

        binding.buttonSaveTranscription.setOnClickListener {
            saveTranscriptionState()
        }

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

        binding.buttonTakeVideo.setOnClickListener {
            if(checkPermission(Manifest.permission.CAMERA)) {
                openVideoCapture()
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

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            launchImageEditor(photoUri)
            addImageToLayout(photoUri)
        }
    }

    private val editImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val editedUri = result.data?.getParcelableExtra<Uri>("editedImageUri")
            if (editedUri != null) {
                photoUri = editedUri
            }
        }
    }

    private val videoCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (videoUri != Uri.EMPTY){
                Toast.makeText(this, "Video saved to: $videoUri", Toast.LENGTH_SHORT).show()
                addVideoToLayout(videoUri, videoName)
            } else {
                Toast.makeText(this, "Failed to save video", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openVideoCapture() {
        val videoCaptureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "video_${timestamp}.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PMDocumentVideos")
            }
            videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)!!
            videoName = "video_${timestamp}.mp4"
            videoCaptureIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            videoCaptureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            videoCaptureLauncher.launch(videoCaptureIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVideoInPlayer(videoUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(videoUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No video player app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchImageEditor(uri: Uri) {
        val editIntent = Intent(this, ImageEditorActivity::class.java).apply {
            putExtra("imageUri", photoUri)
        }
        editImageLauncher.launch(editIntent)
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timestamp}_", ".jpg", storageDir).apply {
            photoUri = FileProvider.getUriForFile(this@SpeechTranscribeActivity, "${packageName}.provider", this)
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            createImageFile()
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            takePictureLauncher.launch(takePictureIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun correctImageOrientation(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream) // Decode to Bitmap

                contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )

                    val rotationAngle = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }

                    if (rotationAngle != 0f) {
                        val matrix = Matrix()
                        matrix.postRotate(rotationAngle)
                        Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                    } else {
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
        binding.linearLayoutCapturedContent.addView(editText)
        transcribedItems.add(editText)
        fullTranscription += "\n$text"
    }

    private fun addImageToLayout(uri: Uri) {
        val imageView = ImageView(this).apply {
            var bitmap = correctImageOrientation(uri)
            if (bitmap == null)
                bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))

            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.MATRIX
            val matrix = Matrix()

            val targetWidth = 400f
            val targetHeight = 400f

            val scaleX = targetWidth / bitmap!!.width
            val scaleY = targetHeight / bitmap.height

            val scale = minOf(scaleX, scaleY)

            matrix.setScale(scale, scale)

            val dx = (targetWidth - bitmap.width * scale) / 2
            val dy = (targetHeight - bitmap.height * scale) / 2
            matrix.postTranslate(dx, dy)

            imageMatrix = matrix
            layoutParams = LinearLayout.LayoutParams(
                targetWidth.toInt(),
                targetHeight.toInt()
            ).apply {
                setMargins(16, 16, 0, 16)
            }
        }

        binding.linearLayoutCapturedContent.addView(imageView)
        transcribedItems.add(imageView)
        imageViewToFileMap[imageView] = uri
    }

    private fun addVideoToLayout(videoUri: Uri, videoName: String) {
        val textView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 16f
            setPadding(0, 16, 0, 16)
            text = videoName
            tag = videoUri
            setTextColor(ContextCompat.getColor(this@SpeechTranscribeActivity, R.color.purple_200))
            setOnClickListener {
                openVideoInPlayer(videoUri)
            }
        }
        binding.linearLayoutCapturedContent.addView(textView)
        transcribedItems.add(textView)
    }


    private fun undoLastItem() {
        if (transcribedItems.isNotEmpty()) {
            val lastView = transcribedItems.removeAt(transcribedItems.size - 1)
            binding.linearLayoutCapturedContent.removeView(lastView)
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

    private fun saveTranscriptionState() {
        val title = binding.editTextTranscriptName.text.toString()
        val subfolderName = "transcriptions"
        val subfolder = File(filesDir, subfolderName)
        if (!subfolder.exists()) {
            subfolder.mkdirs()
        }
        val file = File(subfolder, "${title}.json")

        val items = transcribedItems.mapNotNull { item ->
            when (item) {
                is EditText -> TranscriptionItem("text", arrayOf(item.text.toString()))
                is ImageView -> {
                    val uri = imageViewToFileMap[item]
                    TranscriptionItem("image", arrayOf(uri.toString()))
                }
                is TextView -> TranscriptionItem("video", arrayOf(item.text.toString(), item.tag.toString()))
                else -> null
            }
        }

        val transcriptionState = TranscriptionState(items)

        try {
            val jsonString = Gson().toJson(transcriptionState)
            file.writeText(jsonString)

            Toast.makeText(this, "Transcription saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save transcription: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun loadTranscriptionState() {
        val transcriptionName = intent.getStringExtra("transcriptionName") ?: ""
        if (transcriptionName.isNotEmpty()){
            binding.editTextTranscriptName.setText(transcriptionName)
        }
        if(binding.editTextTranscriptName.text.isEmpty()) {
            return
        }
        val title = binding.editTextTranscriptName.text.toString()
        val subfolderName = "transcriptions"
        val subfolder = File(filesDir, subfolderName)
        if (!subfolder.exists()) {
            subfolder.mkdirs()
        }
        val file = File(subfolder, "${title}.json")

        if (!file.exists()) {
            return
        }

        try {
            val jsonString = file.readText()
            val transcriptionState = Gson().fromJson(jsonString, TranscriptionState::class.java)

            binding.linearLayoutCapturedContent.removeAllViews()
            fullTranscription = ""
            transcribedItems.clear()

            transcriptionState.items.forEach { item ->
                when (item.type) {
                    "text" -> {
                        addTextToLayout(item.content[0])
                    }
                    "image" -> {
                        val uri = Uri.parse(item.content[0])
                        addImageToLayout(uri)
                    }
                    "video" -> {
                        val uri = Uri.parse(item.content[1])
                        addVideoToLayout(uri, item.content[0])
                    }
                }
            }

            Toast.makeText(this, "Transcription restored successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No saved transcription found", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun savePdfDocument() {
        val title = binding.editTextTranscriptName.text.toString().replace(" ", "_")
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDirectory.exists()) {
            documentsDirectory.mkdirs()
        }
        val pdfPath = File(documentsDirectory, "transcription_${title}_$currentDateTime.pdf")
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
                        val uri = imageViewToFileMap[view]
                        uri?.let {
                            val correctedBitmap = correctImageOrientation(uri)
                            correctedBitmap?.let { bitmap ->
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                val imageData = ImageDataFactory.create(stream.toByteArray())
                                val image = Image(imageData)

                                image.scaleToFit(400f, 400f)
                                image.setMargins(10f, 10f, 10f, 10f)

                                document.add(image)
                            }
                        }
                        Log.d("ImageViewMapping", "View: $view -> Uri: ${imageViewToFileMap[view]}")
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
        val title = binding.editTextTranscriptName.text.toString().replace(" ", "_")
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val docxFile = File(documentsDirectory, "transcription_${title}_${currentDateTime}.docx")

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
                        val uri = imageViewToFileMap[view]
                        uri?.let {
                            val correctedBitmap = correctImageOrientation(uri)
                            correctedBitmap?.let { bitmap ->
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                val imageData = stream.toByteArray()
                                val imageInputStream = ByteArrayInputStream(imageData)
                                val run = document.createParagraph().createRun()
                                run.addPicture(
                                    imageInputStream,
                                    XWPFDocument.PICTURE_TYPE_JPEG,
                                    uri.lastPathSegment,
                                    Units.toEMU(400.0),
                                    Units.toEMU(400.0)
                                )
                            }
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
        val title = binding.editTextTranscriptName.text.toString().replace(" ", "_")
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val txtFile = File(documentsDirectory, "transcription_${title}_${currentDateTime}.txt")

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
