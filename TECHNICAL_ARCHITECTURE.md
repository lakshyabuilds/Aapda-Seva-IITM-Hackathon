# 🛠️ Technical Architecture & Stack

Aapda Seva is engineered with a modern, reactive native Android stack prioritizing offline-first reliability, background execution, and borderless API integrations to satisfy the rigorous requirements of the IIT Madras Road Safety Hackathon.

---

## 🏗️ Core Technology Stack

*   **Language:** Kotlin (100% Native Android)
*   **UI Framework:** Jetpack Compose (Material Design 3 paradigm for robust, accessible UI)
*   **Asynchronous Operations:** Kotlin Coroutines & Kotlin Flow (`StateFlow` / `SharedFlow`)
*   **Dependency Injection:** Constructor-based injection via `ViewModelFactory`
*   **Local Database:** Android Room (SQLite abstraction mapper)
*   **Background Processing:** Android WorkManager & Foreground Services

---

## 🌐 External APIs & Data Sources (Open & Free Architecture)

To align with the hackathon's preference for scalable, open models and independent architecture, we utilized:

1.  **OpenStreetMap (OSM) Ecosystem:**
    *   **Nominatim API (`NominatimApiService`):** Used for forward and reverse geocoding. Translates GPS coordinates into localized street/city data instantly.
    *   **Overpass API (`OverpassApiService`):** The core engine for fetching services. It queries the massive OSM database dynamically based on floating bounding boxes to find highly specific geographic nodes (e.g., `amenity=hospital`, `shop=car_repair`).
2.  **Encyclopedic Verification:**
    *   **Wikidata & Wikipedia APIs (`WikidataApiService`, `WikipediaApiService`):** Used to supplement AI data with factual, peer-reviewed definitions or protocol expansions.
3.  **AI Integration:**
    *   **Nvidia API (`NvidiaApiService`):** Integrated via `.env` secrets for intelligent inferences regarding first-aid steps or complex scenario analysis.

---

## ⚙️ System Logics & Mechanisms

### 1. Phased Payload Transmission (The "Golden Ping")
*   An accident often happens on a highway with zero/fluctuating cellular reception. Huge base64 encodings (audio/photos) can cause `SocketTimeoutExceptions`.
*   **Logic:** The system utilizes a **Two-Phase Dispatch**:
    1.  **Phase 1 (`QUICK_DISPATCH`):** Within milliseconds of an SOS, a highly compressed JSON payload strictly adhering to standard schemas (containing location, nested `locationInfo`, and `battery`) goes out immediately to secure a Firestore database entry on the backend.
    2.  **Phase 2 (`MULTI_TAP_SOS`):** Coroutines launch ambient device sensors for audio and photo arrays. The `StealthMediaCapture` module leverages CameraX to sequentially bind and capture images from **both front and back cameras**, minimizing blind-spots when the device is dropped. Once resolved, the expanded payload is sent to append the pre-existing ID securely.

### 2. Intelligent Geocoding for Native Dispatches
*   Rather than hardcoding standard Indian numbers, the system acts universally. Utilizing a dual-fallback approach, the system checks the `TelephonyManager` for network/SIM region, which operates instantly without network calls. 
*   If the network is unavailable, it falls back to Android's `Geocoder` API (handling asynchronous callbacks on API 33+ safely) to determine the country. It immediately dials the appropriate National Helpline (112, 911, 999) using `Intent.ACTION_CALL` while explicitly messaging personal contacts natively via `SmsManager`.

### 3. Multi-Threading & Concurrency
*   All API calls (Nominatim, Overpass) use `suspend` functions constrained to `Dispatchers.IO` to ensure the UI thread never drops a frame, crucial for a high-stress scenario where UI freezing is unacceptable.
*   The Map UI consumes a `StateFlow` utilizing `collectAsStateWithLifecycle()`, guaranteeing that location updates are only processed when the app is actively in the foreground.

### 3. Dynamic Bounding Box Generation
*   Instead of static city queries, the app takes the user's `(Lat, Lng)` and calculates an offset (e.g., +/- 0.05 degrees) to generate a customized Overpass Query Language (OQL) string. 
*   This makes the app borderless — it works identically in Chennai, New York, or a rural village, automatically scaling to global needs.

### 4. Continuous Integration (CI/CD)
*   To ensure production-readiness for Stage 1 submission, automated builds run via **GitHub Actions** (`android-build.yml`), executing Gradle `assembleDebug`. API keys are securely decoupled from the codebase using GitHub Secrets and injected dynamically into a `.env` file during the runner execution.
