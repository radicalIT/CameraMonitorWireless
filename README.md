# CameraMonitorWireless

A wireless professional monitoring solution that receives video streams over the local network via HTTP MJPEG, providing mobility on set without sacrificing professional tools.

## Connectivity
* **MJPEG Streaming:** High-performance MJPEG decoding optimized for low-latency network environments.
* **Dynamic Configuration:** Easily update and save stream URLs (e.g., `http://10.42.0.1:8080/stream`) directly from the side panel.
* **Compatibility:** Ideal for use with DIY wireless transmitters based on Orange Pi, Raspberry Pi, or other MJPEG servers.

## Professional Analysis Tools
Includes the full suite of image analysis tools found in the Pro version:
* **Exposure Aids:** Zebra stripes and False Color mapping.
* **Focus Tools:** Focus Peaking with variable sensitivity and color.
* **Color Management:** Built-in S-Log3 to Rec.709 LUT.
* **Advanced Scopes:** Real-time Luma Histogram and RGB Parade optimized for bitmap processing.
* **Anamorphic Support:** Desqueeze options for anamorphic workflows.

## How to Use
1. Host an MJPEG stream on your local network (e.g., via an Raspberry Pi).
2. Open the Advanced Panel in the app.
3. Enter the full Stream URL and click "Update & Connect."
4. The app will automatically save the URL for future sessions.

## License
Based on [android-usb-cam-viewer](https://gitlab.com/yaky/android-usb-cam-viewer). Licensed under the [Apache License 2.0](LICENSE).