package com.daviddokakis.karoointerval

import io.hammerhead.karooext.models.SavedDevices

/**
 * Is the Karoo set up for ERG with the chosen connection? Checked from the Karoo's saved sensors,
 * every step the rider has to get right, for Settings' checklist and the start screen's hint.
 * Extensions can't switch the Karoo's sensors themselves, so the app can only say which one and how.
 *
 * Bluetooth: the trainer on for the profile (the Karoo records it, Read-Only), Nearby devices
 * allowed, nothing of the Karoo's on the trainer's Bluetooth (the original Tacx NEO takes one
 * connection), and the app's ANT+ sensor off.
 * ANT+: a trainer number, the Karoo's own ANT+ trainer off (its channel would take the trainer's
 * messages, see [AntFecTrainer]), and the app's trainer sensor on, or the ride records no power.
 */
object ErgSetup {

    enum class Kind {
        // Bluetooth
        TRAINER_ON, PERMISSION, BLUETOOTH_FREE, OUR_SENSOR_OFF, READ_ONLY,
        // ANT+
        TRAINER_KNOWN, KAROO_ANT_OFF, POWER_RECORDED,
    }

    enum class State { OK, WARN, INFO }

    /** One line of the checklist. [name]: the sensor it's about, if any. */
    data class Step(val kind: Kind, val state: State, val name: String? = null)

    fun check(devices: SavedDevices, connection: ErgConnection, bluetoothPermission: Boolean): List<Step> {
        val pick = ErgController.trainerPick(devices, connection)
        val ours = devices.devices.filter { TrainerSensor.numberOf(it.id) != null }
        val oursOn = ours.firstOrNull { it.enabled }
        return if (connection == ErgConnection.BLUETOOTH) {
            val n = pick?.match?.antNumber
            // The trainer's Bluetooth side paired on the Karoo too ("Tacx Neo 22877"): it can take the
            // trainer's only Bluetooth connection.
            val btTwin = n?.let { num ->
                devices.devices.firstOrNull { it.enabled && it.connectionType == "BLE" && "TYPE_TRAINER_ID" !in it.supportedDataTypes && FecBle.nameMatches(it.name, num) }
            }
            listOfNotNull(
                Step(Kind.TRAINER_ON, if (pick != null) State.OK else State.WARN, pick?.name),
                Step(Kind.PERMISSION, if (bluetoothPermission) State.OK else State.WARN),
                if (pick != null && n != null) Step(Kind.BLUETOOTH_FREE, if (btTwin == null) State.OK else State.WARN, btTwin?.name) else null,
                oursOn?.let { Step(Kind.OUR_SENSOR_OFF, State.WARN, it.name) },
                Step(Kind.READ_ONLY, State.INFO),
            )
        } else {
            val native = devices.devices.filter { "TYPE_TRAINER_ID" in it.supportedDataTypes && it.connectionType == "ANT_PLUS" }
            val nativeOn = pick?.let { p -> native.firstOrNull { it.enabled && it.id.substringBefore('-').toIntOrNull() == p.match.antNumber } }
            // Another power sensor on the profile also records the ride (a power meter, the trainer's Bluetooth power).
            val otherPower = devices.devices.firstOrNull {
                it.enabled && TrainerSensor.numberOf(it.id) == null && "TYPE_TRAINER_ID" !in it.supportedDataTypes &&
                    it.supportedDataTypes.any { t -> t == "TYPE_POWER_ID" }
            }
            listOfNotNull(
                Step(Kind.TRAINER_KNOWN, if (pick != null) State.OK else State.WARN, pick?.name),
                if (pick != null) Step(Kind.KAROO_ANT_OFF, if (nativeOn == null) State.OK else State.WARN, nativeOn?.name) else null,
                when {
                    oursOn != null -> Step(Kind.POWER_RECORDED, State.OK, oursOn.name)
                    otherPower != null -> Step(Kind.POWER_RECORDED, State.INFO, otherPower.name)
                    else -> Step(Kind.POWER_RECORDED, State.WARN)
                },
            )
        }
    }

    /** The first thing in the way, for the start screen. */
    fun firstProblem(steps: List<Step>): Step? = steps.firstOrNull { it.state == State.WARN }
}
