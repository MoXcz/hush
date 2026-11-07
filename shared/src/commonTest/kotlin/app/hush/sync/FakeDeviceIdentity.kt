// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

class FakeDeviceIdentity(
    var id: String = "device-a",
) : DeviceIdentity {
    override suspend fun deviceId(): String = id
}
