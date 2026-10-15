package com.rams.editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileTreeAdapter(
    private var nodes: List<FileNode>,
    private val onFileClick: (File) -> Unit,
    private val onFolderClick: (FileNode) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.treeFileName)
        val fileIcon: TextView = view.findViewById(R.id.treeFileIcon)
        val indentView: View = view.findViewById(R.id.treeIndent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.tree_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = nodes[position]
        holder.fileName.text = node.file.name

        // Set indent based on depth
        val params = holder.indentView.layoutParams
        params.width = node.depth * 24
        holder.indentView.layoutParams = params

        // Set icon based on type
        if (node.file.isDirectory) {
            holder.fileIcon.text = if (node.isExpanded) "▾" else "▸"
            holder.fileIcon.setTextColor(
                android.graphics.Color.parseColor("#E8C070")
            )
            holder.itemView.setOnClickListener {
                onFolderClick(node)
            }
        } else {
            holder.fileIcon.text = getFileIcon(node.file.name)
            holder.fileIcon.setTextColor(
                android.graphics.Color.parseColor("#7EC8E3")
            )
            holder.itemView.setOnClickListener {
                onFileClick(node.file)
            }
        }
    }

    override fun getItemCount() = nodes.size

    fun updateNodes(newNodes: List<FileNode>) {
        nodes = newNodes
        notifyDataSetChanged()
    }

    private fun getFileIcon(fileName: String): String {
        return when {
            fileName.endsWith(".html") -> "H"
            fileName.endsWith(".css") -> "C"
            fileName.endsWith(".js") -> "J"
            fileName.endsWith(".json") -> "{}"
            fileName.endsWith(".md") -> "M"
            fileName.endsWith(".kt") -> "K"
            fileName.endsWith(".py") -> "P"
            else -> "F"
        }
    }
}