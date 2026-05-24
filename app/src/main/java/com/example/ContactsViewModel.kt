package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val contactDao = AppDatabase.getDatabase(application).contactDao()

    val contacts: StateFlow<List<Contact>> = contactDao.getAllContacts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addContact(name: String, phoneNumber: String, relation: String) {
        viewModelScope.launch {
            contactDao.insertContact(Contact(name = name, phoneNumber = phoneNumber, relation = relation))
        }
    }

    fun deleteContact(id: Int) {
        viewModelScope.launch {
            contactDao.deleteContactById(id)
        }
    }
}
