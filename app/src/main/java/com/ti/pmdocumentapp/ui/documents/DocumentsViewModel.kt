package com.ti.pmdocumentapp.ui.documents

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File

class DocumentsViewModel : ViewModel() {

    private val _transcriptFiles = MutableLiveData<List<File>>()
    val transcriptFiles: LiveData<List<File>> = _transcriptFiles

    fun loadTranscriptFiles(directory: File) {
        val files = directory.listFiles()?.filter { it.isFile } ?: emptyList()
        _transcriptFiles.value = files
    }
}