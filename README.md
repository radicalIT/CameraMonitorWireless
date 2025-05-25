# Android USB Camera Viewer

Simple Android app to display video feed from a connected USB camera. There are no controls or configuration - the app automatically detects the camera and displays the video using the highest resolution possible. The view area uses the most of the device's display, and might distort the original image.

The app is expected to work with any USB device that presents itself as a UVC camera, whether a webcam, a capture device, or any sort of a converter. The original use case is an in-car display for rear camera.

## Permissions

This app requires two permissions:
- Camera permission is required to access USB cameras. The app does not take pictures nor record video.
- Record audio permission is required to automatically launch the app when USB camera is connected. The app does not record any audio. (Without this permission, when USB camera is connected, Android will display the "launch with" prompt, but the "Always" option for USB Camera Viewer will be unavailable)

## Installation

Download and install the **.apk** files from releases page.

Also available on [yaky.dev](https://yaky.dev/apps/usb_camera_viewer).

## Building

This app was built using Android Studio Meerkat 2024.3.1, using OpenJDK 21.0.5.

## Contributing

You are welcome to open issues and PRs for any issues that you find.

I would like to keep this application as simple as possible.

## License

Licensed under [Apache License 2.0](LICENSE)

## Acknowledgments

This app uses an updated [uvccamera library](https://uvccamera.org/), hosted on Maven, built by Alexey Pelykh, and licensed under [Apache License 2.0](https://github.com/alexey-pelykh/UVCCamera/blob/main/LICENSE.md). 

The updated library is based on the [original UVCCamera library](https://github.com/saki4510t/UVCCamera) by saki, licensed under [Apache License 2.0](https://github.com/saki4510t/UVCCamera/blob/master/README.md)

App icon is a [Camera icon](https://commons.wikimedia.org/wiki/File:Camera_rounded.svg) from Wikimedia Commons, created by Eduardo López and licensed under [CC-BY-SA-3.0 license](https://creativecommons.org/licenses/by-sa/3.0/deed.en).
