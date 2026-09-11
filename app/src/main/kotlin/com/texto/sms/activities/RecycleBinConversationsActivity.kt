package com.texto.sms.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import com.texto.sms.extensions.hideKeyboard
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import com.texto.sms.R
import com.texto.sms.adapters.RecycleBinConversationsAdapter
import com.texto.sms.databinding.ActivityRecycleBinConversationsBinding
import com.texto.sms.extensions.config
import com.texto.sms.extensions.conversationsDB
import com.texto.sms.extensions.emptyMessagesRecycleBin
import com.texto.sms.helpers.IS_RECYCLE_BIN
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.helpers.THREAD_TITLE
import com.texto.sms.helpers.textoConfirmDialog
import com.texto.sms.models.Conversation
import com.texto.sms.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class RecycleBinConversationsActivity : SimpleActivity() {
    private var bus: EventBus? = null
    private val binding by viewBinding(ActivityRecycleBinConversationsBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))

        loadRecycleBinConversations()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.recycleBinAppbar, NavigationIcon.Arrow)
        loadRecycleBinConversations()
        applyCustomColors()
        updateAppFonts(binding.root)
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun setupOptionsMenu() {
        binding.recycleBinToolbar.inflateMenu(R.menu.recycle_bin_menu)
        binding.recycleBinToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.empty_recycle_bin -> removeAll()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateOptionsMenu(conversations: ArrayList<Conversation>) {
        binding.recycleBinToolbar.menu.apply {
            findItem(R.id.empty_recycle_bin).isVisible = conversations.isNotEmpty()
        }
    }

    private fun loadRecycleBinConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getAllWithMessagesInRecycleBin()
                    .toMutableList() as ArrayList<Conversation>
            } catch (e: Exception) {
                ArrayList()
            }

            runOnUiThread {
                setupConversations(conversations)
            }
        }

        bus = EventBus.getDefault()
        try {
            bus!!.register(this)
        } catch (ignored: Exception) {
        }
    }

    private fun removeAll() {
        textoConfirmDialog(
            message = getString(R.string.empty_recycle_bin_messages_confirmation),
            isDestructive = true
        ) {
            ensureBackgroundThread {
                emptyMessagesRecycleBin()
                loadRecycleBinConversations()
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): RecycleBinConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = RecycleBinConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as RecycleBinConversationsAdapter
    }

    private fun setupConversations(conversations: ArrayList<Conversation>) {
        val sortedConversations = conversations.sortedWith(
            compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
                .thenByDescending { it.date }
        ).toMutableList() as ArrayList<Conversation>

        showOrHidePlaceholder(conversations.isEmpty())
        updateOptionsMenu(conversations)

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.setTextColor(config.mainTextColor)
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            putExtra(IS_RECYCLE_BIN, true)
            startActivity(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        loadRecycleBinConversations()
    }
}
