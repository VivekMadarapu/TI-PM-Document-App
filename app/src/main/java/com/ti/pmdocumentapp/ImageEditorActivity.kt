package com.ti.pmdocumentapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ja.burhanrashid52.photoeditor.OnSaveBitmap
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import yuku.ambilwarna.AmbilWarnaDialog

class ImageEditorActivity : AppCompatActivity() {
    private lateinit var photoEditor: PhotoEditor
    private lateinit var photoEditorView: PhotoEditorView
    private var imageUri: Uri? = null
    private var selectedTextColor = Color.WHITE
    private var selectedBrushColor = Color.BLACK
    private var selectedBrushSize = 10f
    private var selectedEraserSize = 10f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        photoEditorView = findViewById(R.id.photoEditorView)

        imageUri = intent.getParcelableExtra("imageUri")
        photoEditorView.source.setImageURI(imageUri)

        photoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true)
            .build()

        setupEditingTools()
    }

    private fun setupEditingTools() {
        findViewById<ImageButton>(R.id.btnBrush).setOnClickListener {
            showBrushDialog()
        }

        findViewById<ImageButton>(R.id.btnEraser).setOnClickListener {
            showEraserSizeDialog()
        }

        findViewById<ImageButton>(R.id.btnText).setOnClickListener {
            showAddTextDialog()
        }

        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener {
            photoEditor.undo()
        }

        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener {
            photoEditor.redo()
        }

        findViewById<ImageButton>(R.id.btnSave).setOnClickListener {
            saveImage()
        }
    }

    private fun showAddTextDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Text")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val input = EditText(this).apply {
            hint = "Enter your text"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        layout.addView(input)

        val colorPreview = View(this).apply {
            setBackgroundColor(selectedTextColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100
            ).apply {
                setMargins(0, 20, 0, 20)
            }
        }
        layout.addView(colorPreview)

        val colorPickerButton = Button(this).apply {
            text = "Choose Color"
            setOnClickListener {
                AmbilWarnaDialog(this@ImageEditorActivity, selectedTextColor,
                    object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                            selectedTextColor = color
                            colorPreview.setBackgroundColor(selectedTextColor)
                        }

                        override fun onCancel(dialog: AmbilWarnaDialog?) {
                            // No action on cancel
                        }
                    }).show()
            }
        }
        layout.addView(colorPickerButton)

        builder.setView(layout)

        builder.setPositiveButton("Add") { dialog, _ ->
            val userInputText = input.text.toString()
            if (userInputText.isNotEmpty()) {
                photoEditor.addText(userInputText, selectedTextColor)
            } else {
                Toast.makeText(this, "Text cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun showBrushDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Brush Settings")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val sizeSeekBar = SeekBar(this).apply {
            max = 50
            progress = selectedBrushSize.toInt()
        }
        val sizeLabel = TextView(this).apply {
            text = "Brush Size: ${selectedBrushSize.toInt()}"
            setPadding(0, 10, 0, 10)
        }
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedBrushSize = progress.toFloat()
                sizeLabel.text = "Brush Size: $progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        layout.addView(sizeLabel)
        layout.addView(sizeSeekBar)

        val colorPreview = View(this).apply {
            setBackgroundColor(selectedBrushColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100
            ).apply {
                setMargins(0, 20, 0, 20)
            }
        }
        layout.addView(colorPreview)

        val colorPickerButton = Button(this).apply {
            text = "Choose Color"
            setOnClickListener {
                AmbilWarnaDialog(this@ImageEditorActivity, selectedBrushColor,
                    object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                            selectedBrushColor = color
                            colorPreview.setBackgroundColor(selectedBrushColor)
                        }

                        override fun onCancel(dialog: AmbilWarnaDialog?) {
                            // No action on cancel
                        }
                    }).show()
            }
        }
        layout.addView(colorPickerButton)

        builder.setView(layout)

        builder.setPositiveButton("Apply") { dialog, _ ->
            photoEditor.setBrushDrawingMode(true)
            photoEditor.brushSize = selectedBrushSize
            photoEditor.brushColor = selectedBrushColor
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun showEraserSizeDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Adjust Eraser Size")

        // Create a vertical layout for the dialog
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        // Add a label for the current eraser size
        val sizeLabel = TextView(this).apply {
            text = "Eraser Size: ${selectedEraserSize.toInt()}"
            setPadding(0, 10, 0, 10)
        }
        layout.addView(sizeLabel)

        // Add a SeekBar for eraser size adjustment
        val sizeSeekBar = SeekBar(this).apply {
            max = 50 // Maximum eraser size
            progress = selectedEraserSize.toInt()
        }
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedEraserSize = progress.toFloat()
                sizeLabel.text = "Eraser Size: $progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        layout.addView(sizeSeekBar)

        builder.setView(layout)

        // Add "Apply" button
        builder.setPositiveButton("Apply") { dialog, _ ->
            // Apply the selected eraser size
            photoEditor.setBrushDrawingMode(true)
            photoEditor.brushSize = selectedEraserSize
            photoEditor.brushEraser()
            dialog.dismiss()
        }

        // Add "Cancel" button
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun saveImage() {
        photoEditor.saveAsBitmap(object : OnSaveBitmap {
            override fun onBitmapReady(saveBitmap: Bitmap) {
                overwriteImageInUri(saveBitmap, imageUri!!)
            }
        })
    }

    private fun overwriteImageInUri(bitmap: Bitmap, uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
            }
            Toast.makeText(this, "Image saved successfully", Toast.LENGTH_SHORT).show()

            val resultIntent = Intent().apply {
                putExtra("editedImageUri", uri)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}
