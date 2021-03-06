/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.RecyclerView
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.tunjid.androidx.recyclerview.ListManager
import com.tunjid.androidx.recyclerview.ListManagerBuilder
import com.tunjid.androidx.recyclerview.ListPlaceholder
import com.tunjid.androidx.recyclerview.SwipeDragOptions
import com.tunjid.androidx.recyclerview.adapterOf
import com.tunjid.androidx.view.util.inflate
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.rcswitchcontrol.protocols.models.Device
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.rcswitchcontrol.zigbee.models.ZigBeeDevice
import com.rcswitchcontrol.zigbee.models.createGroupSequence
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.dialogfragments.GroupDeviceDialogFragment
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment
import com.tunjid.rcswitchcontrol.dialogfragments.throttleColorChanges
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService
import com.tunjid.rcswitchcontrol.utils.DeletionHandler
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewholders.DeviceAdapterListener
import com.tunjid.rcswitchcontrol.viewholders.DeviceViewHolder
import com.tunjid.rcswitchcontrol.viewholders.RfDeviceViewHolder
import com.tunjid.rcswitchcontrol.viewholders.ZigBeeDeviceViewHolder
import com.tunjid.rcswitchcontrol.viewholders.withPaddedAdapter
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel.State

class DevicesFragment : BaseFragment(),
        DeviceAdapterListener,
        GroupDeviceDialogFragment.GroupNameListener,
        RenameSwitchDialogFragment.SwitchNameListener {

    private var isDeleting: Boolean = false

    private val viewModel by activityViewModels<ControlViewModel>()

    private lateinit var listManager: ListManager<RecyclerView.ViewHolder, ListPlaceholder<*>>

    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backPressedCallback = activity?.onBackPressedDispatcher?.addCallback(this) {
            isEnabled = viewModel.withSelectedDevices(Set<Device>::isEmpty)

            if (!isEnabled) activity?.onBackPressed()
            else {
                viewModel.clearSelections()
                refresh()
                refreshUi()
            }
        }

        viewModel.listen(State.Devices::class.java).observe(this, this::onPayloadReceived)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val spanCount = SpanCountCalculator.spanCount
        val root = inflater.inflate(R.layout.fragment_list, container, false)
        listManager = ListManagerBuilder<RecyclerView.ViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .withGridLayoutManager(spanCount)
                .withPaddedAdapter(adapterOf(
                        itemsSource = viewModel::devices,
                        viewHolderCreator = this::createViewHolder,
                        viewTypeFunction = this::getDeviceViewType,
                        viewHolderBinder = { holder, device, _ -> bindDevice(holder, device) },
                        itemIdFunction = { it.hashCode().toLong() }
                ), spanCount)
                .withSwipeDragOptions(SwipeDragOptions(
                        swipeConsumer = { viewHolder: RecyclerView.ViewHolder, _ -> onDelete(viewHolder) },
                        movementFlagFunction = this::swipeDirection,
                        itemViewSwipeSupplier = { true }
                ))
                .withInconsistencyHandler(this::onInconsistentList)
                .build()

        return root
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        backPressedCallback?.isEnabled = true
    }

    override fun onStop() {
        super.onStop()
        backPressedCallback?.isEnabled = false
    }

    private fun refreshUi() {
        updateUi(
                altToolBarMenu = R.menu.menu_alt_devices,
                altToolbarTitle = getString(R.string.devices_selected, viewModel.numSelections()),
                altToolBarShows = viewModel.withSelectedDevices { it.isNotEmpty() }
        )
    }

    override fun onDestroyView() {
        listManager.clear()
        super.onDestroyView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_rename_device)?.isVisible = viewModel.withSelectedDevices { it.size == 1 && it.first() is RfSwitch }
        menu.findItem(R.id.menu_create_group)?.isVisible = viewModel.withSelectedDevices { it.find { device -> device is RfSwitch } == null }

        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_rename_device -> RenameSwitchDialogFragment.newInstance(
                viewModel.withSelectedDevices { it.first() } as RfSwitch
        ).show(childFragmentManager, item.itemId.toString()).let { true }
        R.id.menu_create_group -> GroupDeviceDialogFragment.newInstance.show(childFragmentManager, item.itemId.toString()).let { true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun isSelected(device: Device): Boolean = viewModel.withSelectedDevices { it.contains(device) }

    override fun onClicked(device: Device) {
        if (viewModel.withSelectedDevices { it.isNotEmpty() }) longClickDevice(device)
    }

    override fun onLongClicked(device: Device): Boolean = viewModel.select(device).apply { refreshUi() }

    override fun onSwitchToggled(device: Device, state: Boolean) = viewModel.dispatchPayload(device.key) {
        when (device) {
            is RfSwitch -> {
                action = ClientBleService.ACTION_TRANSMITTER
                data = device.getEncodedTransmission(state)
            }
            is ZigBeeDevice -> {
                val zigBeeCommandArgs = device.toggleCommand(state)
                action = zigBeeCommandArgs.command
                data = zigBeeCommandArgs.serialize()
            }
        }
    }

    override fun rediscover(device: ZigBeeDevice) = device.rediscoverCommand().let { args ->
        viewModel.dispatchPayload(device.key) {
            action = args.command
            data = args.serialize()
        }
    }

    override fun color(device: ZigBeeDevice) = ColorPickerDialogBuilder
            .with(context)
            .setTitle(R.string.color_picker_choose)
            .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
            .showLightnessSlider(true)
            .showAlphaSlider(false)
            .density(12)
            .throttleColorChanges {
                device.colorCommand(it).let { args ->
                    viewModel.dispatchPayload(device.key) {
                        action = args.command
                        data = args.serialize()
                    }
                }
            }
            .build()
            .show()

    override fun level(device: ZigBeeDevice, level: Float) = device.levelCommand(level).let { args ->
        viewModel.dispatchPayload(device.key) {
            action = args.command
            data = args.serialize()
        }
    }

    override fun onGroupNamed(groupName: CharSequence) = viewModel.run {
        withSelectedDevices { devices ->
            devices.filterIsInstance(ZigBeeDevice::class.java)
                    .createGroupSequence(groupName.toString())
                    .forEach {
                        dispatchPayload(it.key) {
                            action = it.command
                            data = it.serialize()
                        }
                    }
        }
        clearSelections()
        refreshUi()
        refresh()
    }

    override fun onSwitchRenamed(rfSwitch: RfSwitch) {
        listManager.notifyItemChanged(viewModel.devices.indexOf(rfSwitch))
        viewModel.dispatchPayload(rfSwitch.key) {
            action = getString(R.string.blercprotocol_rename_command)
            data = rfSwitch.serialize()
        }
    }

    private fun refresh() = listManager.notifyDataSetChanged()

    private fun onPayloadReceived(state: State.Devices) = listManager.onDiff(state.result)

    private fun getDeviceViewType(device: Device) = when (device) {
        is RfSwitch -> RF_DEVICE
        is ZigBeeDevice -> ZIG_BEE_DEVICE
        else -> Int.MAX_VALUE
    }

    private fun createViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        RF_DEVICE -> RfDeviceViewHolder(parent.inflate(R.layout.viewholder_remote_switch), this)
        ZIG_BEE_DEVICE -> ZigBeeDeviceViewHolder(parent.inflate(R.layout.viewholder_zigbee_device), this)
        else -> object : RecyclerView.ViewHolder(parent.inflate(R.layout.viewholder_padding)) {}
    }

    private fun bindDevice(holder: RecyclerView.ViewHolder, device: Device) = when {
        holder is RfDeviceViewHolder && device is RfSwitch -> holder.bind(device)
        holder is ZigBeeDeviceViewHolder && device is ZigBeeDevice -> holder.bind(device)
        else -> Unit
    }

    private fun swipeDirection(holder: RecyclerView.ViewHolder): Int =
            if (isDeleting || holder is ZigBeeDeviceViewHolder) 0
            else makeMovementFlags(0, ItemTouchHelper.LEFT)

    private fun longClickDevice(device: Device) {
        val holder = listManager.findViewHolderForItemId(device.hashCode().toLong()) as? DeviceViewHolder<*, *>
                ?: return

        holder.performLongClick()
    }

    private fun onDelete(viewHolder: RecyclerView.ViewHolder) {
        if (isDeleting) return
        isDeleting = true

        if (view == null) return

        val position = viewHolder.adapterPosition

        val devices = viewModel.devices
        val deletionHandler = DeletionHandler<Device>(position) { self ->
            if (self.hasItems() && self.peek() is RfSwitch) self.pop().also { device ->
                viewModel.dispatchPayload(device.key) {
                    action = getString(R.string.blercprotocol_delete_command)
                    data = device.serialize()
                }
            }
            isDeleting = false
        }

        deletionHandler.push(devices[position])
        devices.removeAt(position)
        listManager.notifyItemRemoved(position)

        navigator.transientBarDriver.showSnackBar { snackBar ->
            snackBar.setText(R.string.deleted_switch)
                    .addCallback(deletionHandler)
                    .setAction(R.string.undo) {
                        if (deletionHandler.hasItems()) {
                            val deletedAt = deletionHandler.deletedPosition
                            devices.add(deletedAt, deletionHandler.pop())
                            listManager.notifyItemInserted(deletedAt)
                        }
                        isDeleting = false
                    }
        }
    }

    companion object {

        private const val RF_DEVICE = 1
        private const val ZIG_BEE_DEVICE = 2

        fun newInstance(): DevicesFragment {
            val fragment = DevicesFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}
