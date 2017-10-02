## Arduator
**A Firmware (.hex) Uploader for Arduino using Bluetooth 2.0 or 4.0LE (BLE).**

Use Arduator to upload a .hex file produced by the Arduino compiler to a properly equipped **Arduino Nano or Uno** device.

In App help documentation explains how to build a programming circuit and setup/connect the Arduino, bluetooth module, and programming circuit.

**Only Arduino Nano and Uno are currently supported. A programming circuit is required.**

### Like the code you see? Want to encourage and support the developer? Buy the App its only $0.99!
[![Google Play Store](https://raw.githubusercontent.com/e-regular-games/arduator/master/art/google-play-badge.png "Google Play Store")](https://play.google.com/store/apps/details?id=com.e_regular_games.arduator)

### The Programming Circuit

[Programming Circuit](https://raw.githubusercontent.com/e-regular-games/arduator/master/art/google-play-badge.png "Programming Circuit")

In this diagram, STATE is an output from the Bluetooth module and RST is connected to the Arduino RST pin input.

A switch is used to provide power to an Inverter logic gate. When the switch is ON and a connection is established to the Bluetooth module, the capacitor discharges pulling the RST pin to LOW temporarily. This causes the Arduino to reset and accept bootloader commands.

There are programming circuit designs provided by others online. Any similar working design will function with Arduator.

### Brought to You By

[![E-Regular Games](https://raw.githubusercontent.com/e-regular-games/arduator/master/art/company_logo.png "E-Regular Games")](http://e-regular-games.com)
