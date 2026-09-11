package com.texto.sms.adapters

import android.view.Menu
import com.texto.sms.extensions.notificationManager
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.views.TextoRecyclerView
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.deleteOrRecycleConversation
import com.texto.sms.extensions.updateConversationArchivedStatus
import com.texto.sms.helpers.refreshConversations
import com.texto.sms.helpers.textoConfirmDialog
import com.texto.sms.models.Conversation

class ArchivedConversationsAdapter(
    activity: SimpleActivity, recyclerView: TextoRecyclerView, onRefresh: () -> Unit, itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override fun getActionMenuId() = R.menu.cab_archived_conversations

    override fun prepareActionMode(menu: Menu) {}

    override fun getCustomActions(): List<Int> {
        // The bottom selection bar only has a fixed icon slot for cab_archive, not for
        // cab_unarchive, and actionItemPressed() below never handled cab_archive anyway --
        // so advertising it here just showed an "Archive" button that did nothing when
        // tapped. Unarchiving a multi-selection still works from the classic action bar
        // (cab_archived_conversations.xml); this only affects the modern selection pill.
        return listOf(
            R.id.cab_select_all,
            R.id.cab_delete
        )
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_unarchive -> unarchiveConversation()
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = org.fossify.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        (activity as SimpleActivity).textoConfirmDialog(question, isDestructive = true) {
            ensureBackgroundThread {
                deleteConversations()
            }
        }
    }

    private fun deleteConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.deleteOrRecycleConversation(it.threadId)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        removeConversationsFromList(conversationsToRemove)
    }

    private fun unarchiveConversation() {
        if (selectedKeys.isEmpty()) {
            return
        }

        ensureBackgroundThread {
            val conversationsToUnarchive = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
            conversationsToUnarchive.forEach {
                activity.updateConversationArchivedStatus(it.threadId, false)
            }

            removeConversationsFromList(conversationsToUnarchive)
        }
    }

    private fun removeConversationsFromList(removedConversations: List<Conversation>) {
        val newList = try {
            currentList.toMutableList().apply { removeAll(removedConversations) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }
}
