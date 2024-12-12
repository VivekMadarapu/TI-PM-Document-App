package com.ti.pmdocumentapp.ui.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ti.pmdocumentapp.SpeechTranscribeActivity
import com.ti.pmdocumentapp.databinding.FragmentDocumentsBinding
import java.io.File

class DocumentsFragment : Fragment() {

    private var _binding: FragmentDocumentsBinding? = null
    private val binding get() = _binding!!
    private var documentsViewModel: DocumentsViewModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        documentsViewModel = ViewModelProvider(this)[DocumentsViewModel::class.java]

        _binding = FragmentDocumentsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val recyclerView = binding.recyclerViewTranscripts
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        documentsViewModel?.transcriptFiles?.observe(viewLifecycleOwner) { files ->
            recyclerView.adapter = TranscriptAdapter(requireContext(), files)
        }

        loadTranscriptFiles()
        return root
    }

    private fun loadTranscriptFiles() {
        val subfolderName = "transcriptions"
        val subfolder = File(requireContext().filesDir, subfolderName)
        if (subfolder.exists()) {
            documentsViewModel?.loadTranscriptFiles(subfolder)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}