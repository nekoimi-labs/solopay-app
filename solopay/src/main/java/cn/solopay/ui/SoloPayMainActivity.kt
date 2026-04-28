package cn.solopay.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.solopay.CredentialsStore
import cn.solopay.R
import cn.solopay.databinding.ActivitySoloPayMainBinding
import cn.solopay.service.SoloPayKeepAliveService
import com.google.android.material.navigation.NavigationBarView

class SoloPayMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySoloPayMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cn.solopay.SoloPay.ensureInit(applicationContext)
        binding = ActivitySoloPayMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.solopayBottom.setOnItemSelectedListener(
            NavigationBarView.OnItemSelectedListener { item ->
                val frag = when (item.itemId) {
                    R.id.solopay_nav_home -> HomeFragment()
                    R.id.solopay_nav_bind -> BindFragment()
                    R.id.solopay_nav_perm -> PermissionsFragment()
                    R.id.solopay_nav_records -> RecordsFragment()
                    else -> return@OnItemSelectedListener false
                }
                supportFragmentManager.beginTransaction()
                    .replace(R.id.solopay_container, frag)
                    .commit()
                true
            },
        )

        if (savedInstanceState == null) {
            binding.solopayBottom.selectedItemId = R.id.solopay_nav_home
            supportFragmentManager.beginTransaction()
                .replace(R.id.solopay_container, HomeFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        if (CredentialsStore(this).isBound()) {
            SoloPayKeepAliveService.start(this)
        }
    }
}
