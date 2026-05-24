package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@Composable
fun ProfileScreen(
    viewModel: UserProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()

    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(profile) {
        if (profile != null) {
            name = profile?.name ?: ""
            age = profile?.age?.toString() ?: ""
            bloodGroup = profile?.bloodGroup ?: ""
            allergies = profile?.allergies ?: ""
            notes = profile?.notes ?: ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Medical Profile",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = age,
            onValueChange = { age = it },
            label = { Text("Age") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = bloodGroup,
            onValueChange = { bloodGroup = it },
            label = { Text("Blood Group (e.g. O+)") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = allergies,
            onValueChange = { allergies = it },
            label = { Text("Allergies (comma separated)") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Medical Notes") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            minLines = 3
        )

        Button(
            onClick = {
                val ageInt = age.toIntOrNull() ?: 0
                viewModel.saveProfile(name, ageInt, bloodGroup, allergies, notes)
                Toast.makeText(context, "Profile saved successfully", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Save Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
