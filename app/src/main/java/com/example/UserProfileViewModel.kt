package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.UserProfileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val userDao = AppDatabase.getDatabase(application).userProfileDao()

    val userProfile: StateFlow<UserProfileEntity?> = userDao.getUserProfileFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun saveProfile(name: String, age: Int, bloodGroup: String, allergies: String, notes: String) {
        viewModelScope.launch {
            userDao.insertUserProfile(
                UserProfileEntity(
                    id = 1,
                    name = name,
                    age = age,
                    bloodGroup = bloodGroup,
                    allergies = allergies,
                    notes = notes
                )
            )
        }
    }
}
