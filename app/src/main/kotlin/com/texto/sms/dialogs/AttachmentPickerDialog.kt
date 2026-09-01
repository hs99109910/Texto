package com.texto.sms.dialogs

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import org.fossify.commons.extensions.applyColorFilter
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.DialogAttachmentPickerBinding
import com.texto.sms.extensions.config
import com.texto.sms.helpers.TextoGlass

class AttachmentPickerDialog(
    private val onItemSelected: (Int) -> Unit
) : DialogFragment() {

    private var _binding: DialogAttachmentPickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.PickerTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAttachmentPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val items = mapOf(
            binding.pickerImage to R.id.picker_image,
            binding.pickerVideo to R.id.picker_video,
            binding.pickerCamera to R.id.picker_camera,
            binding.pickerCameraVideo to R.id.picker_camera_video,
            binding.pickerAudio to R.id.picker_audio,
            binding.pickerFile to R.id.picker_file,
            binding.pickerContact to R.id.picker_contact,
            binding.pickerSchedule to R.id.picker_schedule
        )

        items.forEach { (view, id) ->
            view.setOnClickListener {
                onItemSelected(id)
                dismiss()
            }
        }

        style()
    }

    /**
     * This sheet was the one surface that never joined the design: a hardcoded black slab with
     * white ink and the platform font, which read as a foreign dialog on a dark theme and as
     * an unreadable black rectangle once the light theme became the default. It is painted
     * from Config now, like every other surface, so it follows the skin and the tonality strip.
     */
    private fun style() {
        val activity = activity as? SimpleActivity ?: return
        val config = activity.config
        val density = resources.displayMetrics.density

        binding.root.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = config.cardCornerRadiusDp * density
                setColor(config.recentColor)
                setStroke(1, TextoGlass.rimFor(config.recentColor, 0.18f))
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        binding.pickerTitle.apply {
            setTextColor(config.mainTextColor)
            typeface = activity.typefaceFor(Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getScaledTextSize(1.15f))
        }

        // The glyphs carry the accent, so the sheet moves with the tonality strip alongside
        // the badges and chips it sits behind; the labels stay plain ink.
        items().forEach { row ->
            for (i in 0 until row.childCount) {
                when (val child = row.getChildAt(i)) {
                    is ImageView -> child.applyColorFilter(config.accentGradientStart)
                    is TextView -> {
                        child.setTextColor(config.mainTextColor)
                        child.typeface = activity.typefaceFor(Typeface.NORMAL)
                        child.setTextSize(
                            TypedValue.COMPLEX_UNIT_PX, activity.getScaledTextSize(0.8f)
                        )
                    }
                }
            }
        }
    }

    private fun items() = listOf(
        binding.pickerImage, binding.pickerVideo, binding.pickerCamera,
        binding.pickerCameraVideo, binding.pickerAudio, binding.pickerFile,
        binding.pickerContact, binding.pickerSchedule
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AttachmentPickerDialog"
    }
}
