package org.matrix.vector.daemon.ipc

import android.os.Build
import android.os.IBinder
import android.os.IServiceCallback
import android.os.Parcel
import android.os.ServiceManager
import android.util.Log
import org.lsposed.lspd.service.ILSPApplicationService
import org.lsposed.lspd.service.ILSPSystemServerService
import org.matrix.vector.daemon.*
import org.matrix.vector.daemon.system.getSystemServiceManager

private const val TAG = "VectorSystemServer"

/**
 * Holds the Android R+ IServiceCallback implementation behind a separate class boundary.
 *
 * ART resolves types referenced by a method while verifying that method. Keeping
 * IServiceCallback.Stub directly inside SystemServerService.registerProxyService therefore makes
 * pre-R releases try to resolve a class they do not have before the SDK gate can run. This holder
 * is only initialized on R and newer; daemon/proguard-rules.pro keeps R8 from merging it back into
 * SystemServerService.
 */
private object ServiceRegistrationWatcher {

  fun watch(serviceName: String) {
    val callback =
        object : IServiceCallback.Stub() {
          override fun onRegistration(name: String, binder: IBinder?) {
            if (name == serviceName && binder != null) {
              SystemServerService.adoptOriginService(name, binder)
            }
          }

          override fun asBinder(): IBinder = this
        }
    getSystemServiceManager().registerForNotifications(serviceName, callback)
  }
}

object SystemServerService : ILSPSystemServerService.Stub(), IBinder.DeathRecipient {

  private var proxyServiceName: String? = null
  private var originService: IBinder? = null
  private var callbackRegistered = false

  var systemServerRequested = false

  @Synchronized
  fun registerProxyService(serviceName: String) {
    // Register as the service name early to setup an IPC for `system_server`.
    Log.d(TAG, "Registering bridge service for `system_server` with name `$serviceName`.")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && serviceName == "serial" &&
        !callbackRegistered) {
      runCatching { ServiceRegistrationWatcher.watch(serviceName) }
          .onSuccess { callbackRegistered = true }
          .onFailure { Log.e(TAG, "Failed to register IServiceCallback", it) }
    }
    runCatching {
          ServiceManager.addService(serviceName, this)
          proxyServiceName = serviceName
        }
        .onFailure { Log.e(TAG, "Failed to register proxy service `$serviceName`", it) }
  }

  internal fun adoptOriginService(name: String, binder: IBinder) {
    if (binder === this) return
    Log.d(TAG, "Intercepted system service registration with name `$name`")
    originService = binder
    runCatching { binder.linkToDeath(this, 0) }
  }

  @Synchronized
  fun prepareForSystemServerRestart(
      serviceName: String? = proxyServiceName,
      ownerInstanceId: String? = null,
      round: Long? = null,
  ) {
    val name = serviceName ?: return
    Log.i(
        TAG,
        "Preparing proxy service `$name` for system_server restart" +
            ownerInstanceId?.let { " owner=$it" }.orEmpty() +
            round?.let { " round=$it" }.orEmpty())
    binderDied()
    systemServerRequested = false
    registerProxyService(name)
  }

  override fun requestApplicationService(
      uid: Int,
      pid: Int,
      processName: String,
      heartBeat: IBinder?
  ): ILSPApplicationService? {
    if (uid != 1000 || heartBeat == null || processName != "system") return null
    systemServerRequested = true

    // Return the ApplicationService singleton if successfully registered
    return if (ApplicationService.registerHeartBeat(uid, pid, processName, heartBeat)) {
      ApplicationService
    } else null
  }

  override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
    when (code) {
      BRIDGE_TRANSACTION_CODE -> {
        val uid = data.readInt()
        val pid = data.readInt()
        val processName = data.readString() ?: ""
        val heartBeat = data.readStrongBinder()

        val service = requestApplicationService(uid, pid, processName, heartBeat)
        if (service != null) {
          reply?.writeNoException()
          reply?.writeStrongBinder(service.asBinder())
          return true
        }
        return false
      }
      DEX_TRANSACTION_CODE,
      OBFUSCATION_MAP_TRANSACTION_CODE -> {
        return ApplicationService.onTransact(code, data, reply, flags)
      }
      else -> {
        originService?.let {
          // Keep bridge transactions handled locally; only proxy non-Vector calls.
          Log.d(TAG, "Forwarding request to real `$proxyServiceName` service.")
          return it.transact(code, data, reply, flags)
        }
        return super.onTransact(code, data, reply, flags)
      }
    }
  }

  override fun binderDied() {
    originService?.unlinkToDeath(this, 0)
    originService = null
  }
}
