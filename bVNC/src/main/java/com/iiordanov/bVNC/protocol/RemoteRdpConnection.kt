package com.iiordanov.bVNC.protocol

import android.app.Activity
import android.content.Context
import android.util.Log
import com.iiordanov.bVNC.App
import com.iiordanov.bVNC.COLORMODEL
import com.iiordanov.bVNC.RemoteCanvasActivity
import com.iiordanov.bVNC.Utils
import com.iiordanov.bVNC.input.RemoteRdpKeyboard
import com.iiordanov.bVNC.input.RemoteRdpPointer
import com.undatech.opaque.Connection
import com.undatech.opaque.RdpCommunicator
import com.undatech.opaque.RdpMonitorConnection
import com.undatech.opaque.RdpMonitorLayout
import com.undatech.opaque.Viewable
import com.undatech.remoteClientUi.R

class RemoteRdpConnection(
    context: Context,
    connection: Connection?,
    canvas: Viewable,
    hideKeyboardAndExtraKeys: Runnable,
) : RemoteConnection(context, connection, canvas, hideKeyboardAndExtraKeys) {
    private val tag: String = "RemoteRdpConnection"
    private var rdpComm: RdpCommunicator? = null
    private val activity = context as? Activity
    private val monitorCount = activity?.intent?.getIntExtra(RemoteCanvasActivity.EXTRA_RDP_MONITOR_COUNT, 1) ?: 1
    private val monitorIndex = activity?.intent?.getIntExtra(RemoteCanvasActivity.EXTRA_RDP_MONITOR_INDEX, 0) ?: 0
    private val monitorGroupId = activity?.intent?.getStringExtra(RemoteCanvasActivity.EXTRA_RDP_MONITOR_GROUP)
    private var startsSharedSession = true

    /**
     * Initializes an RDP connection.
     */
    private fun initializeRdpConnection() {
        Log.i(tag, "initializeRdpConnection: Initializing RDP connection.")
        rdpComm = if (monitorCount > 1 && monitorGroupId != null) {
            RdpCommunicator.acquireMultiMonitorSession(
                monitorGroupId, monitorIndex, monitorCount, connection, context, handler, canvas,
                connection.connectionConfigFile, connection.userName, connection.rdpDomain,
                connection.password, App.debugLog, isRemoteToLocalClipboardIntegrationEnabled
            )
        } else {
            RdpCommunicator(
                connection, context, handler, canvas,
                connection.connectionConfigFile, connection.userName, connection.rdpDomain, connection.password,
                App.debugLog, isRemoteToLocalClipboardIntegrationEnabled
            )
        }
        startsSharedSession = monitorCount == 1 || rdpComm!!.beginConnection()
        rfbConn = if (monitorCount > 1) RdpMonitorConnection(rdpComm, monitorIndex, canvas) else rdpComm
        pointer = RemoteRdpPointer(rfbConn, context, this, canvas, handler, !connection.useDpadAsArrows, App.debugLog)
        keyboard = RemoteRdpKeyboard(
            rdpComm, canvas, this, handler, App.debugLog,
            connection.preferSendingUnicode
        )
    }

    /**
     * Starts an RDP connection using the FreeRDP library.
     */
    @Throws(Exception::class)
    private fun startRdpConnection() {
        Log.i(tag, "startRdpConnection: Starting RDP connection.")

        // Get the address and port (based on whether an SSH tunnel is being established or not).
        val address = address
        val gatewayAddress = getGatewayAddress()
        val rdpPort = getRemoteProtocolPort(connection.port)
        val gatewayPort = getGatewayPort(connection.rdpGatewayPort)
        canvas.waitUntilInflated()
        val remoteWidth = minOf(canvas.getRemoteWidth(canvas.width, canvas.height), RdpMonitorLayout.maxMonitorWidth(monitorCount))
        val remoteHeight = minOf(canvas.getRemoteHeight(canvas.width, canvas.height), RdpMonitorLayout.MAX_DESKTOP_DIMENSION)
        rdpComm!!.setConnectionParameters(
            address, rdpPort,
            connection.rdpGatewayEnabled, gatewayAddress, gatewayPort,
            connection.rdpGatewayUsername, connection.rdpGatewayDomain, connection.rdpGatewayPassword,
            connection.nickname, remoteWidth,
            remoteHeight, connection.desktopBackground, connection.fontSmoothing,
            connection.desktopComposition, connection.windowContents,
            connection.menuAnimation, connection.visualStyles,
            connection.redirectSdCard, connection.consoleMode,
            connection.remoteSoundType, connection.enableRecording,
            connection.remoteFx, connection.enableGfx, connection.enableGfxH264,
            connection.rdpColor, connection.desktopScalePercentage, App.debugLog
        )
        rdpComm!!.connect()
    }

    override fun initializeConnection() {
        super.initializeConnection()
        try {
            initializeRdpConnection()
            if (startsSharedSession) initializeClipboardMonitor()
        } catch (e: Throwable) {
            handleUncaughtException(e, R.string.error_rdp_unable_to_connect)
        }
        connectionThread = object : Thread() {
            override fun run() {
                try {
                    if (startsSharedSession) startRdpConnection()
                } catch (e: Throwable) {
                    handleUncaughtException(e, R.string.error_rdp_unable_to_connect)
                }
            }
        }
        connectionThread.start()
    }

    /**
     * If necessary, initializes an SSH tunnel and returns local forwarded port, or
     * if SSH tunneling is not needed, returns the given port.
     */
    @Throws(java.lang.Exception::class)
    fun getRemoteProtocolPort(port: Int): Int {
        val result =
            if (sshTunneled && !connection.rdpGatewayEnabled) {
                constructSshConnectionIfNeeded()
                sshConnection.createLocalPortForward(port)
            } else {
                port
            }
        return result
    }


    /**
     * If necessary, initializes an SSH tunnel and returns local forwarded port, or
     * if SSH tunneling is not needed, returns the given port.
     */
    @Throws(java.lang.Exception::class)
    private fun getGatewayPort(port: Int): Int {
        var result = port
        if (sshTunneled) {
            constructSshConnectionIfNeeded()
            result = sshConnection.createLocalPortForward(port)
        }
        return result
    }

    /**
     * Returns localhost if using SSH tunnel (without gateway). If the address is prefixed with
     * {@code \\}, strips the prefix and resolves the name via NetBIOS. Otherwise returns the
     * address as-is.
     */
    override fun getAddress(): String? {
        val address = connection.address ?: return null
        val sshTunneledAndNoGatewayConfig = sshTunneled && !connection.rdpGatewayEnabled
        val netBiosResolutionForced = address.startsWith("\\\\")
        val result = when {
            sshTunneledAndNoGatewayConfig -> "127.0.0.1"
            netBiosResolutionForced -> address.substring(2).let { addressWithoutPrefix ->
                Utils.resolveNetbiosAddress(addressWithoutPrefix) ?: addressWithoutPrefix
            }
            else -> address
        }
        return result
    }
    
    /**
     * Returns localhost if using SSH tunnel or saved address.
     */
    private fun getGatewayAddress(): String? {
        return if (sshTunneled) {
            "127.0.0.1"
        } else {
            connection.rdpGatewayHostname
        }
    }

    override fun getSshTunnelTargetAddress(): String? {
        var address = connection.address
        if (connection.rdpGatewayEnabled) {
            address = connection.rdpGatewayHostname
        }
        return address
    }

    override fun isColorModel(cm: COLORMODEL) = false
    override fun setColorModel(cm: COLORMODEL?) {}

    override fun shouldSaveScreenshot() = monitorIndex == 0

    override fun disconnectSession() {
        if (monitorCount > 1) rdpComm?.disconnectAllMonitors() else super.disconnectSession()
    }

    override fun getMissingMonitorIndices(): List<Int> =
        if (monitorCount > 1) rdpComm?.missingMonitorIndices ?: emptyList() else emptyList()

    @Throws(java.lang.Exception::class)
    override fun correctAfterRotation() {
    }
}
