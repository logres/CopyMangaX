package com.crow.module_main.ui.fragment

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Base64
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.crow.base.copymanga.BaseEventEnum
import com.crow.base.copymanga.BaseStrings
import com.crow.base.copymanga.BaseUserConfig
import com.crow.base.copymanga.entity.Fragments
import com.crow.base.tools.coroutine.FlowBus
import com.crow.base.tools.coroutine.launchDelay
import com.crow.base.tools.extensions.BASE_ANIM_200L
import com.crow.base.tools.extensions.BASE_ANIM_300L
import com.crow.base.tools.extensions.doOnClickInterval
import com.crow.base.tools.extensions.isLatestVersion
import com.crow.base.tools.extensions.navigateToWithBackStack
import com.crow.base.tools.extensions.newMaterialDialog
import com.crow.base.tools.extensions.onCollect
import com.crow.base.tools.extensions.toast
import com.crow.base.ui.fragment.BaseMviFragment
import com.crow.base.ui.view.event.BaseEvent
import com.crow.base.ui.viewmodel.doOnError
import com.crow.base.ui.viewmodel.doOnErrorInCoroutine
import com.crow.base.ui.viewmodel.doOnResult
import com.crow.base.ui.viewmodel.doOnResultInCoroutine
import com.crow.module_bookshelf.ui.fragment.BookshelfFragment
import com.crow.module_discover.ui.fragment.DiscoverComicFragment
import com.crow.module_home.ui.fragment.NewHomeFragment
import com.crow.module_main.R
import com.crow.module_main.databinding.MainFragmentContainerBinding
import com.crow.module_main.databinding.MainUpdateLayoutBinding
import com.crow.module_main.databinding.MainUpdateUrlLayoutBinding
import com.crow.module_main.model.intent.AppIntent
import com.crow.module_main.model.resp.MainAppUpdateResp
import com.crow.module_main.ui.adapter.ContainerAdapter
import com.crow.module_main.ui.adapter.MainAppUpdateRv
import com.crow.module_main.ui.viewmodel.MainViewModel
import com.crow.module_user.ui.viewmodel.UserViewModel
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.core.qualifier.named

/*************************
 * @Machine: RedmiBook Pro 15 Win11
 * @Path: module_home/src/main/kotlin/com/crow/module_home/ui/fragment
 * @Time: 2023/3/7 14:00
 * @Author: CrowForKotlin
 * @Description: HomeContainerFragment
 * @formatter:on
 **************************/
class ContainerFragment : BaseMviFragment<MainFragmentContainerBinding>() {

    /** ● 碎片容器适配器 */
    private var mContainerAdapter: ContainerAdapter? = null

    /** ● （Activity级别）容器VM */
    private val mContainerVM by sharedViewModel<MainViewModel>()

    /** ● （Activity级别）用户VM */
    private val mUserVM by sharedViewModel<UserViewModel>()

    /** ● 碎片集 */
    private val mFragmentList by lazy { mutableListOf<Fragment>(NewHomeFragment(), DiscoverComicFragment(), BookshelfFragment()) }

