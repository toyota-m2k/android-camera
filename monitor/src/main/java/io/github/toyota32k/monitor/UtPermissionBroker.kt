package io.github.toyota32k.monitor

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.dialog.broker.UtActivityBroker

class UtPermissionBroker(val activity:FragmentActivity) : UtActivityBroker<String, Boolean>() {
    val context get() = activity.applicationContext
    init { register(activity) }
    companion object {
        fun isPermitted(context: Context, permission: String):Boolean {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    fun isPermitted(permission: String):Boolean {
        return isPermitted(context, permission)
    }
    override val contract: ActivityResultContract<String, Boolean>
        get() = ActivityResultContracts.RequestPermission()

    suspend fun requestPermission(permission:String):Boolean {
        if(isPermitted(permission)) {
            return true // すでに許可されている
        }
        return invoke(permission)
    }
}

class UtMultiPermissionsBroker(val activity: FragmentActivity) : UtActivityBroker<Array<String>, Map<String,Boolean>>() {
    val context get() = activity.applicationContext
    init { register(activity) }

    override val contract: ActivityResultContract<Array<String>, Map<String,Boolean>>
        get() = ActivityResultContracts.RequestMultiplePermissions()

    inner class Request {
        val list = mutableListOf<String>()
        var requiredFlags = mutableMapOf<String,Boolean>()
        fun add(permission:String, required:Boolean=true):Request {
            if(!UtPermissionBroker.isPermitted(context,permission)) {
                list.add(permission)
                requiredFlags[permission] = required
            }
            return this
        }
        fun addIf(condition:Boolean, permission:String, required: Boolean=true):Request {
            if(condition) {
                add(permission, required)
            }
            return this
        }
        fun add(permissionFn:()->String?, required: Boolean=true):Request {
            val permission = permissionFn() ?: return this
            add(permission, required)
            return this
        }
        suspend fun execute() : Boolean {
            if(list.isEmpty()) {
                return true
            }
            val result = invoke(list.toTypedArray())
            if(result.containsValue(false)) {
                for(key in result.keys) {
                    if(result[key]!=true && requiredFlags[key]!=false) {
                        // required = true で、resultが true でないものが含まれていたら失敗
                        return false
                    }
                }
            }
            return true
        }
    }
}