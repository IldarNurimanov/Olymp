package com.example.project1.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.example.project1.R
import com.example.project1.databinding.FragmentMainBinding
import com.example.project1.repositories.DataBase
import com.example.project1.repositories.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber


class MainFragment : Fragment() {
    private lateinit var binding: FragmentMainBinding

    private val scope = CoroutineScope(Dispatchers.IO)
    var pushed:Int=0
    lateinit var infoTextView: TextView

    private val observer = Observer<Event>{
        Timber.d("Event = $it")
        if (!it.name.isNullOrEmpty()) {
            infoTextView.text = getString(
                R.string.eventInfo,
                it.date,
                it.startEvent,
                it.endEvent,
                it.name,
                it.responsible
            )
        } else if (pushed==1){
            infoTextView.text = "Текущего мероприятия не найдено"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false)
        infoTextView= binding.infoWithDb
        val numberClassroomView: TextView = binding.numberClassroom

        binding.toAdmin.setOnClickListener {
            findNavController().navigate(MainFragmentDirections.actionMainDestToAdminDest())
        }
        binding.toMLKit.setOnClickListener {
            findNavController().navigate(MainFragmentDirections.actionMainDestToMlkitDest())
        }

        DataBase.currentEvent.observeForever(observer)

        binding.get.setOnClickListener {
            pushed=1
            scope.launch {
                DataBase.getEventFromServer(numberClassroomView.text.toString())
            }
        }

        return binding.root
    }

    override fun onDestroy() {
        DataBase.currentEvent.removeObserver(observer)
        scope.cancel()
        super.onDestroy()
    }
}




