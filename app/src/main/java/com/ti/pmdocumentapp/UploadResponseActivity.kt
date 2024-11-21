package com.ti.pmdocumentapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ti.pmdocumentapp.databinding.ActivityUploadResponseBinding

class UploadResponseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUploadResponseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUploadResponseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val responseMessage = intent.getStringExtra("response_message")
        val responseDetails = intent.getStringExtra("response_details")

        binding.textviewResponseMessage.text = responseMessage ?: "No response message available"
        binding.textviewResponseDetails.text = responseDetails ?: "No details available"
    }
}
