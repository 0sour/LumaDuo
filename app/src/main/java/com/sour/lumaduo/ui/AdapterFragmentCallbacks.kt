package com.sour.lumaduo.ui

import android.view.View

interface AdapterFragmentCallbacks {
    fun getAdapterFragmentLayoutId(): Int
    // Generalized to accept any View (RecyclerView or AbsListView). Implementations
    // should check the runtime type if necessary.
    fun receiveAbsListView(gridView: View?)
}
