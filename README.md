# 🚨 Aapda Seva - Road SOS & Emergency Rescue Platform

[![Android CI Build](https://github.com/lakshyabuilds/Aapda-Seva-IITM-Hackathon/actions/workflows/android-build.yml/badge.svg)](https://github.com/lakshyabuilds/Aapda-Seva-IITM-Hackathon/actions/workflows/android-build.yml)

Aapda Seva is a globally scalable, unified location-based mobile application designed to maximize survival chances during the crucial "golden hour" following a road accident. Created for the **IIT Madras Road Safety Hackathon (Road SOS Track)**, the platform seamlessly connects bystanders and victims to the nearest medical, police, and vehicle rescue services.

## 🏆 Key Features Tailored to IIT Madras Criteria

### 1. Unified Location-Based Emergency Interface
Built to function rapidly under pressure, addressing the critical **Golden Hour**:
* **Medical & Security Assistance:** Instantly fetches the nearest **Police Stations, Hospitals, and Ambulance Services** based on ultra-precise GPS geocoding.
* **Vehicle Rescue Services:** Quickly locates nearest **Towing Services, Puncture Repair Shops, and Vehicle Showrooms/Garages**.
* **Single Tap SOS:** Everything is accessible from a clean, unified dashboard, speeding up coordination for bystanders without navigating multiple menus.

### 2. Robust Technical Architecture
* **Offline-First Resilience:** Integrated Room database for offline fallback, ensuring the app remains highly functional in low/no-network zones (e.g., remote highways). 
* **Global Applicability:** Not restricted by geography. Utilizes borderless APIs (OpenStreetMap Overpass / Nominatim) ensuring the SOS module scales and works flawlessly in any country.
* **Open Models & Free APIs:** Prioritizes open and cost-effective architecture. Leverages free OpenStreetMap nodes for massive location data volumes, Wikidata/Wikipedia for contextual information, and free tier open APIs for processing.
* **Python Compatibility:** The application’s data models and external structures are designed to interact seamlessly with Python-based backends/analysis scripts, adhering to rulebook preferences.

### 3. Evaluation Highlights
* **High Data Accuracy & Volume:** By tapping into OpenStreetMap's exhaustive database (using Overpass API), the system maximizes the number of relevant, verified contacts returned (clinics, police, mechanics) bypassing the limitations of proprietary, paid APIs.
* **Cross-Border Execution:** Fully dynamic bounding box logic; no hardcoded regional APIs. Works exactly the same in India as it would globally.
* **Innovative Additions:** Includes automated background incident syncing (WorkManager), stealth media capture potentials, and secure offline local caching.

## 🛠️ Tech Stack
* **Frontend:** Kotlin, Jetpack Compose (Modern Android UI)
* **Local Persistence:** Android Room Database (SQLite)
* **Real-time Mapping & APIs:** Nominatim Service, Overpass API, Wikidata API, Nvidia APIs
* **Background Processing:** Android Coroutines, WorkManager

---

## 🚀 How to Build and Run

### 1. Local Development
1. Clone the repository.
2. Open the project in Android Studio.
3. In the root directory, create a `.env` file based on `.env.example`:
   ```bash
   cp .env.example .env
   ```
4. Open the `.env` file and add your `NVIDIA_API_KEY`.
5. Sync Gradle and run on an Android Emulator or physical device.

### 2. Automated GitHub Actions Build (CI/CD)
This repository includes a production-grade GitHub Action. Upon any push to the `main` branch, it automatically:
1. Provisions an Ubuntu CI runner.
2. Sets up JDK 17 and Gradle build environment.
3. Injects API keys securely from your repository secrets.
4. Builds a release-ready Debug APK.
5. Uploads the APK as an artifact you can download.

#### 🔑 Adding API Keys to GitHub Secrets
To make the automated build work without failing:
1. Go to your GitHub repository -> **Settings** -> **Secrets and variables** -> **Actions**.
2. Click **New repository secret**.
3. Add the following secret:
   * **Name**: `NVIDIA_API_KEY`
   * **Secret**: (Paste your actual API key here)
4. *(Optional)* To inject your own keystore securely, you can add `DEBUG_KEYSTORE_BASE64` containing your base64-encoded debug keystore.

---

## 📄 Stage 1 Submission Logistics Checklist
- [x] **Code Submission:** Complete app source architecture ready for review and upload.
- [x] **Database Submission:** The local offline system is structured using Entity tables (`IncidentBackupEntity`, `EmergencyServiceEntity`).
- [ ] **Pitch Deck:** *Reminder: Ensure your external presentation is exactly 7 slides long, including a Welcome and Thank You slide.*
- [ ] **Originality:** Verified original source code.
