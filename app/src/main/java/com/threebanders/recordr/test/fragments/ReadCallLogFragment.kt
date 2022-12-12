package com.threebanders.recordr.test.fragments

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.threebanders.recordr.R
import com.threebanders.recordr.common.Extras
import com.threebanders.recordr.test.PermissionActivity
import com.threebanders.recordr.viewmodels.MainViewModel
import kotlinx.coroutines.flow.combine

class ReadCallLogFragment : Fragment() {

    private lateinit var rootView : View
    private lateinit var permissionText : TextView
    private lateinit var allowNextBtn : Button
    private lateinit var mainViewModel: MainViewModel
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.permission_fragment_layout,container,false)
        permissionText = rootView.findViewById(R.id.permissionText)
        allowNextBtn = rootView.findViewById(R.id.allowNextBtn)
        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        permissionText.text = "Read Call Log Fragment"
        allowNextBtn.setOnClickListener {
            if(allowNextBtn.text.toString() == "Allow"){
                activityResultLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
            else if (allowNextBtn.text.toString() == "Next"){

                requireActivity()
                    .supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.container,mainViewModel.fragments.value!![Extras.getCurrentFragmentPosition(requireContext()) + 1])
                    .commit()

                Extras.addCurrentFragmentPosition(requireContext(),Extras.getCurrentFragmentPosition(requireContext()) + 1)
            }
        }
    }

    private var activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){ isGranted ->
        if(isGranted){
            allowNextBtn.text = "Next"
        } else {
            allowNextBtn.text = "Allow"
        }
    }
}