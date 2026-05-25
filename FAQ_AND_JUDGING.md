# ⚖️ FAQ & Judging Criteria Insights

To stand before the IIT Madras Judges, we must anticipate their technical scrutiny. This document prepares answers for potential questions—combining intelligent framing of our strengths while respectfully acknowledging and mitigating our technical limitations.

---

### 🟢 Theme: Architecture & Innovation

**Q1: The hackathon rulebook strongly prefers Python. Given your app is native Android (Kotlin), how does this fit the ecosystem?**
**A:** We recognized that for a *client-facing mobile app* required to execute efficiently offline and handle background sensors/GPS without battery drain, native Kotlin is objectively superior. However, our architecture is built to perfectly sync with a Python backend. All data exported by our `IncidentSyncWorker` (JSON) and all database structures are designed to be ingested by a Python/Django or FastAPI server for advanced AI analytics, seamlessly marrying mobile native performance with Pythonic backend preference.

**Q2: How are you ensuring your point-of-interest data (Hospitals, Police) is accurate?**
**A:** Instead of relying on a closed, paid system like Google Places API which can be cost-prohibitive for governments, we utilized the **Overpass API (OpenStreetMap)**. This represents a highly crowdsourced, continuously updated, global open-source model. It maximizes the volume of queried data by bypassing corporate rate-limits.

**Q3: What makes this app an "innovation" rather than just a wrapper for a map?**
**A:** Standard maps require active "searching" and cognitive load. Aapda Seva utilizes **Contextual Geofencing** and **One-Tap Execution**. Furthermore, our integration of a stealth background SOS (`SosBackgroundService`) to preserve incident data, alongside an AI first-aid inference component (`AiHelpScreen`), pushes the platform from a simple map to a comprehensive crisis-management tool.

---

### 🟡 Theme: Limitations & Honest Criticisms

**Q4: OpenStreetMap relies on community input. What happens if an ambulance service isn't mapped in a rural village?**
**A:** *(Honest Limitation)* This is a valid limitation of relying entirely on open databases; rural parity is sometimes lower than urban parity. *Mitigation:* The app architecture is designed logically. If the Overpass API returns zero nearby emergency nodes, the app defaults to displaying the National Emergency Numbers (112, 108) as a universal fallback, ensuring the user is never left with a blank screen.

**Q5: What happens if the Overpass API goes down or rate-limits you during an emergency?**
**A:** *(Honest Limitation)* Public instances of Overpass can be throttled. *Mitigation:* We implemented an offline-first Room Database architecture (`EmergencyServiceDao`). When the app is opened under regular conditions, it quietly caches local nodes. If the live API fails during an emergency, the app seamlessly reads from the SQLite cache. For a true national deployment, the government would host its own dedicated Overpass instance, eliminating rate limits entirely.

**Q6: You mention "Offline Functionality," but reverse geocoding and AI features require the internet. How do you reconcile this?**
**A:** *(Honest Limitation)* It's true that complex Nvidia AI queries and Nominatim street-name generation require HTTP requests. *Mitigation:* The absolute core critical path—the SOS trigger, reading the GPS coordinate string (Lat/Lng), and accessing the cached medical profiles and nearby stored emergency numbers—all operate perfectly offline. We degrade gracefully: the user loses the AI chatbot in a tunnel, but they never lose the ability to queue an SOS via the `IncidentSyncWorker` which will fire the second a 1G signal is caught.

---

### 🟢 Theme: Compliance & Final Logistics

**Q7: How does this address the "Golden Hour"?**
**A:** The Golden hour is lost to panic and lack of information. By unifying Towing, Police, and Medical routing into a strict Location-Based bounding box, we drastically reduce the time a bystander spends figuring out *who* to call, accelerating the dispatch timeline.

**Q8: Have you secured user data against unauthorized background tracking?**
**A:** Yes. Location fetching relies entirely on explicit Android Runtime Permissions. The `SosBackgroundService` only executes upon verified user confirmation within the `SosConfirmScreen`. The profile medical data remains encrypted natively via Android's local sandbox and is not transmitted unless explicitly packaged into the SOS payload.
