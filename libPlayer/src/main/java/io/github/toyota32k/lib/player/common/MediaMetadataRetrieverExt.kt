package io.github.toyota32k.lib.player.common

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import java.util.*

/**
 * ビットマップのサイズを変更する
 */
private fun fitBitmapScale(src: Bitmap, width:Int, height:Int) : Bitmap {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        return src
    }
    if(src.width==width && src.height==height) {
        return src
    }
    val bmp = Bitmap.createScaledBitmap(src, width, height, true)
    src.recycle()
    return bmp
}

fun MediaMetadataRetriever.getLong(key:Int, def:Long = 0) :Long {
    val s = extractMetadata(key)
    if(null!=s) {
        return s.toLong()
    }
    return def
}

fun MediaMetadataRetriever.getDate(key:Int) : Date? {
    val s = extractMetadata(key)
    return if(null!=s) {
        parseIso8601DateString(s)
    } else {
        null
    }
}

/**
 * 指定オフセット位置のフレーム画像を取得
 * API-27以降なら、サムネイルサイズにリサイズした画像が取得され、API-26以下なら、生サイズのやつが取得される。
 *
 * @param tm        オフセット位置
 * @param option    OPTION_CLOSEST（WinのNearestFrame）/ OPTION_CLOSEST_SYNC (WinのNearestKeyFrame)
 */
fun MediaMetadataRetriever.getBitmapAt(tm:Long, option:Int, width:Int, height:Int) : Bitmap? {
    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(tm*1000, option, width, height)
    } else {
        getFrameAtTime(tm*1000, option)
    }
}

/**
 * 指定オフセット位置のフレーム画像を取得
 */
fun MediaMetadataRetriever.getBitmapAt(tm:Long, exactFrame:Boolean, width:Int, height:Int) : Bitmap? {
    // フレームリスト作成時（mThumbnailCount>1)は、高速化のため、OPTION_CLOSEST_SYNC（キーフレームを取得）、
    // １枚だけ画像生成する場合は、OPTION_CLOSEST （正確なやつを取得）を指定する。
    val option = if(!exactFrame) MediaMetadataRetriever.OPTION_CLOSEST_SYNC else MediaMetadataRetriever.OPTION_CLOSEST
    var bmp = getBitmapAt(tm, option, width, height)
    // OPTION_CLOSESTで、tm==durationを渡した場合ｍ、getBitmapAt()が失敗することがある（ていうか、Chromebookの場合しか再現していないけど）。
    // この場合は、optionにOPTION_CLOSEST_SYNCで、再試行する。（これで、Chromebookもうまくいった）
    if(bmp==null && option == MediaMetadataRetriever.OPTION_CLOSEST) {
        bmp = getBitmapAt(tm, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
    }
    // 再試行してもだめならやはりエラー
    if(bmp==null) {
        return null
    }
    // 必要に応じてリサイズ
    return fitBitmapScale(bmp, width, height)
}

fun MediaMetadataRetriever.getBitmapAt(tm:Long, exactFrame:Boolean) : Bitmap? {
    // フレームリスト作成時（mThumbnailCount>1)は、高速化のため、OPTION_CLOSEST_SYNC（キーフレームを取得）、
    // １枚だけ画像生成する場合は、OPTION_CLOSEST （正確なやつを取得）を指定する。
    val option = if(!exactFrame) MediaMetadataRetriever.OPTION_CLOSEST_SYNC else MediaMetadataRetriever.OPTION_CLOSEST
    var bmp = getFrameAtTime(tm*1000, option)
    // OPTION_CLOSESTで、tm==durationを渡した場合ｍ、getBitmapAt()が失敗することがある（ていうか、Chromebookの場合しか再現していないけど）。
    // この場合は、optionにOPTION_CLOSEST_SYNCで、再試行する。（これで、Chromebookもうまくいった）
    if(bmp==null && option == MediaMetadataRetriever.OPTION_CLOSEST) {
        bmp = getFrameAtTime(tm*1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }
    // 再試行してもだめならやはりエラー
    return bmp
}
