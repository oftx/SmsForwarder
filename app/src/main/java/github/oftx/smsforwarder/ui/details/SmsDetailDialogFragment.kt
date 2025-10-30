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
        
        setupRecyclerView()
        observeViewModel()

        binding.btnOk.setOnClickListener {
            dismiss()
        }
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
                    binding.tvDetailSender.text = getString(R.string.detail_sender) + " " + it.sender
                    // Pass context to get the user-defined time format
                    binding.tvDetailTimestamp.text = getString(R.string.detail_received_at) + " " + TimeUtil.getAbsoluteTime(requireContext(), it.timestamp)
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
