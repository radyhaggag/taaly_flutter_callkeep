package co.doneservices.callkeep

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.KeyguardManager.KeyguardLock
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.ACTION_CALL_INCOMING
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_AVATAR
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_BACKGROUND_URL
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_ACCENT_COLOR
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_DURATION
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_INCOMING_DATA
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_CALLER_NAME
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_CONTENT_TITLE
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_HANDLE
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_HEADERS
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_LOGO
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import kotlin.math.abs
import okhttp3.OkHttpClient
import com.squareup.picasso.OkHttp3Downloader
import android.view.ViewGroup.MarginLayoutParams
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.text.TextUtils
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_ACCEPT_TEXT
import co.doneservices.callkeep.CallKeepBroadcastReceiver.Companion.EXTRA_CALLKEEP_DECLINE_TEXT
import android.util.Log

class IncomingCallActivity : Activity() {

    companion object {

        const val ACTION_ENDED_CALL_INCOMING =
                "co.doneservices.callkeep.ACTION_ENDED_CALL_INCOMING"

        fun getIntent(context: Context, data: Bundle) = Intent(ACTION_CALL_INCOMING).apply {
            action = "${context.packageName}.${ACTION_CALL_INCOMING}"
            putExtra(EXTRA_CALLKEEP_INCOMING_DATA, data)
            flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        fun getIntentEnded(context: Context): Intent {
            return Intent("${context.packageName}.$ACTION_ENDED_CALL_INCOMING")
        }
    }

    inner class EndedCallKeepBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isFinishing) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask()
                } else {
                    finish()
                }
            }
        }
    }

    private var endedCallKeepBroadcastReceiver = EndedCallKeepBroadcastReceiver()

    private lateinit var tvCallerName: TextView
    private lateinit var callerImage: CircleImageView
    private lateinit var btnAnswer: LinearLayout
    private lateinit var btnDecline: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        setTransparentStatusAndNavigation()
        setContentView(R.layout.activity_call_incoming)
        initView()
        updateViewWithIncomingIntentData(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                endedCallKeepBroadcastReceiver,
                IntentFilter("${packageName}.${ACTION_ENDED_CALL_INCOMING}"),
                Context.RECEIVER_EXPORTED,
            )
        } else {
            registerReceiver(
                endedCallKeepBroadcastReceiver,
                IntentFilter("${packageName}.${ACTION_ENDED_CALL_INCOMING}")
            )
        }
    }

    private fun wakeLockRequest(duration: Long) {

        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CallKeep:PowerManager"
        )
        wakeLock.acquire(duration)
    }

    private fun setTransparentStatusAndNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                            or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, true
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(
                    (WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                            or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION), false
            )
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun setWindowFlag(bits: Int, on: Boolean) {
        val win: Window = window
        val winParams: WindowManager.LayoutParams = win.attributes
        if (on) {
            winParams.flags = winParams.flags or bits
        } else {
            winParams.flags = winParams.flags and bits.inv()
        }
        win.attributes = winParams
    }


    private fun updateViewWithIncomingIntentData(intent: Intent) {
        val data = intent.extras?.getBundle(EXTRA_CALLKEEP_INCOMING_DATA)
        if (data == null) finish()

        tvCallerName.text = data?.getString("EXTRA_CALLKEEP_CALLER_NAME", "")
                
        if (data != null) {
            Log.d("CallKeep", "Incoming Call Data:")
        
            // Safely get the key set and iterate
            data.keySet()?.forEach { key ->
                val value = data.get(key) // Retrieve the value for each key
                Log.d("CallKeepDebug", "$key: $value")
            }
        } else {
            Log.d("CallKeepDebug", "No incoming data found in the Bundle")
        }        
    
        // Get the EXTRA_CALLKEEP_EXTRA field as a Bundle or Map
        val extra = data?.getSerializable("EXTRA_CALLKEEP_EXTRA") as? HashMap<String, Any?>
        val imageUrl = extra?.get("image") as? String
        
        Log.d("CallKeep", "Image URL: $imageUrl")

        if (!imageUrl.isNullOrEmpty()) {
            Picasso.get()
                .load(imageUrl)
                .placeholder(R.drawable.user_placeholder)
                .error(R.drawable.user_placeholder)
                .into(callerImage);
        } else {
            
            callerImage.setImageResource(R.drawable.user_placeholder) // Fallback if URL is empty
        }

        val duration = data?.getLong(EXTRA_CALLKEEP_DURATION, 0L) ?: 0L
        wakeLockRequest(duration)

        finishTimeout(data, duration)
    }

    private fun finishTimeout(data: Bundle?, duration: Long) {
        val currentSystemTime = System.currentTimeMillis()
        val timeStartCall =
                data?.getLong(CallKeepNotificationManager.EXTRA_TIME_START_CALL, currentSystemTime)
                        ?: currentSystemTime

        val timeOut = duration - abs(currentSystemTime - timeStartCall)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask()
                } else {
                    finish()
                }
            }
        }, timeOut)
    }

    private fun initView() {
        tvCallerName = findViewById(R.id.tvCallerName)
        callerImage = findViewById(R.id.callerImage)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnDecline = findViewById(R.id.btnDecline)

        animateAcceptCall()

        btnAnswer.setOnClickListener {
            onAcceptClick()
        }
        btnDecline.setOnClickListener {
            onDeclineClick()
        }
    }

    private fun animateAcceptCall() {
        val shakeAnimation =
                AnimationUtils.loadAnimation(this@IncomingCallActivity, R.anim.shake_anim)
        btnAnswer.animation = shakeAnimation
    }


    private fun onAcceptClick() {
        val data = intent.extras?.getBundle(EXTRA_CALLKEEP_INCOMING_DATA)
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.cloneFilter()
        if (isTaskRoot) {
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } else {
            intent?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (intent != null) {
            val intentTransparent = TransparentActivity.getIntentAccept(this@IncomingCallActivity, data)
            startActivities(arrayOf(intent, intentTransparent))
        } else {
            val acceptIntent = CallKeepBroadcastReceiver.getIntentAccept(this@IncomingCallActivity, data)
            sendBroadcast(acceptIntent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    private fun onDeclineClick() {
        val data = intent.extras?.getBundle(EXTRA_CALLKEEP_INCOMING_DATA)
        val intent =
                CallKeepBroadcastReceiver.getIntentDecline(this@IncomingCallActivity, data)
        sendBroadcast(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    private fun getPicassoInstance(context: Context, headers: HashMap<String, Any?>): Picasso {
        val client = OkHttpClient.Builder()
                .addNetworkInterceptor { chain ->
                    val newRequestBuilder: okhttp3.Request.Builder = chain.request().newBuilder()
                    for ((key, value) in headers) {
                        newRequestBuilder.addHeader(key, value.toString())
                    }
                    chain.proceed(newRequestBuilder.build())
                }
                .build()
        return Picasso.Builder(context)
                .downloader(OkHttp3Downloader(client))
                .build()
    }

    override fun onDestroy() {
        unregisterReceiver(endedCallKeepBroadcastReceiver)
        super.onDestroy()
    }

    override fun onBackPressed() {}


}
