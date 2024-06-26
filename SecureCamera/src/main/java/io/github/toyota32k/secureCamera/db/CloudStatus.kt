package io.github.toyota32k.secureCamera.db

import io.github.toyota32k.secureCamera.settings.Settings

enum class CloudStatus(val v:Int) {
    Local(0),       // ファイルはローカルにのみ存在
    Uploaded(1),    // アップロード済（ファイルはローカルとサーバーの両方に存在）
    Cloud(2)        // ファイルはサーバーにのみ存在
    ;
    val isFileInLocal:Boolean get() = this!=Cloud
    val isFileInCloud:Boolean get() = this==Cloud || this == Uploaded
    val loadFromCloud:Boolean get() =
        (Settings.PlayListSetting.cloudTestMode && isFileInCloud) ||    // クラウドにありさえすれば
        (!Settings.PlayListSetting.cloudTestMode && this==Cloud)        // クラウドにしかなければ

    companion object {
        fun valueOf(v:Int):CloudStatus {
            return CloudStatus.values().find { it.v == v } ?: Local
        }
    }
}