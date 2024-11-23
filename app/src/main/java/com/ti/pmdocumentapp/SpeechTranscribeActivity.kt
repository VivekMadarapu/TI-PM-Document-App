package com.ti.pmdocumentapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ti.pmdocumentapp.databinding.ActivitySpeechTranscribeBinding
import java.util.*

class SpeechTranscribeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpeechTranscribeBinding
    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 101
    private lateinit var speechRecognizer: SpeechRecognizer
    private val TAG = "SpeechToTextActivity"
    private var transcript = "";

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechTranscribeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!checkAudioPermission()) {
            requestAudioPermission()
        }

        binding.buttonStartTranscription.setOnClickListener {
            if (checkAudioPermission()) {
                startSpeechRecognition()
            } else {
                requestAudioPermission()
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.textViewTranscriptionStatus.text = "Listening..."
            }

            override fun onBeginningOfSpeech() {
            }

            override fun onRmsChanged(rmsdB: Float) {
            }

            override fun onBufferReceived(buffer: ByteArray?) {
            }

            override fun onEndOfSpeech() {
                binding.textViewTranscriptionStatus.text = "Status: Processing..."
            }

            override fun onError(error: Int) {
                // Log the error type to debug the issue
                val errorMessage = getErrorText(error)
                Log.e(TAG, "Recognition Error: $errorMessage")
                binding.textViewTranscriptionStatus.text = "Error occurred: $errorMessage. Please try again."
                binding.buttonStartTranscription.isEnabled = true
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcribedText = matches?.get(0) ?: "No speech detected"
                transcript += transcribedText + "\n"
                binding.textViewTranscriptionStatus.text = "Status: Recorded"
                binding.textViewTranscribedText.text = transcript;
                binding.buttonStartTranscription.isEnabled = true
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialMatch = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = partialMatch?.get(0) ?: ""
                binding.textViewCurrentText.text = partialText
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                TODO("Not yet implemented")
            }
        })
    }

    private fun startSpeechRecognition() {
        binding.buttonStartTranscription.isEnabled = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "Audio permission is required to use this feature", Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSIONS_REQUEST_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Audio permission is required to use this feature", Toast.LENGTH_SHORT).show()
            }
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