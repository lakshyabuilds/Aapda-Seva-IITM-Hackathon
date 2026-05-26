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

## 🚨 3. Single-Tap SOS Engine & Phased Dispatch

**Non-Technical Overview:** 
In high-stress situations, users don't have time to navigate permissions or type messages. Our fully frictionless SOS operates via a single tap. It instantly dispatches a silent ping to our dashboards, automatically messages your emergency contacts with an exact Google Maps location link via SMS, and universally calls the exact National Helpline (112, 911, 999) depending on your current geographical country.

**Technical Overview:** 
- **Frictionless Trigger:** Removed all disruptive permission blocks; if a permission (like background location) isn't fully granted, the app degrades gracefully but *never* blocks the SOS execution.
- **Phased Payload Architecture:** To combat spotty networks, SOS data is dispatched in prioritized tiers:
  - **Phase 1 (`QUICK_DISPATCH`):** Instantly pushes a lightweight JSON payload with GPS coordinates, battery level, and ID to the backend for immediate registry.
  - **Phase 2 (`MULTI_TAP_SOS`):** In parallel, environmental media is captured. It simultaneously triggers **both the front and back cameras** sequentially utilizing CameraX, maximizing the chance of capturing relevant surroundings regardless of the phone's physical orientation. Upon completion, the expanded critical payload with the base64 media arrays is dispatched.
- **Native Contextual Communications:** 
  - Iterates through the local Room emergency contact list, automatically firing native Android SMS intents with concatenated G-Maps coordinate links.
  - **Intelligent Dual-Fallback Helplines:** Instantly resolves the device's ISO Country Code by querying the `TelephonyManager` for network/SIM region (near 0ms latency), falling back to synchronous `Geocoder` reverse mapping resolving correctly using `Dispatchers.IO`. A smart router evaluates this code (e.g., `IN` -> `112`, `US`/`CA` -> `911`, `GB` -> `999`). Utilizing strategic delays, it circumvents native OS intent conflicts with the SMS execution, ensuring `ACTION_CALL` universally triggers reliably without silent suppression.

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
