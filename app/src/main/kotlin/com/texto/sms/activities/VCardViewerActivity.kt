package com.texto.sms.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import ezvcard.VCard
import ezvcard.property.Email
import ezvcard.property.Telephone
import com.texto.sms.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.sendEmailIntent
import com.texto.sms.helpers.NavigationIcon
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.extensions.viewBinding
import com.texto.sms.R
import com.texto.sms.adapters.VCardViewerAdapter
import com.texto.sms.databinding.ActivityVcardViewerBinding
import com.texto.sms.extensions.dialNumber
import com.texto.sms.helpers.EXTRA_VCARD_URI
import com.texto.sms.helpers.parseVCardFromUri
import com.texto.sms.models.VCardPropertyWrapper
import com.texto.sms.models.VCardWrapper

class VCardViewerActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityVcardViewerBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.contactsList))
        setupMaterialScrollListener(binding.contactsList, binding.vcardAppbar)

        val vCardUri = intent.getParcelableExtra(EXTRA_VCARD_URI) as? Uri
        if (vCardUri != null) {
            setupOptionsMenu(vCardUri)
            parseVCardFromUri(this, vCardUri) {
                runOnUiThread {
                    setupContactsList(it)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTextoTopAppBar(binding.vcardAppbar, NavigationIcon.Arrow)
        applyCustomColors()
        updateAppFonts(binding.root)
    }

    private fun setupOptionsMenu(vCardUri: Uri) {
        binding.vcardToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_contact -> {
                    // getType() is a binder round trip into another app's provider, and it
                    // was being made straight from the click. Resolved off the main thread,
                    // with the launch handed back to it.
                    ensureBackgroundThread {
                        val mimetype = contentResolver.getType(vCardUri)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(vCardUri, mimetype?.lowercase())
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        }
                    }
                }

                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupContactsList(vCards: List<VCard>) {
        val items = prepareData(vCards)
        val adapter = VCardViewerAdapter(this, items.toMutableList()) { item ->
            val property = item as? VCardPropertyWrapper
            if (property != null) {
                handleClick(item)
            }
        }
        binding.contactsList.adapter = adapter
    }

    private fun handleClick(property: VCardPropertyWrapper) {
        when (property.property) {
            is Telephone -> dialNumber(property.value.normalizePhoneNumber())
            is Email -> sendEmailIntent(property.value)
        }
    }

    private fun prepareData(vCards: List<VCard>): List<VCardWrapper> {
        return vCards.map { vCard -> VCardWrapper.from(this, vCard) }
    }
}
