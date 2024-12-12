package com.ti.pmdocumentapp.ui.documents

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ti.pmdocumentapp.SpeechTranscribeActivity
import com.ti.pmdocumentapp.R
import java.io.File

class TranscriptAdapter(
    private val context: Context,
    private val files: List<File>
) : RecyclerView.Adapter<TranscriptAdapter.TranscriptViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranscriptViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_transcript, parent, false)
        return TranscriptViewHolder(view)
    }

    override fun onBindViewHolder(holder: TranscriptViewHolder, position: Int) {
        val file = files[position]
        holder.transcriptName.text = file.name.split(".")[0]
        holder.itemView.setOnClickListener {
            val transcriptionName = file.name.split(".")[0]
            val intent = Intent(context, SpeechTranscribeActivity::class.java).apply {
                putExtra("transcriptionName", transcriptionName)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = files.size

    class TranscriptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val transcriptName: TextView = itemView.findViewById(R.id.text_transcript_name)
    }
}
