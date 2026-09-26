package icu.nullptr.hidemyapplist.ui.fragment

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.androidbroadcast.vbpd.viewBinding
import icu.nullptr.hidemyapplist.service.PrefManager
import icu.nullptr.hidemyapplist.service.ServiceClient
import icu.nullptr.hidemyapplist.ui.adapter.AppScopeAdapter
import icu.nullptr.hidemyapplist.ui.util.ThemeUtils.homeItemBackgroundColor
import icu.nullptr.hidemyapplist.ui.util.ThemeUtils.themeColor
import icu.nullptr.hidemyapplist.ui.util.navController
import icu.nullptr.hidemyapplist.ui.util.registerOnBackCallback
import icu.nullptr.hidemyapplist.ui.util.setEdge2EdgeFlags
import icu.nullptr.hidemyapplist.ui.util.setupToolbar
import icu.nullptr.hidemyapplist.ui.util.showToast
import icu.nullptr.hidemyapplist.util.PackageHelper
import icu.nullptr.hidemyapplist.util.SuUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.frknkrc44.hma_oss.R
import org.frknkrc44.hma_oss.databinding.FragmentAppSelectBinding

/**
 * "Invalidate inline hooks" screen.
 *
 * Lets the user pick apps and then wipes their compiled ART artifacts
 * ("cleanup libart.so"). The runtime regenerates a fresh image on next launch
 * so our inline hooks are re-applied from a clean state. This is a heavy,
 * one-shot maintenance action: nothing is persisted, and it is meant only for
 * apps that refuse to open because their hook state got corrupted.
 */
class InvalidateInlineHooksFragment : Fragment(R.layout.fragment_app_select) {

    private val binding by viewBinding(FragmentAppSelectBinding::bind)

    // Packages the user ticked. Not persisted on purpose (one-shot action).
    private val checked = mutableSetOf<String>()

    // Ticked apps float to the top of the list.
    private val firstComparator: Comparator<String> =
        Comparator.comparing { !checked.contains(it) }

    private val adapter by lazy {
        AppScopeAdapter(checked, hideMyself = true, firstFilter = null)
    }

    private var search = ""

    private fun applyFilter() = adapter.filter.filter(search)

    private fun sortList() {
        lifecycleScope.launch {
            PackageHelper.sortList(firstComparator)
            applyFilter()
        }
    }

