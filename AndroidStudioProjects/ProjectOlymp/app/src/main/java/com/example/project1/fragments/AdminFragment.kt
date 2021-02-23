package com.example.project1.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.project1.R
import com.example.project1.databinding.FragmentAdminBinding
import com.example.project1.repositories.DataBase
import com.example.project1.repositories.DataBase.CLASSROOM
import com.example.project1.repositories.DataBase.DATE
import com.example.project1.repositories.DataBase.END_EVENT
import com.example.project1.repositories.DataBase.EVENT
import com.example.project1.repositories.DataBase.EVENTS
import com.example.project1.repositories.DataBase.RESPONSIBLE
import com.example.project1.repositories.DataBase.START_EVENT
import com.example.project1.repositories.Event
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class AdminFragment : Fragment() {

    val db = Firebase.firestore
    private lateinit var binding: FragmentAdminBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_admin, container, false)
        binding.back.setOnClickListener {
            findNavController().popBackStack()
        }
        val eventView: EditText = binding.event
        val startEventView: EditText = binding.startEvent
        val endEventView: EditText = binding.endEvent
        val responsibleView: EditText = binding.responsible
        val numberClassroomView: EditText = binding.numberClassroom
        val dateView: EditText = binding.date


        binding.save.setOnClickListener {
            val event = Event(numberClassroomView.text.toString(),eventView.text.toString(),startEventView.text.toString(),endEventView.text.toString(),dateView.text.toString(),responsibleView.text.toString())
            DataBase.setEvenToServer(event)
        }
        return binding.root
    }

}
