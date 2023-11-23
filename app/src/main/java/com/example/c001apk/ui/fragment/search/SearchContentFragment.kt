package com.example.c001apk.ui.fragment.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.ThemeUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.adapter.AppAdapter
import com.example.c001apk.databinding.FragmentSearchFeedBinding
import com.example.c001apk.ui.fragment.minterface.IOnLikeClickListener
import com.example.c001apk.ui.fragment.minterface.IOnSearchMenuClickContainer
import com.example.c001apk.ui.fragment.minterface.IOnSearchMenuClickListener
import com.example.c001apk.ui.fragment.minterface.OnPostFollowListener
import com.example.c001apk.util.BlackListUtil
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.view.LinearItemDecoration
import com.example.c001apk.view.ninegridimageview.NineGridImageView
import com.example.c001apk.view.ninegridimageview.OnImageItemClickListener
import com.example.c001apk.viewmodel.AppViewModel

class SearchContentFragment : Fragment(), IOnLikeClickListener, OnImageItemClickListener,
    IOnSearchMenuClickListener, OnPostFollowListener {

    private lateinit var binding: FragmentSearchFeedBinding
    private val viewModel by lazy { ViewModelProvider(this)[AppViewModel::class.java] }
    private lateinit var mAdapter: AppAdapter
    private lateinit var mLayoutManager: LinearLayoutManager


    companion object {
        @JvmStatic
        fun newInstance(keyWord: String, type: String, pageType: String, pageParam: String) =
            SearchContentFragment().apply {
                arguments = Bundle().apply {
                    putString("KEYWORD", keyWord)
                    putString("TYPE", type)
                    putString("pageType", pageType)
                    putString("pageParam", pageParam)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            viewModel.keyWord = it.getString("KEYWORD")!!
            viewModel.type = it.getString("TYPE")!!
            viewModel.pageType = it.getString("pageType")!!
            viewModel.pageParam = it.getString("pageParam")!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()

        (requireParentFragment() as IOnSearchMenuClickContainer).controller = this

        if (viewModel.isInit) {
            viewModel.isInit = false
            initView()
            initData()
            initRefresh()
            initScroll()
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.isInit) {
            initView()
            initData()
            initRefresh()
            initScroll()
        }

        viewModel.searchData.observe(viewLifecycleOwner) { result ->
            if (viewModel.isNew) {
                viewModel.isNew = false

                val search = result.getOrNull()
                if (!search.isNullOrEmpty()) {
                    if (viewModel.isRefreshing)
                        viewModel.searchList.clear()
                    if (viewModel.isRefreshing || viewModel.isLoadMore) {
                        if (viewModel.type == "feed")
                            for (element in search) {
                                if (element.entityType == "feed")
                                    if (!BlackListUtil.checkUid(element.userInfo?.uid.toString()))
                                        viewModel.searchList.add(element)
                            }
                        else
                            viewModel.searchList.addAll(search)
                    }
                    mAdapter.notifyDataSetChanged()
                    mAdapter.setLoadState(mAdapter.LOADING_COMPLETE)
                } else {
                    mAdapter.setLoadState(mAdapter.LOADING_END)
                    viewModel.isEnd = true
                    result.exceptionOrNull()?.printStackTrace()
                }
                binding.indicator.isIndeterminate = false
                binding.indicator.visibility = View.GONE
                viewModel.isLoadMore = false
                viewModel.isRefreshing = false
                binding.swipeRefresh.isRefreshing = false
            }
        }

        viewModel.likeFeedData.observe(viewLifecycleOwner) { result ->
            if (viewModel.isPostLikeFeed) {
                viewModel.isPostLikeFeed = false

                val response = result.getOrNull()
                if (response != null) {
                    if (response.data != null) {
                        viewModel.searchList[viewModel.likePosition].likenum = response.data.count
                        viewModel.searchList[viewModel.likePosition].userAction?.like = 1
                        mAdapter.notifyDataSetChanged()
                    } else
                        Toast.makeText(activity, response.message, Toast.LENGTH_SHORT).show()
                } else {
                    result.exceptionOrNull()?.printStackTrace()
                }
            }
        }

        viewModel.unLikeFeedData.observe(viewLifecycleOwner) { result ->
            if (viewModel.isPostUnLikeFeed) {
                viewModel.isPostUnLikeFeed = false

                val response = result.getOrNull()
                if (response != null) {
                    if (response.data != null) {
                        viewModel.searchList[viewModel.likePosition].likenum = response.data.count
                        viewModel.searchList[viewModel.likePosition].userAction?.like = 0
                        mAdapter.notifyDataSetChanged()
                    } else
                        Toast.makeText(activity, response.message, Toast.LENGTH_SHORT).show()
                } else {
                    result.exceptionOrNull()?.printStackTrace()
                }
            }
        }

        viewModel.postFollowUnFollowData.observe(viewLifecycleOwner) { result ->
            if (viewModel.postFollowUnFollow) {
                viewModel.postFollowUnFollow = false

                val response = result.getOrNull()
                if (response != null) {
                    if (viewModel.followType) {
                        viewModel.searchList[viewModel.position].isFollow = 0
                    } else {
                        viewModel.searchList[viewModel.position].isFollow = 1
                    }
                    mAdapter.notifyDataSetChanged()
                } else {
                    result.exceptionOrNull()?.printStackTrace()
                }
            }
        }

    }

    private fun initScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (viewModel.lastVisibleItemPosition == viewModel.searchList.size
                        && !viewModel.isEnd && !viewModel.isRefreshing && !viewModel.isLoadMore
                    ) {
                        mAdapter.setLoadState(mAdapter.LOADING)
                        viewModel.isLoadMore = true
                        viewModel.page++
                        viewModel.isNew = true
                        viewModel.getSearch()

                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (viewModel.searchList.isNotEmpty()) {
                    viewModel.lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition()
                    viewModel.firstCompletelyVisibleItemPosition =
                        mLayoutManager.findFirstCompletelyVisibleItemPosition()
                }
            }
        })
    }

    @SuppressLint("RestrictedApi")
    private fun initRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            ThemeUtils.getThemeAttrColor(
                requireContext(),
                rikka.preference.simplemenu.R.attr.colorPrimary
            )
        )
        binding.swipeRefresh.setOnRefreshListener {
            binding.indicator.isIndeterminate = false
            binding.indicator.visibility = View.GONE
            refreshData()
        }
    }

    private fun initData() {
        if (viewModel.searchList.isEmpty()) {
            binding.indicator.visibility = View.VISIBLE
            binding.indicator.isIndeterminate = true
            refreshData()
        }
    }

    private fun refreshData() {
        viewModel.page = 1
        viewModel.isEnd = false
        viewModel.isRefreshing = true
        viewModel.isLoadMore = false
        viewModel.isNew = true
        viewModel.getSearch()
    }

    private fun initView() {
        val space = resources.getDimensionPixelSize(R.dimen.normal_space)

        mAdapter = AppAdapter(requireContext(), viewModel.searchList)
        mAdapter.setIOnLikeReplyListener(this)
        mAdapter.setOnImageItemClickListener(this)
        mAdapter.setOnPostFollowListener(this)
        mLayoutManager = LinearLayoutManager(activity)
        binding.recyclerView.apply {
            adapter = mAdapter
            layoutManager = mLayoutManager
            itemAnimator = null
            if (itemDecorationCount == 0)
                addItemDecoration(LinearItemDecoration(space))
        }
    }

    override fun onPostLike(type: String?, isLike: Boolean, id: String, position: Int?) {
        viewModel.likeFeedId = id
        viewModel.likePosition = position!!
        if (isLike) {
            viewModel.isPostUnLikeFeed = true
            viewModel.postUnLikeFeed()
        } else {
            viewModel.isPostLikeFeed = true
            viewModel.postLikeFeed()
        }
    }

    override fun onClick(
        nineGridView: NineGridImageView,
        imageView: ImageView,
        urlList: List<String>,
        position: Int
    ) {
        ImageUtil.startBigImgView(
            nineGridView,
            imageView,
            urlList,
            position
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSearch(type: String, value: String) {
        when (type) {
            "sort" -> viewModel.sort = value
            "feedType" -> viewModel.feedType = value
        }
        viewModel.searchList.clear()
        mAdapter.notifyDataSetChanged()
        binding.indicator.visibility = View.VISIBLE
        binding.indicator.isIndeterminate = true
        refreshData()
    }

    override fun onPostFollow(isFollow: Boolean, uid: String, position: Int) {
        viewModel.uid = uid
        viewModel.position = position
        if (isFollow) {
            viewModel.followType = true
            viewModel.postFollowUnFollow = true
            viewModel.url = "/v6/user/unfollow"
            viewModel.postFollowUnFollow()
        } else {
            viewModel.followType = false
            viewModel.postFollowUnFollow = true
            viewModel.url = "/v6/user/follow"
            viewModel.postFollowUnFollow()
        }
    }

}