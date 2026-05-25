# 👥 Aapda Seva: User Personas & Use Cases

Aapda Seva isn't designed for just one type of user. In an emergency, the dynamic shifts depending on who holds the phone. Here are the core use cases demonstrating the platform's versatility across the Road SOS ecosystem.

---

## 🧍 Use Case 1: The Bystander / Good Samaritan
*Context: A pedestrian witnesses a severe collision between a two-wheeler and a car on an urban road.*

*   **The Problem:** The bystander wants to help but is panicked, doesn't know the exact address to tell the dispatcher, and doesn't know where the nearest trauma center is.
*   **Aapda Seva Solution:** 
    1.  The bystander opens the app. The **MapScreen** immediately resolves their exact GPS location without typing.
    2.  They tap the **"Nearest Hospital"** button. The app queries the Overpass API and highlights a clinic just 400 meters away.
    3.  Unsure of how to safely move the victim, the bystander uses the **AiHelpScreen** to quickly get verified instructions: *"Should I remove the helmet?"*
*   **Outcome:** The "Golden Hour" response time is drastically cut down, bypassing traditional dispatch delays.

---

## 🤕 Use Case 2: The Victim (Conscious but Immobile)
*Context: A driver veers off a rural highway at night. They are injured, alone, and have only a 1G / Edge network connection.*

*   **The Problem:** The user cannot speak clearly, cannot browse Google Maps on a slow connection, and doesn't know what mile marker they are at.
*   **Aapda Seva Solution:**
    1.  The user triggers the **One-Tap SOS**.
    2.  The app functions in **Offline/Low-Network Mode**. It reads the local cached SQLite database (Room) to find the nearest stored patrol station.
    3.  The **SosBackgroundService** queues the distress signal. As soon as a microscopic ping of data is available, the `IncidentSyncWorker` violently pushes the exact latitude, longitude, and victim’s Medical Profile (blood group, allergies) to authorities.
*   **Outcome:** The victim secures a lifeline without needing to articulate their location verbally over a dropped call.

---

## 🚗 Use Case 3: The Mild Collision (Vehicle Rescue)
*Context: A family is on a road trip. Their tire blows out, causing a minor spinout into a ditch. No injuries, but the vehicle is immobile.*

*   **The Problem:** 100/112 (Police) and 108 (Ambulance) are overkill and shouldn't be clogged with non-injury calls. The family just needs a tow truck and a mechanic.
*   **Aapda Seva Solution:**
    1.  The user navigates to the **ServicesScreen**.
    2.  Instead of trauma centers, they filter for **Vehicle Rescue**. 
    3.  The app queries OpenStreetMap specifically for `shop=tyres` and `amenity=vehicle_rescue` nodes, displaying a puncture shop 2km away complete with contact numbers.
*   **Outcome:** Efficient resolution of a logistical nightmare without burdening the national emergency medical infrastructure.

---

## 🚔 Use Case 4: The Authority / Responder (Potential Future Extension)
*Context: A dispatched ambulance is en route to the exact coordinates provided by the Aapda Seva SOS payload.*

*   **The Problem:** Responders often arrive blind to the victim's medical history.
*   **Aapda Seva Solution:**
    1.  Because the victim had filled out their **ProfileScreen** (stored via `UserProfileDao`), the SOS payload contains JSON data indicating the victim has a severe Penicillin allergy and is O-Negative.
    2.  The paramedics prepare the correct blood type en route.
*   **Outcome:** Avoidance of fatal medical errors during chaotic on-site treatment.
