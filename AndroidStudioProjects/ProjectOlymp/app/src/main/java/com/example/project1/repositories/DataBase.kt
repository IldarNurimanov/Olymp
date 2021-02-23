package com.example.project1.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object DataBase {
    private val db = Firebase.firestore
    fun getCollection() = db.collection("Classrooms")

    val currentEvent : LiveData<Event>
        get() = _currentEvent
    private val _currentEvent = MutableLiveData<Event>(Event())



    fun getEventFromServer(numberClassroomView: String) {
        if(numberClassroomView.isEmpty()) return
        Timber.d("numberClassroomView = $numberClassroomView")
        val currentDate = Date()
        val dateFormat: DateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateText = dateFormat.format(currentDate).toString()
        val timeFormat: DateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val hours: DateFormat = SimpleDateFormat("HH", Locale.getDefault())
        val minutes: DateFormat = SimpleDateFormat("mm", Locale.getDefault())
        var a: String
        val intHours = hours.format(currentDate).toInt()
        var count: Int
        val intMinutes = minutes.format(currentDate).toInt()
        val timeMin = intHours * 60 + intMinutes
        val timeText = timeFormat.format(currentDate)
        val docRef = DataBase.getCollection()
        val CR: String = numberClassroomView
        var event = Event()
        try {
            docRef.document(CR).collection("Events")
                .whereLessThanOrEqualTo("startEvent", timeText)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        a = document.get("endEvent").toString()
                        count = (a[0].toString().toInt() * 10 + a[1].toString()
                            .toInt()) * 60 + (a[3].toString().toInt() * 10 + a[4].toString()
                            .toInt())
                        Log.d("TAG", "DATE:$dateText" + "iuhiu$count" + "svcsc$timeMin")
                        if ((count > timeMin) && (document.get("date") == dateText)) {
                            Log.d("TAG", "iuhiu${document.data}")
                            _currentEvent.value = Event(
                                numberClassRoom = numberClassroomView,
                                name = document.get(EVENT).toString(),
                                startEvent = document.get(START_EVENT).toString(),
                                endEvent = document.get(END_EVENT).toString(),
                                date = document.get(DATE).toString(),
                                responsible = document.get(RESPONSIBLE).toString()
                            )
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    _currentEvent.value = Event()
                    Timber.w(exception, "Error getting documents: ")
                }
        } catch(e:Throwable){
            Timber.e(e,"Проблема с файербэйзом")
        }
    }


    fun setEvenToServer(event: Event) {
        val data = hashMapOf(
            EVENT to event.name,
            RESPONSIBLE to event.responsible,
            START_EVENT to event.startEvent,
            END_EVENT to event.endEvent,
            DATE to event.date
        )
        getCollection().document(event.numberClassRoom.toString()).collection(
            EVENTS
        ).document(UUID.randomUUID().toString()).set(data)

    }

    const val DATE = "date"
    const val EVENT = "event"
    const val EVENTS = "Events"
    const val RESPONSIBLE = "responsible"
    const val START_EVENT = "startEvent"
    const val END_EVENT = "endEvent"
    const val NUMBER_CLASSROOM = "numberClassroom"
    const val CLASSROOM = "Classrooms"

}