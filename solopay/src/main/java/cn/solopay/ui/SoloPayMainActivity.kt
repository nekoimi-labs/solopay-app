package cn.solopay.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import cn.solopay.CredentialsStore
import cn.solopay.R
import cn.solopay.databinding.ActivitySoloPayMainBinding
import cn.solopay.service.SoloPayKeepAliveService
import com.google.android.material.tabs.TabLayout
import com.xuexiang.xui.XUI

class SoloPayMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySoloPayMainBinding

    private lateinit var homeFragment: HomeFragment
    private lateinit var bindFragment: BindFragment
    private lateinit var permissionsFragment: PermissionsFragment
    private lateinit var recordsFragment: RecordsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        XUI.initTheme(this)
        super.onCreate(savedInstanceState)
        cn.solopay.SoloPay.ensureInit(applicationContext)
        binding = ActivitySoloPayMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            bindFragment = BindFragment()
            permissionsFragment = PermissionsFragment()
            recordsFragment = RecordsFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.solopay_container, homeFragment, TAG_HOME)
                .add(R.id.solopay_container, bindFragment, TAG_BIND).hide(bindFragment)
                .add(R.id.solopay_container, permissionsFragment, TAG_PERM).hide(permissionsFragment)
                .add(R.id.solopay_container, recordsFragment, TAG_REC).hide(recordsFragment)
                .commitNow()
        } else {
            homeFragment = supportFragmentManager.findFragmentByTag(TAG_HOME) as HomeFragment
            bindFragment = supportFragmentManager.findFragmentByTag(TAG_BIND) as BindFragment
            permissionsFragment = supportFragmentManager.findFragmentByTag(TAG_PERM) as PermissionsFragment
            recordsFragment = supportFragmentManager.findFragmentByTag(TAG_REC) as RecordsFragment
        }

        binding.solopayTabs.removeAllTabs()
        binding.solopayTabs.addTab(binding.solopayTabs.newTab().setText(R.string.solopay_home))
        binding.solopayTabs.addTab(binding.solopayTabs.newTab().setText(R.string.solopay_bind))
        binding.solopayTabs.addTab(binding.solopayTabs.newTab().setText(R.string.solopay_permissions))
        binding.solopayTabs.addTab(binding.solopayTabs.newTab().setText(R.string.solopay_records))

        binding.solopayTabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    showPage(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}

                override fun onTabReselected(tab: TabLayout.Tab) {}
            },
        )

        val initialTab = savedInstanceState?.getInt(STATE_TAB, 0) ?: 0
        binding.solopayTabs.getTabAt(initialTab)?.let { binding.solopayTabs.selectTab(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, binding.solopayTabs.selectedTabPosition)
    }

    private fun showPage(index: Int) {
        val tx = supportFragmentManager.beginTransaction()
        val pages = listOf<Fragment>(homeFragment, bindFragment, permissionsFragment, recordsFragment)
        pages.forEachIndexed { i, f ->
            if (i == index) tx.show(f) else tx.hide(f)
        }
        tx.commitNowAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        if (CredentialsStore(this).isBound()) {
            SoloPayKeepAliveService.start(this)
        }
    }

    companion object {
        private const val TAG_HOME = "solopay_home"
        private const val TAG_BIND = "solopay_bind"
        private const val TAG_PERM = "solopay_perm"
        private const val TAG_REC = "solopay_rec"
        private const val STATE_TAB = "solopay_selected_tab"
    }
}
