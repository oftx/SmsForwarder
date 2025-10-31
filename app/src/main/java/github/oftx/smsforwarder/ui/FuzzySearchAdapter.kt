package github.oftx.smsforwarder.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import java.util.Locale

class FuzzySearchAdapter(
    context: Context,
    resource: Int,
    private val allItems: List<String>
) : ArrayAdapter<String>(context, resource, ArrayList(allItems)), Filterable {

    private val fullList = ArrayList(allItems)

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val suggestions = ArrayList<String>()

                if (constraint.isNullOrBlank()) {
                    // No filter, return the full list
                    suggestions.addAll(fullList)
                } else {
                    val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
                    for (item in fullList) {
                        // The core logic change: use 'contains' instead of 'startsWith'
                        if (item.lowercase(Locale.getDefault()).contains(filterPattern)) {
                            suggestions.add(item)
                        }
                    }
                }

                results.values = suggestions
                results.count = suggestions.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                clear()
                if (results != null && results.count > 0) {
                    // Add the filtered results to the adapter's list
                    addAll(results.values as ArrayList<String>)
                }
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return resultValue as String
            }
        }
    }
}