    /**
     * ● 手势检测
     *
     * ● 2023-09-08 01:04:40 周五 上午
     */
    private val mGestureDetector by lazy {
        GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                when(mBinding.mainViewPager.currentItem) {
                    1 -> childFragmentManager.setFragmentResult("onDoubleTap_Discover_Comic", arguments ?: bundleOf())
                    2 -> childFragmentManager.setFragmentResult("onDoubleTap_Bookshelf", arguments ?: bundleOf())
                }
                return super.onDoubleTap(e)
            }
        })
    }

    /**
     * ● Global BaseEvent
     *
     * ● 2023-09-16 18:37:52 周六 下午
     */
    private val mEvent: BaseEvent by lazy { BaseEvent.getSIngleInstance() }

    /** ● 获取ViewBinding */
    override fun getViewBinding(inflater: LayoutInflater) = MainFragmentContainerBinding.inflate(inflater)

    /** ● 初始化观察者 */
    override fun initObserver(saveInstanceState: Bundle?) {

        // 用户信息 收集
        mUserVM.userInfo.onCollect(this) {

            // 加载 Icon  无链接或加载失败 则默认Drawable
            mUserVM.doLoadIcon(mContext, true) { resource -> FlowBus.with<Drawable>(BaseEventEnum.SetIcon.name).post(this, resource) }

            // 初始化 用户Tokne
            BaseUserConfig.CURRENT_USER_TOKEN = it?.mToken ?: return@onCollect
        }

        // 观察ContainerVM
        mContainerVM.onOutput { intent ->
            when(intent) {
                is AppIntent.GetDynamicSite -> {
                    intent.mBaseViewState
                        .doOnErrorInCoroutine { _, _ -> mContainerVM.saveAppConfig() }
                        .doOnResultInCoroutine {
                            BaseStrings.URL.COPYMANGA = Base64.decode(intent.siteResp!!.mSiteList!!.first()!!.mEncodeSite, Base64.DEFAULT).decodeToString()
                            mContainerVM.saveAppConfig()
                        }
                }
                is AppIntent.GetUpdateInfo -> {
                    intent.mBaseViewState
                        .doOnError { _, _ -> toast(getString(R.string.main_update_error)) }
                        .doOnResult { doUpdateChecker(saveInstanceState, intent.appUpdateResp!!) }
                }
            }
        }
    }

    /** ● 初始化视图 */
    override fun initView(savedInstanceState: Bundle?) {

        // 设置 内边距属性 实现沉浸式效果
        immersionRoot()

        // 适配器 初始化 （设置Adapter、预加载页数）
        mBinding.mainViewPager.offscreenPageLimit = 3
        mBinding.mainViewPager.isUserInputEnabled = false
        mBinding.mainViewPager.adapter = ContainerAdapter(mFragmentList, childFragmentManager, viewLifecycleOwner.lifecycle)
    }

    /**
     * ● Lifecycle OnViewCreated
     *
     * ● 2023-09-10 20:01:04 周日 下午
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            mContainerVM.mIsRestarted = true
            if (!isHidden) onNotifyPage()
        } else {
            saveItemPageID(0)
            mEvent.setBoolean(mFragmentList[0].hashCode().toString(), true)
        }
    }

    /**
     * ● Lifecycle onCreate 内存重启逻辑
     *
     * ● 2023-07-02 20:22:37 周日 下午
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mContainerVM.input(AppIntent.GetUpdateInfo())
    }

    /**
     * ● Lifecycle onDestroyView
     *
     * ● 2023-07-02 20:22:59 周日 下午
     */
    override fun onDestroyView() {
        super.onDestroyView()
        mFragmentList.forEach { mEvent.remove(it.hashCode().toString()) }
    }

    /** ● 当视图隐藏状态发生改变 并触发 */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)

        if (hidden) return

        // 可见： 当返回ContainerFragment时回调此方法 则通知设置Icon
        mUserVM.doLoadIcon(mContext, true) { resource -> FlowBus.with<Drawable>(BaseEventEnum.SetIcon.name).post(this, resource) }

        onNotifyPage()
    }

    /** ● 初始化监听器 */
    @SuppressLint("ClickableViewAccessibility")
    override fun initListener() {

        // 登录类别
        parentFragmentManager.setFragmentResultListener(BaseEventEnum.LoginCategories.name, viewLifecycleOwner) { _, bundle ->
            if (bundle.getBoolean("isLogout", false)) {
                mUserVM.doClearUserInfo()
            }
            if (bundle.getBoolean(BaseStrings.ENABLE_DELAY, false)) {
                launchDelay(BASE_ANIM_200L) {
                    childFragmentManager.setFragmentResult(BaseEventEnum.LoginCategories.name,  bundleOf(BaseStrings.ID to mBinding.mainViewPager.currentItem))
                }
            } else {
                childFragmentManager.setFragmentResult(BaseEventEnum.LoginCategories.name,  bundleOf(BaseStrings.ID to mBinding.mainViewPager.currentItem))
            }
        }

        // 子Fragment 清除用户信息
        childFragmentManager.setFragmentResultListener(BaseEventEnum.ClearUserInfo.name, viewLifecycleOwner) { _, _ ->
            mUserVM.doClearUserInfo()
        }

        // 设置底部导航视图点击Item可见
        mBinding.mainBottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.main_menu_homepage -> doSwitchFragment(0)
                R.id.main_menu_discovery_comic -> doSwitchFragment(1)
                R.id.main_menu_bookshelf -> doSwitchFragment(2)
                // R.id.main_menu_discovery_novel -> doSwitchFragment(2)
            }
            true
        }

        // VP 页面回调
        mBinding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if (mEvent.getBoolean(mFragmentList[mBinding.mainViewPager.currentItem].hashCode().toString()) == true) return
                    else mEvent.setBoolean(mFragmentList[mBinding.mainViewPager.currentItem].hashCode().toString(), true)
                    val bundle = bundleOf(BaseStrings.ID to mBinding.mainViewPager.currentItem, BaseStrings.ENABLE_DELAY to false)
                    childFragmentManager.setFragmentResult(NewHomeFragment.HOME, bundle)
                    childFragmentManager.setFragmentResult(DiscoverComicFragment.COMIC, bundle)
                    childFragmentManager.setFragmentResult(BookshelfFragment.BOOKSHELF, bundle)
                }
            }
        })

        // Item onTouchEvent 漫画
        mBinding.mainBottomNavigation.setItemOnTouchListener(R.id.main_menu_discovery_comic) { _, event ->
            mGestureDetector.onTouchEvent(event)
        }

        // Item onTouchEvent 书架
        mBinding.mainBottomNavigation.setItemOnTouchListener(R.id.main_menu_bookshelf) { _, event ->
            mGestureDetector.onTouchEvent(event)
        }
    }

    /**
     * ● 通知页面更新
     *
     * ● 2023-06-29 01:28:48 周四 上午
     */
    private fun onNotifyPage() {
        if (mContainerVM.mIsRestarted) {
            mContainerVM.mIsRestarted = false
            val bundle = bundleOf(BaseStrings.ID to (arguments?.getInt(BaseStrings.ID) ?: 0).also {
                saveItemPageID(it)
                mEvent.setBoolean(mFragmentList[it].hashCode().toString(), true)
            }, BaseStrings.ENABLE_DELAY to true)
            childFragmentManager.setFragmentResult(NewHomeFragment.HOME, bundle)
            childFragmentManager.setFragmentResult(DiscoverComicFragment.COMIC, bundle)
            childFragmentManager.setFragmentResult(BookshelfFragment.BOOKSHELF, bundle)
        }
    }

    /** ● 执行选择Fragment */
    private fun doSwitchFragment(position: Int) {
        if (mBinding.mainViewPager.currentItem != position) mBinding.mainViewPager.setCurrentItem(position, true)
        saveItemPageID(position)
    }

    /**
     * ● 保存当前页面ID
     *
     * ● 2023-07-02 20:21:35 周日 下午
     */
    private fun saveItemPageID(position: Int) {
        if (arguments == null) {
            val bundle = Bundle()
            bundle.putInt(BaseStrings.ID, position)
            arguments = bundle
        } else {
            arguments!!.putInt(BaseStrings.ID, position)
        }
    }

    /**
     * ● 检查更新
     *
     * ● 2023-09-16 18:32:59 周六 下午
     * @param savedInstanceState 检查内存重启状态
     */
    private fun doUpdateChecker(savedInstanceState: Bundle?, appUpdateResp: MainAppUpdateResp) {
        val update = appUpdateResp.mUpdates.first()
        if (savedInstanceState != null) {
            mEvent.setBoolean("INIT_UPDATE", true)
            if (!appUpdateResp.mForceUpdate) return
        }
        if (isLatestVersion(latest = update.mVersionCode.toLong())) return run {
            if (mEvent.getBoolean("INIT_UPDATE") == true) toast(getString(R.string.main_update_tips))
            mEvent.setBoolean("INIT_UPDATE", true)
        }
        mEvent.setBoolean("INIT_UPDATE", true)
        if (isHidden) return
        val updateBinding = MainUpdateLayoutBinding.inflate(layoutInflater)
        val updateDialog = mContext.newMaterialDialog { dialog ->
            dialog.setCancelable(false)
            dialog.setView(updateBinding.root)
        }
        val screenHeight = resources.displayMetrics.heightPixels / 3
        (updateBinding.mainUpdateScrollview.layoutParams as ConstraintLayout.LayoutParams).matchConstraintMaxHeight = screenHeight
        updateBinding.mainUpdateCancel.isInvisible = appUpdateResp.mForceUpdate
        updateBinding.mainUpdateTitle.text = update.mTitle
        updateBinding.mainUpdateText.text = update.mContent
        updateBinding.mainUpdateTime.text = getString(R.string.main_update_time, update.mTime)
        if (!appUpdateResp.mForceUpdate) { updateBinding.mainUpdateCancel.doOnClickInterval { updateDialog.dismiss() } }
        updateBinding.mainUpdateGo.doOnClickInterval(flagTime = BASE_ANIM_300L) {
            updateDialog.dismiss()
            val updateUrlBinding = MainUpdateUrlLayoutBinding.inflate(layoutInflater)
            val updateUrlDialog = mContext.newMaterialDialog {
                it.setCancelable(false)
                it.setView(updateUrlBinding.root)
            }
            (updateUrlBinding.mainUpdateUrlScrollview.layoutParams as ConstraintLayout.LayoutParams).matchConstraintMaxHeight = screenHeight
            updateUrlBinding.mainUpdateUrlCancel.isInvisible = appUpdateResp.mForceUpdate
            updateUrlBinding.mainUpdateUrlRv.adapter = MainAppUpdateRv(update.mUrl)
            if (!appUpdateResp.mForceUpdate) { updateUrlBinding.mainUpdateUrlCancel.doOnClickInterval { updateUrlDialog.dismiss() } }
        }
        updateBinding.mainUpdateHistory.doOnClickInterval(flagTime = BASE_ANIM_300L) {
            parentFragmentManager.navigateToWithBackStack(
                R.id.app_main_fcv,
                parentFragmentManager.findFragmentByTag(Fragments.Container.name)!!,
                get<Fragment>(named(Fragments.UpdateHistory.name)).also { it.arguments = bundleOf("force_update" to appUpdateResp.mForceUpdate) },
                Fragments.UpdateHistory.name,
                Fragments.UpdateHistory.name
            )
            updateDialog.dismiss()
        }
    }
}