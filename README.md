# WiFi Hotspot Detector 📡

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)
![Java](https://img.shields.io/badge/Language-Java-orange.svg)

**An Android app that detects and locates unauthorized WiFi networks using signal strength and haptic feedback.**

---

## 🎯 Overview

WiFi Hotspot Detector helps authorized personnel identify and physically locate rogue WiFi networks in classrooms and secure facilities[web:57][web:60]. The app uses WiFi signal strength (RSSI) and continuous vibration feedback to guide users toward hidden hotspots.

### Key Features

- 🔍 Real-time WiFi scanning with auto-refresh
- 🚨 Automatic detection of suspicious mobile hotspots
- 📊 Visual signal strength display with color-coded circles
- 📐 Distance estimation (±2-5 meters accuracy)
- 📳 Continuous haptic feedback (stronger = closer)
- 🎨 Material Design UI with custom network list

---

## 💾 Installation

### Requirements

- Android 5.0 (API 21) or higher
- WiFi and vibration hardware
- ~10 MB storage

---

## 📖 Usage

1. **Scan**: Tap "SCAN FOR NETWORKS"
2. **Identify**: Networks marked ⚠️ are suspicious (mobile hotspots, strong signals)
3. **Track**: Tap any network to open tracker
4. **Locate**: Walk around - vibration intensity increases as you get closer

### Signal Guide

- 🟢 **Green circle + Strong vibration** → < 2 meters
- 🟡 **Orange circle + Medium vibration** → 5-10 meters
- 🔴 **Red circle + Weak vibration** → > 20 meters

---

## 🔧 Technical Details

### Architecture

MainActivity.java → Network scanning & list
TrackerActivity.java → Signal tracking & vibration
WifiAdapter.java → Custom network list display

### Distance Formula

d = 10^((RSSI_1m - RSSI) / (10 × n))

Where: RSSI_1m = -40 dBm, n = 2.5 (indoor path loss)

### Permissions

- `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`, `NEARBY_WIFI_DEVICES`
- `VIBRATE`

---

## 🐛 Troubleshooting

| Problem              | Solution                                                         |
| -------------------- | ---------------------------------------------------------------- |
| No networks detected | Enable WiFi, Location, and disable scan throttling               |
| Permission denied    | Settings → Apps → WiFi Finder → Permissions → Allow all the time |
| Scan throttled       | Wait 2 minutes or use cached results (auto-refresh continues)    |
| No vibration         | Check device settings and toggle switch in tracker               |

---

## 🚀 Future Enhancements

- [ ] Multi-point triangulation for precise location
- [ ] Signal strength heatmaps
- [ ] Network logging and history
- [ ] Bluetooth device detection
- [ ] Export scan data (CSV/JSON)

---

## ⚠️ Disclaimer

**For authorized monitoring only.** This app is intended for legitimate security purposes in controlled environments (examination halls, secure facilities)[web:57][web:60].

- ✅ Permitted: Authorized security monitoring, network administration
- ❌ Prohibited: Unauthorized surveillance, privacy invasion

**Privacy:** This app does NOT collect, store, or transmit location data. Location permission is required by Android for WiFi scanning only[web:22][web:49].

Users must comply with local laws and regulations. Use at your own risk.

---

## 📄 License

MIT License - see [LICENSE](LICENSE) file.

---

## 🤝 Contributing

Contributions welcome! Please:

1. Fork the repo
2. Create feature branch (`git checkout -b feature/YourFeature`)
3. Commit changes (`git commit -m "Add feature"`)
4. Push to branch (`git push origin feature/YourFeature`)
5. Open Pull Request

---

## 📞 Contact

**Issues:** [Report bugs](https://github.com/AladinAzz/wifi-hotspot-detector/issues)

---

⭐ **Star this repo if you find it useful!**

Made with ❤️ for secure communication environments