    private fun onMenuOptionSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_apply -> {
                confirmAndRun()
                return
            }
            R.id.menu_show_system -> {
                item.isChecked = !item.isChecked
                PrefManager.appFilter_showSystem = item.isChecked
            }
            R.id.menu_sort_by_label -> {
                item.isChecked = true
                PrefManager.appFilter_sortMethod = PrefManager.SortMethod.BY_LABEL
            }
            R.id.menu_sort_by_package_name -> {
                item.isChecked = true
                PrefManager.appFilter_sortMethod = PrefManager.SortMethod.BY_PACKAGE_NAME
            }
            R.id.menu_sort_by_install_time -> {
                item.isChecked = true
                PrefManager.appFilter_sortMethod = PrefManager.SortMethod.BY_INSTALL_TIME
            }
            R.id.menu_sort_by_update_time -> {
                item.isChecked = true
                PrefManager.appFilter_sortMethod = PrefManager.SortMethod.BY_UPDATE_TIME
            }
            R.id.menu_reverse_order -> {
                item.isChecked = !item.isChecked
                PrefManager.appFilter_reverseOrder = item.isChecked
            }
            else -> return
        }

        sortList()
    }

    private fun confirmAndRun() {
        if (checked.isEmpty()) {
            showToast(R.string.invalidate_inline_hooks_no_selection)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_invalidate_inline_hooks)
            .setMessage(getString(R.string.invalidate_inline_hooks_confirm, checked.size))
            .setPositiveButton(R.string.yes) { _, _ -> runCleanup(checked.toList()) }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun runCleanup(packages: List<String>) {
        val progress = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_invalidate_inline_hooks)
            .setView(R.layout.dialog_loading)
            .setCancelable(false)
            .create()
        progress.show()

        lifecycleScope.launch {
            val failed = withContext(Dispatchers.IO) {
                val fails = mutableListOf<String>()
                for (pkg in packages) {
                    // Reset the app's compiled ART state so libart regenerates it and
                    // any stale inline hooks are dropped.
                    val ok = SuUtils.execPrivileged("pm compile --reset $pkg")

                    // Kill it so the clean image is loaded next time it starts.
                    if (ServiceClient.serviceVersion != 0) {
                        ServiceClient.forceStop(pkg)
                    } else {
                        SuUtils.execPrivileged("am force-stop $pkg")
                    }

                    if (!ok) fails.add(pkg)
                }
                fails
            }

            progress.dismiss()
            checked.clear()
            sortList()

            if (failed.isEmpty()) {
                showToast(R.string.invalidate_inline_hooks_done)
            } else {
                showToast(getString(R.string.invalidate_inline_hooks_partial, failed.size))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        registerOnBackCallback { navController.navigateUp() }

        setupToolbar(
            toolbar = binding.toolbar,
            title = getString(R.string.settings_invalidate_inline_hooks),
            navigationIcon = R.drawable.baseline_arrow_back_24,
            navigationOnClick = { navController.navigateUp() },
            menuRes = R.menu.menu_invalidate_inline_hooks,
            onMenuOptionSelected = this::onMenuOptionSelected
        )

        with(binding.toolbar.menu) {
            val searchView = findItem(R.id.menu_search).actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    search = newText
                    applyFilter()
                    return true
                }
            })

            findItem(R.id.menu_show_system).isChecked = PrefManager.appFilter_showSystem
            when (PrefManager.appFilter_sortMethod) {
                PrefManager.SortMethod.BY_LABEL -> findItem(R.id.menu_sort_by_label).isChecked = true
                PrefManager.SortMethod.BY_PACKAGE_NAME -> findItem(R.id.menu_sort_by_package_name).isChecked = true
                PrefManager.SortMethod.BY_INSTALL_TIME -> findItem(R.id.menu_sort_by_install_time).isChecked = true
                PrefManager.SortMethod.BY_UPDATE_TIME -> findItem(R.id.menu_sort_by_update_time).isChecked = true
            }
            findItem(R.id.menu_reverse_order).isChecked = PrefManager.appFilter_reverseOrder
        }

        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapter

        with(binding.swipeRefresh) {
            setProgressBackgroundColorSchemeColor(homeItemBackgroundColor(true))
            setColorSchemeColors(
                themeColor(androidx.appcompat.R.attr.colorPrimary),
                themeColor(com.google.android.material.R.attr.colorSecondary),
                themeColor(com.google.android.material.R.attr.colorTertiary),
            )

            setOnRefreshListener {
                PackageHelper.invalidateCache()
            }
        }

        adapter.registerAdapterDataObserver(
            EmptyDataObserver(binding.list, binding.listEmptyContainer.root)
        )

        lifecycleScope.launch {
            PackageHelper.isRefreshing
                .flowWithLifecycle(lifecycle)
                .collect { isRefreshing ->
                    binding.swipeRefresh.isRefreshing = isRefreshing
                    if (!isRefreshing) sortList()
                }
        }

        sortList()
        setEdge2EdgeFlags(binding.root)
    }

    // Credit: https://medium.com/nerd-for-tech/empty-dataset-in-recyclerview-ad86833dd5c6
    inner class EmptyDataObserver(
        private val recyclerView: RecyclerView,
        private val emptyView: View
    ) : RecyclerView.AdapterDataObserver() {
        private fun checkIfEmpty() {
            val emptyViewVisible = recyclerView.adapter!!.itemCount < 1
            emptyView.visibility = if (emptyViewVisible) View.VISIBLE else View.GONE
            if (emptyViewVisible) {
                emptyView.findViewById<TextView>(R.id.list_empty_text).text =
                    getString(R.string.list_empty_no_apps)
            }
            recyclerView.visibility = if (emptyViewVisible) View.GONE else View.VISIBLE
        }

        override fun onChanged() {
            super.onChanged()
            checkIfEmpty()
        }
    }
}
