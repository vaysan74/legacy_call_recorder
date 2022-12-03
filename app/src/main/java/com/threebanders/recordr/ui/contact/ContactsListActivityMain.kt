package com.threebanders.recordr.ui.contact

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.navigation.NavigationView
import com.threebanders.recordr.R
import com.threebanders.recordr.ui.BaseActivity
import com.threebanders.recordr.ui.MainViewModel
import com.threebanders.recordr.ui.help.HelpActivity
import com.threebanders.recordr.ui.settings.SettingsActivity
import com.threebanders.recordr.ui.setup.SetupActivity
import core.threebanders.recordr.MyService

class ContactsListActivityMain : BaseActivity() {
    private var fm: FragmentManager? = null
    private var unassignedToInsert: Fragment? = null
    private var viewModel: MainViewModel? = null

    override fun createFragment(): Fragment {
        return UnassignedRecordingsFragment()
    }

    override fun onResume() {
        super.onResume()

        checkIfThemeChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contacts_list_activity)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        if (!isMyServiceRunning(MyService::class.java)) {
            val intent = Intent("android.settings.ACCESSIBILITY_SETTINGS")
            startActivityForResult(intent, ACCESSIBILITY_SETTINGS)
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayShowTitleEnabled(false)
        val title = findViewById<TextView>(R.id.actionbar_title)
        title.text = getString(R.string.app_name)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        val settings = prefs
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val eulaNotAccepted =
            if (settings.getBoolean(HAS_ACCEPTED_EULA, false)) 0 else EULA_NOT_ACCEPTED
        var permsNotGranted = 0
        var powerOptimized = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permsNotGranted = if (checkPermissions()) 0 else PERMS_NOT_GRANTED
            powerOptimized =
                if (pm.isIgnoringBatteryOptimizations(packageName)) 0 else POWER_OPTIMIZED
        }
        val checkResult = eulaNotAccepted or permsNotGranted or powerOptimized
        if (checkResult != 0) {
            val setupIntent = Intent(this, SetupActivity::class.java)
            setupIntent.putExtra(SETUP_ARGUMENT, checkResult)
            startActivityForResult(setupIntent, SETUP_ACTIVITY)
        } else setupRecorderFragment()

        if (savedInstanceState == null) insertFragment(R.id.contacts_list_fragment_container)
        val hamburger = findViewById<ImageButton>(R.id.hamburger)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        val navWidth: Int
        val pixelsDp =
            (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        navWidth =
            if (pixelsDp >= 480) (resources.displayMetrics.widthPixels * 0.4).toInt()
            else (resources.displayMetrics.widthPixels * 0.8).toInt()
        val params = navigationView.layoutParams as DrawerLayout.LayoutParams
        params.width = navWidth
        navigationView.layoutParams = params
        hamburger.setOnClickListener { drawer.openDrawer(GravityCompat.START) }
        navigationView.setNavigationItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.settings -> startActivity(
                    Intent(
                        this@ContactsListActivityMain,
                        SettingsActivity::class.java
                    )
                )
                R.id.help -> startActivity(
                    Intent(
                        this@ContactsListActivityMain,
                        HelpActivity::class.java
                    )
                )
                R.id.rate_app -> {
                    //https://stackoverflow.com/questions/10816757/rate-this-app-link-in-google-play-store-app-on-the-phone
                    //String packageName = "net.synapticweb.callrecorder.gpcompliant.full";
                    val uri = Uri.parse("market://details?id=$packageName")
                    val goToMarket = Intent(Intent.ACTION_VIEW, uri)
                    goToMarket.addFlags(
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    )
                    try {
                        startActivity(goToMarket)
                    } catch (e: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("http://play.google.com/store/apps/details?id=$packageName")
                            )
                        )
                    }
                }
            }
            drawer.closeDrawers()
            true
        }
    }

    private fun setupRecorderFragment() {
        unassignedToInsert = UnassignedRecordingsFragment()
        fm = supportFragmentManager
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (resultCode == RESULT_OK && requestCode == SETUP_ACTIVITY) {
            setupRecorderFragment()
            if (data!!.getBooleanExtra(SetupActivity.EXIT_APP, true)) finish()
        }
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onBackPressed() {
        MaterialDialog.Builder(this)
            .title(R.string.exit_app_title)
            .icon(resources.getDrawable(R.drawable.question_mark))
            .content(R.string.exit_app_message)
            .positiveText(android.R.string.ok)
            .negativeText(android.R.string.cancel)
            .onPositive { _: MaterialDialog, _: DialogAction ->
                super@ContactsListActivityMain.onBackPressed()
            }
            .show()
    }

    private fun checkPermissions(): Boolean {
        val phoneState =
            (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED)
        val recordAudio = (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED)
        val readContacts =
            (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED)
        val readStorage =
            (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED)
        val writeStorage =
            (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED)
        return phoneState && recordAudio && readContacts && readStorage && writeStorage
    }

    companion object {
        private const val SETUP_ACTIVITY = 3
        const val HAS_ACCEPTED_EULA = "has_accepted_eula"
        const val EULA_NOT_ACCEPTED = 1
        const val PERMS_NOT_GRANTED = 2
        const val POWER_OPTIMIZED = 4
        const val SETUP_ARGUMENT = "setup_arg"
        private const val ACCESSIBILITY_SETTINGS = 1991
    }
}