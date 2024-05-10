package it.dii.unipi.masss.noisemapper

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.google.gson.Gson
import com.kontakt.sdk.android.ble.manager.ProximityManagerFactory
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleIBeaconListener
import com.kontakt.sdk.android.common.profile.IBeaconDevice
import com.kontakt.sdk.android.common.profile.IBeaconRegion
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class BLEScanner (val activity: NoiseDetection) {
    private val proximityManager = ProximityManagerFactory.create(activity)
    private var lastUpdate: Long = 0
    private var json_array_request: ArrayList<Map<String, Any>> = ArrayList()

    // obtain flush window from numbers.xml
    private val FLUSH_WINDOW = activity.resources.getInteger(R.integer.FLUSH_SAMPLES_WINDOW)

    init {
        setupProximityManager(activity)
    }

    fun setupProximityManager(activity: NoiseDetection) {
        proximityManager.setIBeaconListener(object : SimpleIBeaconListener() {
            override fun onIBeaconDiscovered(device: IBeaconDevice, region: IBeaconRegion) {
                // print the discovered iBeacon device
                println("iBeacon: Discovered iBeacon device: $device")
            }

            override fun onIBeaconLost(device: IBeaconDevice, region: IBeaconRegion) {
                // print the lost iBeacon device
                println("iBeacon: Lost iBeacon device: $device")
            }

            override fun onIBeaconsUpdated(
                beacons: MutableList<IBeaconDevice>,
                region: IBeaconRegion
            ) {
                // sort the iBeacon devices by rssi
                val mutableBeacons =
                    beacons.filter { it.uniqueId in activity.bleConfig.beaconRoomMap.mapping.keys }
                        .toMutableList()
                if (mutableBeacons.isEmpty()) {
                    return
                }
                mutableBeacons.sortBy { it.rssi }
                val tonino = mutableBeacons[0]
                val nearest_room =
                    activity.bleConfig.beaconRoomMap?.mapping?.get(tonino.uniqueId) ?: "Unknown"
                // print the updated iBeacon devices
                println("iBeacon: Updated iBeacon devices: $beacons")
                activity.findViewById<ListView>(R.id.beacon_list).adapter = BeaconAdapter(beacons)
                val average_noise =
                    activity.map_noise_level.filter { it.key > lastUpdate && it.value != Double.NEGATIVE_INFINITY }.values.toList()
                        .average()
                activity.findViewById<TextView>(R.id.sensing_on_info).text =
                    "Average noise level: $average_noise"
                lastUpdate = System.currentTimeMillis()
                pushUpdate(nearest_room, average_noise, tonino)
            }
        })
    }

    private fun flushRequest() {
        //val json_array = generate_json_from_arraylist()
        val json = Gson()
        val json_array = json.toJson(json_array_request)
        //send the array of json, then clear it
        send_json_array(json_array)
        json_array_request.clear()
    }

    private fun pushUpdate(nearest_room: String?, average_noise: Double, tonino: IBeaconDevice) {


        //push json in the queue
        val m = mapOf("room" to nearest_room, "noise" to average_noise) as Map<String, Any>
        json_array_request.add(m)

        //if the array gets gets to a certain size, all the json ar sent to server server
        if (json_array_request.size == FLUSH_WINDOW) {
            flushRequest()
        }
    }

    private fun send_json_array(json: String) {

        // push the updated iBeacon devices to endpoint /beacons
        val url = activity.getString(R.string.serverURL) + "/measurements"

        println("Pushing $json to $url")
        val client = OkHttpClient()
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("NoiseMapper", "Failed to push update")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("NoiseMapper", "Failed to push update")
            }
        })
    }


    class BeaconAdapter(private val beacons: MutableList<IBeaconDevice>) : BaseAdapter() {

        override fun getCount(): Int {
            return beacons.size
        }

        override fun getItem(position: Int): Any {
            return beacons[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val beacon = beacons[position]
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(R.layout.beacon_item, parent, false)
            view.findViewById<TextView>(R.id.beacon_id).text = beacon.uniqueId
            view.findViewById<TextView>(R.id.beacon_distance).text =
                beacon.distance.toString() + "m"
            return view
        }

    }

    fun stopScanning() {
        proximityManager.stopScanning()
        if (json_array_request.isNotEmpty()) {

        }
        flushRequest() // this is to send the last batch of data also if the window is not full
    }

    fun startScanning() {
        proximityManager.connect {
            proximityManager.startScanning()
        }
    }

}
