package com.example.capstone_frontend

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService
import com.google.firebase.ktx.Firebase
import com.kakao.sdk.user.UserApiClient
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket

class MyFirebaseInstanceIDService : FirebaseInstanceIdService() {
    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID
     * token
     * is initially generated so this is where you would retrieve the token.  */
    lateinit var mSocket: Socket // for socket
    private val gson = Gson()

    // [START refresh_token]
    override fun onTokenRefresh() {
        // Get updated InstanceID token.
        val refreshedToken = FirebaseInstanceId.getInstance().token
        Log.d(
            TAG,
            "Refreshed token: $refreshedToken"
        )
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        sendRegistrationToServer(refreshedToken)
    }
    // [END refresh_token]
    /**
     * token 값을 서버에 등록할 때 사용  */
    private fun sendRegistrationToServer(token: String?) {

    }

    companion object {
        private const val TAG = "MyFirebaseIIDService"
    }
}