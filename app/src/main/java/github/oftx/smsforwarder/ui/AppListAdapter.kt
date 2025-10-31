package github.oftx.smsforwarder.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import github.oftx.smsforwarder.databinding.ItemAppListBinding
import java.util.Locale

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

class AppListAdapter(
    private var appList: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>(), Filterable {

    private var filteredAppList: List<AppInfo> = appList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(filteredAppList[position])
    }

    override fun getItemCount(): Int = filteredAppList.size

    inner class AppViewHolder(private val binding: ItemAppListBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(filteredAppList[bindingAdapterPosition])
                }
            }
        }

        fun bind(appInfo: AppInfo) {
            binding.ivAppIcon.setImageDrawable(appInfo.icon)
            binding.tvAppName.text = appInfo.name
            binding.tvPackageName.text = appInfo.packageName
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint.toString().lowercase(Locale.getDefault())
                val results = FilterResults()
                results.values = if (query.isEmpty()) {
                    appList
                } else {
                    appList.filter {
                        it.name.lowercase(Locale.getDefault()).contains(query) ||
                        it.packageName.lowercase(Locale.getDefault()).contains(query)
                    }
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredAppList = results?.values as? List<AppInfo> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}
