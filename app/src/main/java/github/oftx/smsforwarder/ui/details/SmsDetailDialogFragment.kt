package github.oftx.smsforwarder.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.DialogSmsDetailsBinding
import github.oftx.smsforwarder.ui.TimeUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SmsDetailDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "SmsDetailDialog"
        private const val ARG_SMS_ID = "arg_sms_id"

        fun newInstance(smsId: Long): SmsDetailDialogFragment {
            return SmsDetailDialogFragment().apply {
                arguments = bundleOf(ARG_SMS_ID to smsId)
            }
        }
    }

    private var _binding: DialogSmsDetailsBinding? = null
    private val binding get() = _binding!!

    private val smsId by lazy { requireArguments().getLong(ARG_SMS_ID) }
    private val viewModel: SmsDetailViewModel by viewModels {
        SmsDetailViewModelFactory(requireActivity().application, smsId)
    }

    private lateinit var statusAdapter: ForwardingStatusAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSmsDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupBottomSheet()
        setupRecyclerView()
        observeViewModel()

        binding.btnOk.setOnClickListener {
            dismiss()
        }
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as? com.google.android.material.bottomsheet.BottomSheetDialog
            val bottomSheet = bottomSheetDialog?.findViewById<android.widget.FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.let { sheet ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)

                // Check if in landscape mode
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                if (isLandscape) {
                    behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }

                // Fix for black bar: Make the background extend behind the navigation bar
                // sheet.background = null // REMOVED: This caused the background to disappear. 
                // The default background of the bottom sheet should be used.
                
                // Handle WindowInsets for edge-to-edge
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                    val navigationBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                    
                    // Apply padding to the button or a container, NOT the sheet itself, to allow background to extend
                    // But here we might need to adjust the sheet's padding if the background is set on the sheet.
                    // The issue "black bar" usually comes from the window background or the container not extending.
                    
                    // Let's try a clean approach:
                    // 1. Ensure the sheet itself has no bottom padding that clips content.
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
                    
                    // 2. Add bottom margin/padding to the OK button so it clears the nav bar
                    val params = binding.btnOk.layoutParams as? ViewGroup.MarginLayoutParams
                    params?.let {
                        it.bottomMargin = 24.dpToPx() + navigationBars.bottom
                        binding.btnOk.layoutParams = it
                    }
                    
                    insets
                }
            }
        }

        // Configure the window for edge-to-edge
        dialog?.window?.let { window ->
            // Make navigation bar transparent
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            // Allow drawing behind system bars
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                // For older versions, ensure flags are set correctly if WindowCompat doesn't handle it fully (it usually does for the fitsSystemWindows part, but let's be safe)
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupRecyclerView() {
        statusAdapter = ForwardingStatusAdapter(
            onRetryClicked = { item ->
                viewModel.retryJob(item.job)
                Toast.makeText(requireContext(), getString(R.string.job_retry_toast, item.ruleName), Toast.LENGTH_SHORT).show()
            },
            onCancelClicked = { item ->
                viewModel.cancelJob(item.job.id)
                Toast.makeText(requireContext(), getString(R.string.job_cancelled_toast, item.ruleName), Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvForwardingStatus.adapter = statusAdapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                state.sms?.let {
                    binding.tvDetailSender.text = getString(R.string.detail_sender, it.sender)
                    // Pass context to get the user-defined time format
                    binding.tvDetailTimestamp.text = getString(R.string.detail_received_at, TimeUtil.getAbsoluteTime(requireContext(), it.timestamp))
                    binding.tvDetailContent.text = it.content
                }
                statusAdapter.submitList(state.jobs)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
