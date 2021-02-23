package com.example.project1.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.project1.R
import com.example.project1.databinding.FragmentMainBinding
import com.example.project1.repositories.DataBase
import timber.log.Timber


class MainFragment : Fragment() {
    private lateinit var binding: FragmentMainBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false)
        val infoTextView: TextView = binding.infoWithDb
        val numberClassroomView: TextView = binding.numberClassroom

        binding.toAdmin.setOnClickListener {
            findNavController().navigate(MainFragmentDirections.actionMainDestToAdminDest())
        }
        binding.toMLKit.setOnClickListener {
            findNavController().navigate(MainFragmentDirections.actionMainDestToMlkitDest())

        }



        binding.get.setOnClickListener {
            DataBase.getEventFromServer(numberClassroomView.text.toString())
            DataBase.currentEvent.observeForever{
                Timber.d("Event = $it")
                if (!it.name.isNullOrEmpty()){
                infoTextView.text = getString(
                    R.string.eventInfo,
                    it.date,
                    it.startEvent,
                    it.endEvent,
                    it.name,
                    it.responsible
                )
                }else{
                    infoTextView.text = "Текущего мероприятия не найдено"
                }
            }
        }

        return binding.root

    }

}




