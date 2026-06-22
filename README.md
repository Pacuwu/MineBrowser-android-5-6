# MineBrowserGecko 🦊📱

An ultra-lightweight, high-performance Android web browser built on top of the modern **GeckoView 143** engine. This project is specifically designed and optimized from scratch to breathe new life into legacy hardware, bypassing severe RAM constraints while ruthlessly enforcing resource efficiency.

---

## 🚀 Hardware Butcher Features & Performance

* **1 GB RAM Warrior:** Completely stable, fluid, and crash-free navigation on devices with **strictly 1 GB of RAM** (benchmarked extensively on a legacy **Sony Xperia M2**).
* **Forced Mobile Viewport:** Enforces true `DISPLAY_MODE_MOBILE` / `DISPLAY_MODE_BROWSER` rendering directly inside the Gecko pipelines to block bloated desktop assets and dramatically save device memory.
* **Built-in Tracking Protection:** Active blocking of heavy modern trackers and background scripts to relieve legacy CPUs and optimize data routing.
* **Maximized Network Throughput:** Achieved raw download speeds of **60+ Mbps** on ancient network cards by cutting browser asset processing overhead.

## 📱 System Requirements & Compatibility

* **Minimum Android Version:** Android 5.0+ (API Level 21 - Lollipop) up to modern versions.
* **Target Hardware:** Perfect for low-end testing benches, old devices, or resource-constrained emulated environments.

## 🔥 Verified Live Site Compatibility

Tested and verified to render heavy, reactive modern frameworks and real-time content smoothly on legacy specs:
* **YouTube (`m.youtube.com`):** Perfect video playback leveraging hardware-accelerated AVC/H.264 decoding. **0 dropped frames** (`0 dropped of 2143`) and fluid stats-for-nerds streaming at native speeds.
* **Bluesky (`bsky.app`) & X (`x.com`):** Full support for modern single-page apps (SPA), real-time timelines, interactive tabs, dynamic feeds, and smooth scrolling without WebKit-related freeze-ups or memory leaks.
* **Character.ai (`character.ai` / `c.ai`):** Smooth real-time chat rendering and AI message stream processing without lagging the legacy CPU or triggering low-memory process kills.
* **Heavy WebApps:** Capable of loading intensive real-time web games (like Subway Surfers web) and live chat interfaces natively without memory overflow.
---

## 🛠️ Project Roadmap & Status (85% Completed)

### ✅ Completed Core Architecture & UI
- [x] Fully integrated, independent `GeckoRuntime` lifecycle initialization.
- [x] Dynamic multi-tab system via memory-reusable `GeckoSession` arrays.
- [x] Custom pop-up and target redirection interception via `NavigationDelegate` hooks.
- [x] Integrated address bar with smart URL building and automatic Google search routing.
- [x] Physical keyboard/IME actions support (including `EditorInfo.IME_ACTION_GO` and Enter keys).
- [x] Strict user-agent spoofing combined with native display properties.
- [x] **Navigation Controls & Refresh:** Action mapping for page reloading directly integrated into the UI.
- [x] **Navigation History Menu:** Native localized stack tracking with a dedicated interactive history view.

### ⏳ Remaining Implementation (15% Pending)
- [ ] **Bookmarks System:** Persistent storage for quick links and custom adjustments.
- [ ] **UI Polishing:** Final touches to the XML layouts and smooth visual transitions between multi-tab configurations.

---

## 🛠️ How to Compile
1. Clone this repository.
2. Ensure you have the GeckoView AAR dependency set up in your `build.gradle`.
3. Open in Android Studio and deploy to your legacy device or AVD instance.
