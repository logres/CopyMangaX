package com.crow.module_discover.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crow.base.current_project.getComicCardHeight
import com.crow.base.current_project.getComicCardWidth
import com.crow.base.databinding.BasePagingFooterRetryBinding
import com.crow.base.tools.extensions.clickGap
import com.crow.base.tools.extensions.logMsg

class DiscoverLoadStateAdapter(private val retry: () -> Unit) : LoadStateAdapter<DiscoverLoadStateAdapter.LoadStateViewHolder>() {


    inner class LoadStateViewHolder(val rvBinding: BasePagingFooterRetryBinding) : RecyclerView.ViewHolder(rvBinding.root) {
        fun bind(loadState: LoadState) {
            rvBinding.baseLoadingLottie.isVisible = loadState is LoadState.Loading
            rvBinding.baseLoadingRetry.isVisible = loadState is LoadState.Error
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): LoadStateViewHolder {
        return LoadStateViewHolder(BasePagingFooterRetryBinding.inflate(LayoutInflater.from(parent.context), parent, false)).also {  vh ->
            vh.rvBinding.root.layoutParams.apply {
                height = getComicCardHeight() / 2
            }
            vh.rvBinding.baseLoadingRetry.clickGap { _, _ -> retry() }
        }
    }

    override fun onBindViewHolder(holder: LoadStateViewHolder, loadState: LoadState, ) = holder.bind(loadState)
}