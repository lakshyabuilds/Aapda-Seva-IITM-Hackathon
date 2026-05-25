# 🌟 Aapda Seva App Features

This document provides a comprehensive breakdown of the features, pages, functions, and underlying logic integrated into **Aapda Seva**, explained in both non-technical (for users/judges focused on utility) and technical (for developers/architects) terms. 

---

## 📍 1. Map & Location Engine (`MapScreen`)

**Non-Technical Overview:** 
The core of Aapda Seva. When an accident occurs, the user immediately sees their exact location on a map. With a single tap, the app highlights nearby hospitals, police stations, and even towing services, providing the fastest route to safety or help.

**Technical Overview:** 
- **Component:** Jetpack Compose interactive Map view.
- **Logic:** Utilizes the device's GPS provider (fused location) to retrieve the user's current latitude and longitude. 
- **APIs Used:** Connects to `NominatimApiService` for reverse geocoding (converting coordinates to human-readable addresses) and standard mapping overlays.
- **State Management:** Map state is managed via Kotlin StateFlows, ensuring that as the user's location updates, the UI instantly reacts without full recomposition.

---

## 🏃 2. Emergency Services Directory (`ServicesScreen` & `ContactsScreen`)

**Non-Technical Overview:** 
A categorized, easy-to-read list of every critical contact you might need during the "golden hour". This includes immediate medical trauma centers, police stations, and vehicle rescue teams (puncture shops, garages). 

**Technical Overview:** 
- **Component:** Compose `LazyColumn` for performant list rendering.
- **Logic:** Constructs a bounding box around the user's current GPS coordinates. It then fires queries to the `OverpassApiService` to retrieve localized OpenStreetMap nodes specifically tagged as `amenity=hospital`, `amenity=police`, or vehicle repair nodes. 
- **Data Parsing:** Unpacks raw JSON from the Overpass/Nominatim APIs and transforms them into `ContactData` data classes.
- **Offline Fallback:** If the network fails, it queries the local `Room` database (`EmergencyServiceDao`) to pull the last known cached services.

---

## 🚨 3. Single-Tap SOS Engine (`SosConfirmScreen` & `SosBackgroundService`)

**Non-Technical Overview:** 
In high-stress situations, users don't have time to navigate. The SOS feature is a confirmed trigger that can operate quietly in the background, notifying authorities and generating an incident footprint without requiring continuous screen interaction.

**Technical Overview:** 
- **Component:** Android `Service` (Foreground/Background) and `WorkManager`.
- **Logic:** Once confirmed via `SosConfirmScreen`, the `SosBackgroundService` initiates. It logs the exact incident timestamp and coordinates into the `IncidentBackupEntity` within the local Room database to prevent data loss.
- **Stealth Media Capture:** Contains hooks via `StealthMediaCapture` for potential ambient environment documentation (e.g., photo/audio footprint) to assist police/insurance post-accident, adhering to strict Android sensor permissions.
- **Syncing:** The `IncidentSyncWorker` (WorkManager) queues the SOS payload. If the user is offline, it waits for network reconnection to dispatch the SOS payloads to an external backend.

---

## 🤖 4. AI-Powered Medical & Procedural Assistance (`AiHelpScreen`)

**Non-Technical Overview:** 
While waiting for an ambulance, bystanders often don't know what to do. The AI Help Screen provides immediate, specialized instructions (e.g., "How to stop bleeding," "How to secure the accident scene") using intelligent suggestions.

**Technical Overview:** 
- **Component:** Compose conversational or contextual query interface.
- **Logic:** Interfaces with the `NvidiaApiService` utilizing high-performance API endpoints to fetch immediate inferences for medical first-aid or procedural safety steps.
- **Data Augmentation:** Fetches supplementary context from `WikidataApiService` or `WikipediaApiService` to provide verified, encyclopedic definitions if the user encounters an unknown medical/legal term.

---

## 👤 5. Profile & Medical ID (`ProfileScreen`)

**Non-Technical Overview:** 
A securely stored personal passport. It contains the user's blood type, emergency contacts, and preexisting medical conditions. If the user is the victim, a first responder can view this to administer safe medical care.

**Technical Overview:** 
- **Component:** Form-based Compose screen interacting with `UserProfileViewModel`.
- **Logic:** Persists user data locally using `UserProfileDao` mapping to `UserProfileEntity`. 
- **Security:** Data is kept strictly on-device (offline-first approach) ensuring absolute privacy until the user explicitly triggers an SOS where specific data points may be shared with emergency vehicles.
